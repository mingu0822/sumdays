// 실제 로컬 MySQL + HTTP 라우트. AI는 메모리 스트림으로 대체하므로 외부 호출 없음.
const path = require('node:path');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { Readable } = require('node:stream');
process.env.DB_TARGET = 'local';
require('dotenv').config({ path: path.join(__dirname, '..', '.env.local'), quiet: true });
const { pool, dbConfig } = require('../db/db');
if (!['127.0.0.1', 'localhost', '::1'].includes(dbConfig.host) || !dbConfig.database.endsWith('_local')) {
  throw new Error('Only loopback *_local databases may be tested');
}
const marble = require('../services/mergeMarbleService');
const axios = require('axios');
const express = require('express');
const request = require('supertest');
const controller = require('../controllers/ai/mergeController');
const marbleController = require('../controllers/mergeMarbleController');
const requireLogin = require('../middlewares/requireLogin');
const ids = [];
const marker = `merge-review-${randomUUID()}`;
let aiCalls = 0;
let aiMode = 'success';
let lastAiPayload;
const originalPost = axios.post;
axios.post = async (url, payload) => {
  lastAiPayload = payload;
  aiCalls++;
  if (aiMode === 'failure') throw new Error('Simulated AI failure');
  if (url.endsWith('/paragraph')) {
    if (aiMode === 'empty') return { data: Readable.from([]) };
    if (aiMode === 'broken') return { data: Readable.from((async function* () {
      yield Buffer.from('중간 내용');
      throw new Error('Simulated broken stream');
    })()) };
    const buffer = Buffer.from('산책을 하고 일기를 썼다.', 'utf8');
    return { data: Readable.from([buffer.subarray(0, 2), buffer.subarray(2)]) };
  }
  return { data: { diary: '산책을 하고 일기를 썼다.', mood: '즐거웠어.' } };
};

const app = express();
app.use(express.json());
app.use((req, res, next) => {
  const id = Number(req.header('X-Local-Test-User'));
  req.user = ids.includes(id) ? { userId: id } : null;
  next();
});
app.post('/merge', requireLogin, controller.merge);
app.post('/preview', requireLogin, controller.preview);
app.get('/marbles', requireLogin, marbleController.getState);
app.post('/tickets/purchase', requireLogin, marbleController.purchaseTicket);

const body = (requestId = randomUUID(), endFlag = false) => ({
  request_id: requestId, end_flag: endFlag,
  memos: [{ content: '산책을 했다.', order: 0 }, { content: '일기를 썼다.', order: 1 }],
  style_prompt: { tone: '담담한' }, style_examples: ['오늘은 즐거웠다.'], length_level: 1,
});
async function newUser() {
  const index = ids.length;
  const [result] = await pool.query(
    'INSERT INTO users (email, password_hash, nickname) VALUES (?, ?, ?)',
    [`${marker}-${index}@local.test`, 'local-test-only', `${marker.slice(0, 35)}-${index}`]
  );
  ids.push(result.insertId);
  return result.insertId;
}
const postMerge = (id, data) => request(app).post('/merge').set('X-Local-Test-User', String(id)).send(data);
const ticketBody = (state, packId = 'MERGE_TICKET_1', requestId = randomUUID()) => {
  const product = state.ticketProducts.find((item) => item.packId === packId);
  assert.ok(product, `Missing test ticket product: ${packId}`);
  return { requestId, packId, expectedPrice: product.pointPrice };
};
const pass = message => console.log(`PASS ${message}`);

