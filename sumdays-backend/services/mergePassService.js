const { pool } = require('../db/db');
const wallet = require('./pointWalletService');
const marble = require('./mergeMarbleService');
const play = require('./googlePlayPublisherClient');

function validToken(token) {
  return typeof token === 'string' && token.length >= 16 && token.length <= 512 && !/\s/.test(token);
}

function subscriptionLine(subscription, productId) {
  return subscription?.lineItems?.find((line) => line?.productId === productId) || null;
}

function validateSubscription(subscription, productId) {
  if (subscription?.subscriptionState === 'SUBSCRIPTION_STATE_PENDING') {
    return { error: 'MERGE_PASS_PURCHASE_PENDING' };
  }
  if (subscription?.subscriptionState !== 'SUBSCRIPTION_STATE_ACTIVE') {
    return { error: 'INVALID_MERGE_PASS_PURCHASE' };
  }
  const line = subscriptionLine(subscription, productId);
  if (!line || !line.prepaidPlan || !line.expiryTime) {
    return { error: 'MERGE_PASS_PRODUCT_MISMATCH' };
  }
  const expiresAt = new Date(line.expiryTime);
  if (Number.isNaN(expiresAt.getTime()) || expiresAt <= new Date()) {
    return { error: 'INVALID_MERGE_PASS_PURCHASE' };
  }
  return { expiresAt: expiresAt.toISOString().slice(0, 19).replace('T', ' ') };
}

async function findPurchase(token) {
  const [[purchase]] = await pool.query(
    `SELECT user_id, product_id, expires_at, acknowledged_at
     FROM merge_pass_purchases WHERE purchase_token = ?`,
    [token]
  );
  return purchase || null;
}

async function acknowledge(productId, purchaseToken) {
  try {
    await play.acknowledgeSubscription(productId, purchaseToken);
    await pool.query(
      'UPDATE merge_pass_purchases SET acknowledged_at = COALESCE(acknowledged_at, CURRENT_TIMESTAMP) WHERE purchase_token = ?',
      [purchaseToken]
    );
  } catch (error) {
    console.error('[mergePass.acknowledge]', error.response?.status || error.code || error.message);
    const wrapped = new Error('Google Play acknowledgement failed');
    wrapped.code = 'MERGE_PASS_ACKNOWLEDGE_FAILED';
    throw wrapped;
  }
}

async function activate(userId, productId, purchaseToken, expiresAt) {
  return wallet.withWallet(userId, async (db) => {
    const [[product]] = await db.query(
      'SELECT product_id FROM merge_pass_products WHERE product_id = ? AND is_active = 1 FOR UPDATE',
      [productId]
    );
    if (!product) return { error: 'MERGE_PASS_PRODUCT_NOT_FOUND' };

    const [[existingToken]] = await db.query(
      `SELECT user_id, product_id, acknowledged_at FROM merge_pass_purchases
       WHERE purchase_token = ? FOR UPDATE`,
      [purchaseToken]
    );
    if (existingToken) {
      if (Number(existingToken.user_id) !== userId) return { error: 'MERGE_PASS_TOKEN_ALREADY_USED' };
      return {
        duplicate: true,
        needsAcknowledgement: !existingToken.acknowledged_at,
        productId: existingToken.product_id,
      };
    }

    await db.query(
      `UPDATE user_merge_passes SET status = 'EXPIRED'
       WHERE user_id = ? AND status = 'ACTIVE' AND expires_at <= UTC_TIMESTAMP()`,
      [userId]
    );
    const [[activePass]] = await db.query(
      `SELECT purchase_token FROM user_merge_passes
       WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > UTC_TIMESTAMP() FOR UPDATE`,
      [userId]
    );
    if (activePass) return { error: 'MERGE_PASS_ALREADY_ACTIVE' };

    await db.query(
      `INSERT INTO merge_pass_purchases (purchase_token, user_id, product_id, expires_at)
       VALUES (?, ?, ?, ?)`,
      [purchaseToken, userId, productId, expiresAt]
    );
    await db.query(
      `INSERT INTO user_merge_passes (user_id, product_id, purchase_token, expires_at, status)
       VALUES (?, ?, ?, ?, 'ACTIVE')
       ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), purchase_token = VALUES(purchase_token),
         expires_at = VALUES(expires_at), status = 'ACTIVE'`,
      [userId, productId, purchaseToken, expiresAt]
    );
    return { duplicate: false, needsAcknowledgement: true, productId };
  });
}

