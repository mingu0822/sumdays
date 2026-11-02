const { pool } = require('../db/db');

/** All point/inventory mutations lock the user's wallet FIRST.
 * Callbacks must contain only DB work: a deadlock may execute them again.
 * AI calls, HTTP responses and device side effects belong outside this transaction.
 */
async function withWallet(userId, action, initialBalance = null) {
  for (let attempt = 0; ; attempt++) {
    const db = await pool.getConnection();
    try {
      await db.beginTransaction();
      const [[existing]] = await db.query(
        'SELECT balance FROM point_wallets WHERE user_id = ? FOR UPDATE', [userId]
      );
      let balance = existing ? Number(existing.balance) : initialBalance;
      if (!existing) {
        if (balance === null) {
          const settings = await readSettings(db, { DEFAULT_POINT_BALANCE: 9000 });
          balance = settings.DEFAULT_POINT_BALANCE;
        }
        await db.query(
          'INSERT INTO point_wallets (user_id, balance, initialization_source) VALUES (?, ?, ?)',
          [userId, balance, initialBalance === null ? 'DEFAULT' : 'LEGACY_APP']
        );
      }
      const result = await action(db, balance, !existing);
      await db.commit();
      return result;
    } catch (error) {
      await db.rollback();
      if (!['ER_LOCK_DEADLOCK', 'ER_LOCK_WAIT_TIMEOUT'].includes(error.code) || attempt >= 2) throw error;
    } finally {
      db.release();
    }
  }
}

async function readSettings(db, defaults) {
  const [rows] = await db.query(
    'SELECT config_key, int_value FROM economy_settings WHERE config_key IN (?)',
    [Object.keys(defaults)]
  );
  const settings = { ...defaults };
  for (const row of rows) settings[row.config_key] = Number(row.int_value);
  if (Object.values(settings).some(value => !Number.isSafeInteger(value) || value < 0)) {
    throw new Error('Invalid economy settings');
  }
  return settings;
}

/** Must run inside withWallet. Balance and ledger either both commit or both roll back. */
async function changeBalance(db, userId, before, { amount, type, reference, key }) {
  const after = before + amount;
  if (!Number.isSafeInteger(amount) || !Number.isSafeInteger(after) || after < 0) {
    throw new Error('Invalid point balance change');
  }
  await db.query('UPDATE point_wallets SET balance = ? WHERE user_id = ?', [after, userId]);
  await db.query(
    `INSERT INTO point_transactions
     (user_id, change_amount, balance_after, transaction_type, reference_id, idempotency_key)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [userId, amount, after, type, reference, key]
  );
  return after;
}

const getBalance = userId => withWallet(userId, async (_db, balance) => balance);

module.exports = { withWallet, readSettings, changeBalance, getBalance };
