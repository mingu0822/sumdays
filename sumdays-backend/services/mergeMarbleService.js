const crypto = require('node:crypto');
const moment = require('moment-timezone');
const wallet = require('./pointWalletService');

const today = () => moment().tz('Asia/Seoul').format('YYYY-MM-DD');
const validKey = (key) => typeof key === 'string' && /^[A-Za-z0-9-]{16,80}$/.test(key);
const fingerprint = (value) => crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');

async function settings(connection) {
  const values = await wallet.readSettings(connection, { DAILY_MERGE_FREE_LIMIT: 5 });
  const freeLimit = values.DAILY_MERGE_FREE_LIMIT ?? 5;
  if (!Number.isSafeInteger(freeLimit) || freeLimit < 0) throw new Error('Invalid merge economy settings');
  return { freeLimit };
}

async function ensureTicketBalance(db, userId) {
  await db.query(
    'INSERT IGNORE INTO user_merge_ticket_balances (user_id, balance) VALUES (?, 0)', [userId]
  );
  const [[row]] = await db.query(
    'SELECT balance FROM user_merge_ticket_balances WHERE user_id = ? FOR UPDATE', [userId]
  );
  return Number(row.balance);
}

async function changeTicketBalance(db, userId, before, { amount, type, reference, key }) {
  const after = before + amount;
  if (!Number.isSafeInteger(amount) || !Number.isSafeInteger(after) || after < 0) {
    throw new Error('Invalid merge ticket balance change');
  }
  await db.query('UPDATE user_merge_ticket_balances SET balance = ? WHERE user_id = ?', [after, userId]);
  await db.query(
    `INSERT INTO merge_ticket_transactions
     (user_id, change_amount, balance_after, transaction_type, reference_id, idempotency_key)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [userId, amount, after, type, reference, key]
  );
  return after;
}

async function ticketProducts(db) {
  const [rows] = await db.query(
    `SELECT pack_id, ticket_count, point_price
     FROM merge_ticket_products WHERE is_active = 1 ORDER BY ticket_count ASC`
  );
  return rows.map((row) => ({
    packId: row.pack_id,
    ticketCount: Number(row.ticket_count),
    pointPrice: Number(row.point_price),
  }));
}

async function activePass(db, userId) {
  await db.query(
    `UPDATE user_merge_passes SET status = 'EXPIRED'
     WHERE user_id = ? AND status = 'ACTIVE' AND expires_at <= UTC_TIMESTAMP()`,
    [userId]
  );
  const [[pass]] = await db.query(
    `SELECT product_id, DATE_FORMAT(expires_at, '%Y-%m-%dT%H:%i:%s.000Z') AS expires_at FROM user_merge_passes
     WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > UTC_TIMESTAMP() FOR UPDATE`,
    [userId]
  );
  return pass || null;
}

// 모든 경로에서 지갑 -> 횟수권 -> 사용량/요청 순서로 잠금한다.
// AI 호출 동안에는 DB 잠금을 유지하지 않는다.
async function withAccount(userId, action) {
  return wallet.withWallet(userId, async (db, pointBalance) => {
    const config = await settings(db);
    const ticketBalance = await ensureTicketBalance(db, userId);
    const pass = await activePass(db, userId);
    const [expired] = await db.query(
      `SELECT request_id, marble_source, DATE_FORMAT(usage_date, '%Y-%m-%d') AS usage_date
       FROM merge_operations WHERE user_id = ? AND status = 'PENDING'
       AND operation_type = 'MERGE' AND expires_at <= UTC_TIMESTAMP() FOR UPDATE`, [userId]
    );
    let restoredTicketBalance = ticketBalance;
    for (const operation of expired) {
      // 005 이전에 예약된 요청은 source가 없었고 모두 daily_merge_usage를 사용했다.
      if (operation.marble_source === 'FREE' || operation.marble_source === null) {
        await db.query(
          `UPDATE daily_merge_usage SET used_count = GREATEST(used_count - 1, 0)
           WHERE user_id = ? AND usage_date = ?`, [userId, operation.usage_date]
        );
      } else if (operation.marble_source === 'TICKET') {
        restoredTicketBalance = await changeTicketBalance(db, userId, restoredTicketBalance, {
          amount: 1,
          type: 'MERGE_TICKET_REFUND',
          reference: operation.request_id,
          key: `MERGE_TICKET_REFUND:${userId}:${operation.request_id}`,
        });
      }
      await db.query(
        `UPDATE merge_operations SET status = 'FAILED', error_code = 'MERGE_EXPIRED'
         WHERE user_id = ? AND request_id = ?`, [userId, operation.request_id]
      );
    }
    return action(db, config, pointBalance, restoredTicketBalance, pass);
  });
}

async function state(db, userId, config, pointBalance, ticketBalance, pass, date = today()) {
  const [rows] = await db.query(
    'SELECT used_count FROM daily_merge_usage WHERE user_id = ? AND usage_date = ? FOR UPDATE',
    [userId, date]
  );
  const usedCount = Number(rows[0]?.used_count ?? 0);
  const freeRemainingCount = Math.max(0, config.freeLimit - usedCount);
  return {
    date,
    freeLimit: config.freeLimit,
    usedCount,
    freeRemainingCount,
    ticketBalance,
    // Android DTO is Int; UI does not display this while passActive is true.
    remainingCount: pass ? 2147483647 : freeRemainingCount + ticketBalance,
    ticketProducts: await ticketProducts(db),
    passActive: Boolean(pass),
    passExpiresAt: pass?.expires_at || null,
    pointBalance,
  };
}

const getState = (userId) => withAccount(
  userId,
  (db, config, pointBalance, ticketBalance, pass) => state(db, userId, config, pointBalance, ticketBalance, pass)
);

async function existing(db, userId, key, type, hash) {
  const [rows] = await db.query(
    'SELECT * FROM merge_operations WHERE user_id = ? AND request_id = ? FOR UPDATE', [userId, key]
  );
  const previous = rows[0];
  if (!previous) return null;
  if (previous.operation_type !== type || previous.input_hash !== hash) return { error: 'IDEMPOTENCY_KEY_CONFLICT' };
  if (previous.status === 'FAILED') return { error: previous.error_code };
  if (previous.status === 'PENDING') return { error: 'MERGE_IN_PROGRESS' };
  return { duplicate: true, result: previous.result_json ? JSON.parse(previous.result_json) : null };
}

async function insertOperation(db, userId, key, type, date, hash, { source = null, error = null, result = null } = {}) {
  await db.query(
    `INSERT INTO merge_operations
     (user_id, request_id, operation_type, marble_source, usage_date, input_hash, status, error_code, result_json, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 5 MINUTE))`,
    [userId, key, type, source, date, hash, error ? 'FAILED' : result ? 'SUCCEEDED' : 'PENDING',
      error, result ? JSON.stringify(result) : null]
  );
}

async function purchaseTicket(userId, { requestId, packId, expectedPrice } = {}) {
  if (!validKey(requestId) || typeof packId !== 'string' || !Number.isSafeInteger(expectedPrice)) {
    return { error: 'INVALID_REQUEST' };
  }
  const hash = fingerprint({ packId, expectedPrice });
  return withAccount(userId, async (db, config, pointBalance, ticketBalance, pass) => {
    const previous = await existing(db, userId, requestId, 'PURCHASE', hash);
    if (previous) {
      if (previous.error) return previous;
      return { ...await state(db, userId, config, pointBalance, ticketBalance, pass), duplicate: true };
    }
    if (pass) return { error: 'MERGE_PASS_ACTIVE' };
    const [[product]] = await db.query(
      `SELECT ticket_count, point_price FROM merge_ticket_products
       WHERE pack_id = ? AND is_active = 1 FOR UPDATE`, [packId]
    );
    const error = !product ? 'MERGE_TICKET_NOT_FOUND'
      : expectedPrice !== Number(product.point_price) ? 'MERGE_TICKET_PRICE_CHANGED'
        : pointBalance < Number(product.point_price) ? 'INSUFFICIENT_POINTS' : null;
    if (error) {
      await insertOperation(db, userId, requestId, 'PURCHASE', today(), hash, { error });
      return { error };
    }
    const price = Number(product.point_price);
    const ticketCount = Number(product.ticket_count);
    const afterPoints = await wallet.changeBalance(db, userId, pointBalance, {
      amount: -price,
      type: 'MERGE_TICKET_PURCHASE',
      reference: packId,
      key: `MERGE_TICKET_PURCHASE:${userId}:${requestId}`,
    });
    const afterTickets = await changeTicketBalance(db, userId, ticketBalance, {
      amount: ticketCount,
      type: 'MERGE_TICKET_PURCHASE',
      reference: packId,
      key: `MERGE_TICKET_GRANT:${userId}:${requestId}`,
    });
    const result = await state(db, userId, config, afterPoints, afterTickets, pass);
    await insertOperation(db, userId, requestId, 'PURCHASE', result.date, hash, { result });
    return { ...result, duplicate: false };
  });
}

async function reserve(userId, requestId, payload) {
  if (!validKey(requestId)) return { error: 'INVALID_REQUEST' };
  const hash = fingerprint(payload);
  return withAccount(userId, async (db, config, pointBalance, ticketBalance, pass) => {
    const previous = await existing(db, userId, requestId, 'MERGE', hash);
    if (previous) return previous;
    const current = await state(db, userId, config, pointBalance, ticketBalance, pass);
    let source;
    if (current.passActive) {
      source = 'PASS';
    } else if (current.freeRemainingCount > 0) {
      source = 'FREE';
      await db.query(
        `INSERT INTO daily_merge_usage (user_id, usage_date, used_count) VALUES (?, ?, 1)
         ON DUPLICATE KEY UPDATE used_count = used_count + 1`, [userId, current.date]
      );
    } else if (ticketBalance > 0) {
      source = 'TICKET';
      await changeTicketBalance(db, userId, ticketBalance, {
        amount: -1,
        type: 'MERGE_TICKET_USE',
        reference: requestId,
        key: `MERGE_TICKET_USE:${userId}:${requestId}`,
      });
    } else {
      await insertOperation(db, userId, requestId, 'MERGE', current.date, hash, { error: 'MERGE_LIMIT_REACHED' });
      return { error: 'MERGE_LIMIT_REACHED' };
    }
    await insertOperation(db, userId, requestId, 'MERGE', current.date, hash, { source });
    return { duplicate: false };
  });
}

async function finish(userId, requestId, result, errorCode = null) {
  return withAccount(userId, async (db, _config, _pointBalance, ticketBalance) => {
    const [[operation]] = await db.query(
      `SELECT status, marble_source, DATE_FORMAT(usage_date, '%Y-%m-%d') AS usage_date
       FROM merge_operations WHERE user_id = ? AND request_id = ? AND operation_type = 'MERGE' FOR UPDATE`,
      [userId, requestId]
    );
    if (!operation || operation.status !== 'PENDING') return false;
    if (errorCode && (operation.marble_source === 'FREE' || operation.marble_source === null)) {
      await db.query(
        `UPDATE daily_merge_usage SET used_count = GREATEST(used_count - 1, 0)
         WHERE user_id = ? AND usage_date = ?`, [userId, operation.usage_date]
      );
    } else if (errorCode && operation.marble_source === 'TICKET') {
      await changeTicketBalance(db, userId, ticketBalance, {
        amount: 1,
        type: 'MERGE_TICKET_REFUND',
        reference: requestId,
        key: `MERGE_TICKET_REFUND:${userId}:${requestId}`,
      });
    }
    await db.query(
      `UPDATE merge_operations SET status = ?, error_code = ?, result_json = ?
       WHERE user_id = ? AND request_id = ?`,
      [errorCode ? 'FAILED' : 'SUCCEEDED', errorCode, errorCode ? null : JSON.stringify(result), userId, requestId]
    );
    return true;
  });
}

module.exports = { getState, purchaseTicket, reserve, finish };
