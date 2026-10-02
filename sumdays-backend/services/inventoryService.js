const wallet = require('./pointWalletService');

async function getItems(db, userId) {
  const [rows] = await db.query(
    `SELECT item_id AS itemId, quantity FROM user_shop_inventory
     WHERE user_id = ? AND product_type = 'ITEM' ORDER BY item_id`, [userId]
  );
  return rows.map(row => ({ itemId: Number(row.itemId), quantity: Number(row.quantity) }));
}

async function consume(userId, { requestId, itemIds } = {}) {
  if (typeof requestId !== 'string' || !/^[A-Za-z0-9-]{16,80}$/.test(requestId)
      || !Array.isArray(itemIds) || itemIds.length < 1 || itemIds.length > 4
      || itemIds.some(id => !Number.isSafeInteger(id) || id <= 0)
      || new Set(itemIds).size !== itemIds.length) {
    return { error: 'INVALID_CONSUMPTION' };
  }
  const canonicalIds = [...itemIds].sort((a, b) => a - b);
  const encoded = JSON.stringify(canonicalIds);
  return wallet.withWallet(userId, async (db, pointBalance) => {
    const [[previous]] = await db.query(
      'SELECT item_ids, error_code FROM inventory_consumptions WHERE user_id = ? AND request_id = ?',
      [userId, requestId]
    );
    if (previous) {
      if (previous.item_ids !== encoded) return { error: 'IDEMPOTENCY_KEY_CONFLICT' };
      if (previous.error_code) return { error: previous.error_code };
      return { duplicate: true, pointBalance, items: await getItems(db, userId) };
    }
    const items = await getItems(db, userId);
    const quantities = new Map(items.map(item => [item.itemId, item.quantity]));
    const error = canonicalIds.some(id => (quantities.get(id) ?? 0) < 1) ? 'INSUFFICIENT_ITEMS' : null;
    await db.query(
      'INSERT INTO inventory_consumptions (user_id, request_id, item_ids, error_code) VALUES (?, ?, ?, ?)',
      [userId, requestId, encoded, error]
    );
    if (error) return { error };
    await db.query(
      `UPDATE user_shop_inventory SET quantity = quantity - 1
       WHERE user_id = ? AND product_type = 'ITEM' AND item_id IN (?)`, [userId, canonicalIds]
    );
    return { duplicate: false, pointBalance, items: await getItems(db, userId) };
  });
}

module.exports = { consume };
