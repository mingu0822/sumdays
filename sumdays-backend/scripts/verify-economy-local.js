// Real local MySQL + HTTP regression checks. Never contacts AWS, AI or ad networks.
const path = require('node:path');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
process.env.DB_TARGET = 'local';
require('dotenv').config({ path: path.join(__dirname, '..', '.env.local'), quiet: true });
const { pool, dbConfig } = require('../db/db');
if (!['127.0.0.1', 'localhost', '::1'].includes(dbConfig.host) || !dbConfig.database.endsWith('_local')) {
  throw new Error('Only loopback *_local databases may be tested');
}
const wallet = require('../services/pointWalletService');
const mission = require('../services/missionService');
const marble = require('../services/mergeMarbleService');
const express = require('express');
const request = require('supertest');
const ids = [];
const marker = `economy-review-${randomUUID()}`;
const app = express();
app.use(express.json());
app.use((req, _res, next) => {
  const id = Number(req.header('X-Local-Test-User'));
  req.user = ids.includes(id) ? { userId: id } : null;
  next();
});
app.use('/missions', require('../middlewares/requireLogin'), require('../routes/missions'));
const post = (id, route, data) => request(app).post(`/missions/${route}`).set('X-Local-Test-User', String(id)).send(data);
const catalog = id => mission.getShopCatalog(id);
const count = async (id, itemId) => (await catalog(id)).products.find(p => p.productType === 'ITEM' && p.itemId === itemId)?.quantity ?? 0;
const purchase = (id, itemId, key = randomUUID(), productType = 'ITEM') =>
  mission.purchaseShopProduct(id, { productType, itemId, idempotencyKey: key });
const consume = (id, itemIds, requestId = randomUUID()) => post(id, 'shop/consume', { requestId, itemIds });
const pass = message => console.log(`PASS ${message}`);

async function newUser() {
  const index = ids.length;
  const [row] = await pool.query('INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)',
    [`${marker}-${index}@local.test`, 'local-test-only', `economy-${index}-${marker.slice(-20)}`]);
  ids.push(row.insertId);
  await mission.initializeWallet(row.insertId, 9000);
  return row.insertId;
}

async function main() {
  const user = await newUser();
  const other = await newUser();
  const initial = await wallet.getBalance(user);
  const buyKey = randomUUID();
  const bought = await purchase(user, 1, buyKey);
  assert.ok(!bought.error);
  const adId = randomUUID();
  const ad = await mission.claimAdReward(user, adId);
  const current = await wallet.getBalance(user);
  assert.equal((await purchase(user, 1, buyKey)).pointBalance, current);
  await purchase(user, 2);
  assert.equal((await mission.claimAdReward(user, adId)).pointBalance, await wallet.getBalance(user));
  assert.equal((await catalog(user)).pointBalance, initial - bought.price - (await catalog(user)).products.find(p => p.productType === 'ITEM' && p.itemId === 2).price + ad.rewardPoints);
  pass('Lost purchase/ad response returns CURRENT balance, not historical balance_after');

  assert.equal((await request(app).post('/missions/shop/consume').send({ requestId: randomUUID(), itemIds: [1] })).status, 401);
  assert.equal((await consume(user, [1, 1])).status, 400);
  const useKey = randomUUID();
  const used = await consume(user, [1, 2], useKey);
  assert.equal(used.status, 200);
  assert.equal(await count(user, 1), 0);
  assert.equal(await count(user, 2), 0);
  assert.equal((await consume(user, [2, 1], useKey)).body.data.duplicate, true);
  assert.equal(await count(user, 1), 0);
  assert.equal((await consume(user, [1], useKey)).status, 400);
  assert.equal((await consume(other, [1, 2], useKey)).status, 409);
  assert.equal(await count(other, 1), 0);
  pass('Consume then revisit catalog stays consumed; reordered retry once; key conflicts/account isolation');

  await purchase(user, 1);
  const deniedId = randomUUID();
  assert.equal((await consume(user, [1, 2], deniedId)).status, 409);
  assert.equal(await count(user, 1), 1); // multi-item consumption is all-or-nothing
  await purchase(user, 2);
  assert.equal((await consume(user, [1, 2], deniedId)).status, 409);
  assert.equal(await count(user, 1), 1);
  assert.equal(await count(user, 2), 1);
  const once = randomUUID();
  const duplicate = await Promise.all(Array.from({ length: 5 }, () => consume(user, [1, 2], once)));
  assert.ok(duplicate.every(result => result.status === 200));
  assert.equal(duplicate.filter(result => !result.body.data.duplicate).length, 1);
  assert.equal(await count(user, 1), 0);
  await purchase(user, 1);
  const competitors = await Promise.all(Array.from({ length: 5 }, () => consume(user, [1])));
  assert.equal(competitors.filter(result => result.status === 200).length, 1);
  assert.equal(await count(user, 1), 0);
  pass('Atomic materials; persisted rejection never spends later; same/different concurrent IDs do not overconsume');

  const themeUser = await newUser();
  const themes = await Promise.all(Array.from({ length: 5 }, () => purchase(themeUser, 2, randomUUID(), 'THEME')));
  assert.ok(themes.every(result => !result.error));
  assert.equal(themes.filter(result => !result.alreadyOwned).length, 1);
  assert.equal(await wallet.getBalance(themeUser), 9000 - themes.find(result => !result.alreadyOwned).price);
  const ads = await Promise.all(Array.from({ length: 5 }, () => mission.claimAdReward(themeUser, randomUUID())));
  assert.equal(ads.filter(result => !result.error).length, 3);
  pass('Wallet serialization protects concurrent theme purchase and daily ad limit');

  const today = (await marble.getState(other)).date;
  await pool.query('INSERT INTO daily_entry (user_id, date, diary) VALUES (?, ?, ?)', [other, today, 'local regression diary']);
  const reward = await Promise.all(Array.from({ length: 4 }, () => mission.claimMission(other, 'DAILY_COMPLETE_DIARY')));
  assert.equal(reward.filter(result => !result.error).length, 1);
  assert.equal(reward.filter(result => result.error === 'MISSION_ALREADY_CLAIMED').length, 3);
  const beforeRollback = await wallet.getBalance(other);
  const rollbackKey = `TEST:${randomUUID()}`;
  await assert.rejects(wallet.withWallet(other, async (db, balance) => {
    await wallet.changeBalance(db, other, balance, { amount: -1, type: 'SHOP_PURCHASE', reference: 'ITEM:1', key: rollbackKey });
    throw new Error('simulated transaction failure');
  }), /simulated transaction failure/);
  assert.equal(await wallet.getBalance(other), beforeRollback);
  const [[ledger]] = await pool.query('SELECT COUNT(*) AS n FROM point_transactions WHERE user_id = ? AND idempotency_key = ?', [other, rollbackKey]);
  assert.equal(Number(ledger.n), 0);
  pass('Mission reward once; failed wallet transaction rolls back balance AND ledger');
}

main().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  for (const id of ids) await pool.query('DELETE FROM users WHERE id = ? AND email LIKE ?', [id, `${marker}-%@local.test`]);
  await pool.end();
  console.log('Only generated local test accounts and their cascading records removed');
});