async function redeem(userId, { productId, purchaseToken } = {}) {
  if (typeof productId !== 'string' || !/^[a-z0-9_.]{3,80}$/.test(productId) || !validToken(purchaseToken)) {
    return { error: 'INVALID_MERGE_PASS_PURCHASE' };
  }

  const existing = await findPurchase(purchaseToken);
  if (existing) {
    if (Number(existing.user_id) !== userId) return { error: 'MERGE_PASS_TOKEN_ALREADY_USED' };
    if (!existing.acknowledged_at) await acknowledge(existing.product_id, purchaseToken);
    return { ...await marble.getState(userId), duplicate: true };
  }

  let subscription;
  try {
    subscription = await play.getSubscriptionPurchase(purchaseToken);
  } catch (error) {
    if (error.response?.status === 404) return { error: 'INVALID_MERGE_PASS_PURCHASE' };
    throw error;
  }
  const verified = validateSubscription(subscription, productId);
  if (verified.error) return verified;

  const result = await activate(userId, productId, purchaseToken, verified.expiresAt);
  if (result.error) return result;
  if (result.needsAcknowledgement) await acknowledge(result.productId, purchaseToken);
  return { ...await marble.getState(userId), duplicate: result.duplicate };
}

async function syncRtdn(notification) {
  const purchaseToken = notification?.purchaseToken;
  if (!validToken(purchaseToken)) return { ignored: true };
  const [[knownPurchase]] = await pool.query(
    'SELECT user_id, product_id FROM merge_pass_purchases WHERE purchase_token = ?',
    [purchaseToken]
  );
  if (!knownPurchase) return { ignored: true };

  let subscription;
  try {
    subscription = await play.getSubscriptionPurchase(purchaseToken);
  } catch (error) {
    // 이미 만료된 토큰은 API 보존 기간 이후 조회할 수 없으므로, RTDN의 종료 신호만 반영한다.
    if (Number(notification.notificationType) === 12 || Number(notification.notificationType) === 13) {
      await pool.query(
        `UPDATE user_merge_passes SET status = ? WHERE user_id = ? AND purchase_token = ?`,
        [Number(notification.notificationType) === 12 ? 'REVOKED' : 'EXPIRED', knownPurchase.user_id, purchaseToken]
      );
      return { synchronized: true };
    }
    throw error;
  }

  const line = subscriptionLine(subscription, knownPurchase.product_id);
  const expiresAt = line?.expiryTime ? new Date(line.expiryTime) : null;
  const active = subscription.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE' &&
    line?.prepaidPlan && expiresAt && expiresAt > new Date();
  if (active) {
    const utcExpiry = expiresAt.toISOString().slice(0, 19).replace('T', ' ');
    await pool.query(
      `UPDATE merge_pass_purchases SET expires_at = ? WHERE purchase_token = ?`,
      [utcExpiry, purchaseToken]
    );
    await pool.query(
      `UPDATE user_merge_passes SET expires_at = ?, status = 'ACTIVE'
       WHERE user_id = ? AND purchase_token = ?`,
      [utcExpiry, knownPurchase.user_id, purchaseToken]
    );
    return { synchronized: true };
  }

  const status = Number(notification.notificationType) === 12 ? 'REVOKED' : 'EXPIRED';
  await pool.query(
    `UPDATE user_merge_passes SET status = ? WHERE user_id = ? AND purchase_token = ?`,
    [status, knownPurchase.user_id, purchaseToken]
  );
  return { synchronized: true };
}

async function revokeVoidedPurchase(notification) {
  const purchaseToken = notification?.purchaseToken;
  if (!validToken(purchaseToken)) return { ignored: true };
  const [result] = await pool.query(
    `UPDATE user_merge_passes pass
     INNER JOIN merge_pass_purchases purchase ON purchase.purchase_token = pass.purchase_token
     SET pass.status = 'REVOKED'
     WHERE purchase.purchase_token = ?`,
    [purchaseToken]
  );
  return { synchronized: result.affectedRows > 0 };
}

module.exports = { redeem, syncRtdn, revokeVoidedPurchase };
