const crypto = require('crypto');
const wallet = require('./pointWalletService');
const play = require('./googlePlayPublisherClient');

function pointTransactionKey(purchaseToken) {
  return `PLAY_POINT:${crypto.createHash('sha256').update(purchaseToken).digest('hex')}`;
}

function lineItems(playPurchase) {
  const items = playPurchase?.productLineItem;
  return Array.isArray(items) ? items : items ? [items] : [];
}

function asPurchaseTime(playPurchase) {
  const raw = playPurchase?.purchaseCompletionTime || playPurchase?.purchaseTime;
  const parsed = raw ? new Date(raw) : null;
  return parsed && !Number.isNaN(parsed.getTime()) ? parsed : null;
}

function validatePlayPurchase(playPurchase, productId) {
  const state = playPurchase?.purchaseStateContext?.purchaseState;
  if (state === 'PENDING') return { error: 'PLAY_PURCHASE_PENDING' };
  if (state !== 'PURCHASED') return { error: 'INVALID_PLAY_PURCHASE' };

  const item = lineItems(playPurchase).find(line => line?.productId === productId);
  if (!item) return { error: 'PLAY_PRODUCT_MISMATCH' };

  const consumptionState = playPurchase?.consumptionState;
  if (consumptionState && consumptionState !== 'CONSUMPTION_STATE_YET_TO_BE_CONSUMED') {
    return { error: 'PLAY_PURCHASE_CONSUMED' };
  }
  return { item };
}

async function grant(userId, productId, purchaseToken, playPurchase) {
  return wallet.withWallet(userId, async (db, before) => {
    const [[existing]] = await db.query(
      `SELECT user_id, product_id, point_amount, consumed_at
       FROM google_play_point_purchases WHERE purchase_token = ? FOR UPDATE`,
      [purchaseToken]
    );
    if (existing) {
      if (Number(existing.user_id) !== userId) return { error: 'PLAY_TOKEN_ALREADY_USED' };
      return {
        pointBalance: before,
        pointAmount: Number(existing.point_amount),
        duplicate: true,
        needsConsumption: !existing.consumed_at,
      };
    }

    const [[product]] = await db.query(
      `SELECT point_amount FROM google_play_point_products
       WHERE product_id = ? AND is_active = 1 FOR UPDATE`,
      [productId]
    );
    if (!product) return { error: 'PLAY_PRODUCT_NOT_FOUND' };

    const pointAmount = Number(product.point_amount);
    await db.query(
      `INSERT INTO google_play_point_purchases
       (purchase_token, user_id, product_id, point_amount, order_id, purchased_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [
        purchaseToken,
        userId,
        productId,
        pointAmount,
        playPurchase.orderId || null,
        asPurchaseTime(playPurchase),
      ]
    );
    const pointBalance = await wallet.changeBalance(db, userId, before, {
      amount: pointAmount,
      type: 'PLAY_POINT_PURCHASE',
      reference: productId,
      key: pointTransactionKey(purchaseToken),
    });
    return { pointBalance, pointAmount, duplicate: false, needsConsumption: true };
  });
}

async function markConsumed(purchaseToken) {
  const { pool } = require('../db/db');
  await pool.query(
    'UPDATE google_play_point_purchases SET consumed_at = COALESCE(consumed_at, CURRENT_TIMESTAMP) WHERE purchase_token = ?',
    [purchaseToken]
  );
}

async function findExisting(purchaseToken) {
  const { pool } = require('../db/db');
  const [[purchase]] = await pool.query(
    `SELECT user_id, product_id, point_amount, consumed_at
     FROM google_play_point_purchases WHERE purchase_token = ?`,
    [purchaseToken]
  );
  return purchase || null;
}

async function redeem(userId, { productId, purchaseToken } = {}) {
  if (typeof productId !== 'string' || !/^[a-z0-9_.]{3,80}$/.test(productId)) {
    return { error: 'INVALID_PLAY_PURCHASE' };
  }
  if (
    typeof purchaseToken !== 'string' ||
    purchaseToken.length < 16 ||
    purchaseToken.length > 512 ||
    /\s/.test(purchaseToken)
  ) {
    return { error: 'INVALID_PLAY_PURCHASE' };
  }

  // 이미 검증·지급한 토큰은 Google이 소비한 뒤에도 같은 결과로 복구할 수 있어야 한다.
  const existing = await findExisting(purchaseToken);
  if (existing) {
    if (Number(existing.user_id) !== userId) return { error: 'PLAY_TOKEN_ALREADY_USED' };
    if (!existing.consumed_at) {
      try {
        await play.consumeProduct(existing.product_id, purchaseToken);
        await markConsumed(purchaseToken);
      } catch (error) {
        console.error('[googlePlay.consume.retry]', error.response?.status || error.code || error.message);
        return { error: 'PLAY_CONSUME_FAILED' };
      }
    }
    return {
      pointBalance: await wallet.getBalance(userId),
      pointAmount: Number(existing.point_amount),
      duplicate: true,
    };
  }

  let playPurchase;
  try {
    playPurchase = await play.getProductPurchase(purchaseToken);
  } catch (error) {
    if (error.response?.status === 404) return { error: 'INVALID_PLAY_PURCHASE' };
    throw error;
  }
  const valid = validatePlayPurchase(playPurchase, productId);
  if (valid.error) return valid;

  const result = await grant(userId, productId, purchaseToken, playPurchase);
  if (result.error) return result;
  if (result.needsConsumption) {
    try {
      await play.consumeProduct(productId, purchaseToken);
      await markConsumed(purchaseToken);
    } catch (error) {
      console.error('[googlePlay.consume]', error.response?.status || error.code || error.message);
      return { error: 'PLAY_CONSUME_FAILED' };
    }
  }
  return {
    pointBalance: result.pointBalance,
    pointAmount: result.pointAmount,
    duplicate: result.duplicate,
  };
}

module.exports = { redeem };