async function main() {
  const user = await newUser();
  const other = await newUser();
  const initial = await marble.getState(user);
  assert.equal(initial.freeLimit, 5);
  assert.equal(initial.freeRemainingCount, 5);
  assert.equal(initial.ticketBalance, 0);
  assert.equal(initial.ticketProducts.length, 4);
  assert.equal((await request(app).post('/merge').send(body())).status, 401);
  assert.equal((await request(app).post('/tickets/purchase').send(ticketBody(initial))).status, 401);
  assert.equal((await postMerge(user, { ...body(), memos: [] })).status, 400);
  assert.equal((await marble.getState(user)).usedCount, 0);
  pass('로그인 필수 / 잘못된 입력은 횟수 미차감');

  const firstRequest = body();
  const first = await postMerge(user, firstRequest);
  assert.equal(first.status, 200);
  assert.equal(first.text, '산책을 하고 일기를 썼다.');
  const callsAfterFirst = aiCalls;
  assert.equal((await postMerge(user, firstRequest)).text, first.text);
  assert.equal(aiCalls, callsAfterFirst);
  assert.equal((await marble.getState(user)).usedCount, 1);
  const conflict = await postMerge(user, { ...firstRequest, length_level: 2 });
  assert.equal(conflict.body.code, 'IDEMPOTENCY_KEY_CONFLICT');
  assert.equal((await postMerge(user, body(randomUUID(), true))).body.result.diary, first.text);
  pass('스트리밍 한글 / 최종 일기 / 같은 요청 재전송 시 AI 호출·차감 없음');

  for (const mode of ['failure', 'empty', 'broken']) {
    aiMode = mode;
    const before = (await marble.getState(user)).usedCount;
    await postMerge(user, body()).catch(() => null);
    assert.equal((await marble.getState(user)).usedCount, before);
  }
  aiMode = 'success';
  pass('AI 오류 / 빈 응답 / 끊긴 스트림 무료 횟수 반환');

  const parallel = await Promise.all(Array.from({ length: 5 }, () => postMerge(user, body())));
  assert.equal(parallel.filter(res => res.status === 200).length, 3);
  assert.equal(parallel.filter(res => res.body.code === 'MERGE_LIMIT_REACHED').length, 2);
  assert.equal((await marble.getState(user)).freeRemainingCount, 0);
  assert.equal((await marble.getState(other)).usedCount, 0);
  pass('동시 요청에서 무료 5회 초과 방지 / 계정 분리');

  const preview = await request(app).post('/preview').set('X-Local-Test-User', String(user)).send({
    ...body(), memos: [{ content: '우회용 사용자 메모', order: 0 }],
  });
  assert.equal(preview.status, 200);
  assert.equal(lastAiPayload.memos[0].content, '아침에 늦잠을 자서 밥을 굶었다.');
  assert.equal((await marble.getState(user)).usedCount, 5);
  pass('한도 소진 후 이전 결과 복구 / 스타일 예시는 고정 문장만 사용하고 횟수 미차감');

  const quote = await marble.getState(user);
  const purchaseRequest = ticketBody(quote);
  const purchases = await Promise.all(Array.from({ length: 2 }, () =>
    request(app).post('/tickets/purchase').set('X-Local-Test-User', String(user)).send(purchaseRequest)));
  assert.ok(purchases.every(res => res.status === 200));
  let state = await marble.getState(user);
  assert.equal(state.ticketBalance, 1);
  assert.equal(state.pointBalance, quote.pointBalance - purchaseRequest.expectedPrice);
  assert.equal((await postMerge(user, body())).status, 200);
  assert.equal((await marble.getState(user)).ticketBalance, 0);
  pass('횟수권 구매 원자성 / 동시 재시도 이중 차감 없음 / 무료 소진 뒤 횟수권 사용');

  const fiveTicket = ticketBody(state, 'MERGE_TICKET_5');
  state = await marble.purchaseTicket(user, fiveTicket);
  assert.equal(state.ticketBalance, 5);
  const badPrice = { ...ticketBody(state, 'MERGE_TICKET_10'), expectedPrice: 1 };
  assert.equal((await marble.purchaseTicket(user, badPrice)).error, 'MERGE_TICKET_PRICE_CHANGED');
  assert.equal((await marble.purchaseTicket(user, badPrice)).error, 'MERGE_TICKET_PRICE_CHANGED');
  const otherState = await marble.getState(other);
  await pool.query('UPDATE point_wallets SET balance = 0 WHERE user_id = ?', [other]);
  const denied = ticketBody(otherState);
  assert.equal((await marble.purchaseTicket(other, denied)).error, 'INSUFFICIENT_POINTS');
  await pool.query('UPDATE point_wallets SET balance = ? WHERE user_id = ?', [otherState.pointBalance, other]);
  assert.equal((await marble.purchaseTicket(other, denied)).error, 'INSUFFICIENT_POINTS');
  pass('횟수권 가격 변경 확인 / 거절 응답 유실 뒤 자동 구매 없음 / 누적 횟수권 구매');

  const inFlight = randomUUID();
  assert.equal((await marble.reserve(other, inFlight, { input: 'test' })).duplicate, false);
  assert.equal((await marble.reserve(other, inFlight, { input: 'test' })).error, 'MERGE_IN_PROGRESS');
  await pool.query('UPDATE merge_operations SET expires_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 SECOND) WHERE user_id = ? AND request_id = ?', [other, inFlight]);
  assert.equal((await marble.getState(other)).usedCount, 0);
  assert.equal(await marble.finish(other, inFlight, 'late success'), false);
  pass('서버 종료 뒤 예약 만료 복구 / 만료 요청 늦은 완료 차단');
}

main().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  axios.post = originalPost;
  for (const id of ids) await pool.query('DELETE FROM users WHERE id = ? AND email LIKE ?', [id, `${marker}-%@local.test`]);
  await pool.end();
  console.log('임시 로컬 계정 및 해당 계정의 테스트 거래 정리 완료');
});
