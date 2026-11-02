jest.mock('../db/db', () => ({ pool: {} }));
const express = require('express');
const request = require('supertest');
const { randomUUID } = require('crypto');
const { createStickerRouter } = require('../routes/stickers');
const { createStickerService } = require('../services/stickerService');
const requireLogin = require('../middlewares/requireLogin');

// Exercise the real router, validation, permissions and transaction orchestration with no live DB.
function fixture() {
  const diaries = new Map([
    ['1:2026-10-11', { id: 11, diary: 'My private diary', is_allowed: 0 }],
    ['2:2026-10-11', { id: 22, diary: 'Public friend diary', is_allowed: 1 }],
    ['2:2026-10-12', { id: 23, diary: 'Another entry', is_allowed: 1 }]
  ]);
  const friends = new Set(['1:2', '2:3']);
  const rows = new Map();
  const connection = {
    beginTransaction: jest.fn().mockResolvedValue(), commit: jest.fn().mockResolvedValue(),
    rollback: jest.fn().mockResolvedValue(), release: jest.fn(),
    query: jest.fn(async (sql, p) => {
      if (sql.startsWith('SELECT id FROM users')) return [[{ id: p[0] }]];
      if (sql.startsWith('SELECT id, diary')) return [[diaries.get(`${p[0]}:${p[1]}`)].filter(Boolean)];
      if (sql.includes('FROM friendship')) return [[...(friends.has(`${p[0]}:${p[1]}`) || friends.has(`${p[1]}:${p[0]}`) ? [{ id: 1 }] : [])]];
      if (sql.includes('WHERE s.id = ?')) return [[rows.get(p[0])].filter(Boolean)];
      if (sql.startsWith('SELECT COUNT')) return [[{ count: [...rows.values()].filter(r => r.daily_entry_id === p[0] && !r.removed).length }]];
      if (sql.includes('WHERE s.daily_entry_id')) return [[...rows.values()].filter(r => r.daily_entry_id === p[0] && !r.removed)];
      if (sql.startsWith('INSERT INTO diary_stickers')) {
        rows.set(p[0], { id: p[0], daily_entry_id: p[1], owner_id: p[2], author_id: p[3], diary_date: p[4],
          sticker_id: p[5], x: p[6], y: p[7], size: p[8], rotation: p[9], initial_geometry: p[10],
          author_name: `User ${p[3]}`, removed: 0 });
        return [{ affectedRows: 1 }];
      }
      if (sql.startsWith('UPDATE diary_stickers SET x')) {
        Object.assign(rows.get(p[4]), { x: p[0], y: p[1], size: p[2], rotation: p[3] });
        return [{ affectedRows: 1 }];
      }
      if (sql.startsWith('UPDATE diary_stickers SET removed')) { rows.get(p[0]).removed = 1; return [{ affectedRows: 1 }]; }
      throw new Error(`Unexpected SQL: ${sql}`);
    })
  };
  const pool = { getConnection: jest.fn().mockResolvedValue(connection) };
  const service = createStickerService(pool);
  const app = express();
  app.use(express.json());
  app.use((req, res, next) => { if (req.get('x-test-user')) req.user = { userId: Number(req.get('x-test-user')) }; next(); });
  app.use('/stickers', requireLogin, createStickerRouter(service));
  const put = (owner = 2, actor = 1, id = randomUUID(), body = geometry) =>
    request(app).put(`/stickers/${owner}/2026-10-11/${id}`).set('x-test-user', String(actor)).send(body);
  return { app, put, rows, diaries, friends, connection, pool, service };
}
const geometry = { stickerId: 1001, x: 0.3, y: 0.7, size: 0.18, rotation: -25 };

test('all sticker endpoints require login', async () => {
  const { app, pool } = fixture();
  expect((await request(app).get('/stickers/2/2026-10-11')).status).toBe(401);
  expect((await request(app).put(`/stickers/2/2026-10-11/${randomUUID()}`).send(geometry)).status).toBe(401);
  expect(pool.getConnection).not.toHaveBeenCalled();
});

test('own private diary accepts a sticker and returns it when reopened', async () => {
  const f = fixture();
  const saved = await f.put(1);
  expect(saved.status).toBe(200);
  expect(saved.body.data).toMatchObject({ ...geometry, ownerId: 1, authorId: 1, active: true });
  const loaded = await request(f.app).get('/stickers/1/2026-10-11').set('x-test-user', '1');
  expect(loaded.body.data).toHaveLength(1);
  expect(loaded.body.data[0].id).toBe(saved.body.data.id);
});

test('accepted friends in either direction can place on a public diary and the owner sees it', async () => {
  const f = fixture();
  expect((await f.put(2, 1)).status).toBe(200);
  expect((await f.put(2, 3)).status).toBe(200);
  const list = await request(f.app).get('/stickers/2/2026-10-11').set('x-test-user', '2');
  expect(list.body.data.map(s => s.authorId)).toEqual([1, 3]);
  expect(f.connection.query.mock.calls.some(([sql]) => sql.includes("status = 'ACCEPTED'") && sql.includes('FOR UPDATE'))).toBe(true);
});

test('private entries and missing friendship reject both viewing and placement', async () => {
  const f = fixture();
  f.diaries.get('2:2026-10-11').is_allowed = 0;
  expect((await f.put()).status).toBe(403);
  expect((await request(f.app).get('/stickers/2/2026-10-11').set('x-test-user', '1')).status).toBe(403);
  f.diaries.get('2:2026-10-11').is_allowed = 1;
  f.friends.clear();
  expect((await f.put()).status).toBe(403);
  expect(f.rows.size).toBe(0);
  expect(f.connection.rollback).toHaveBeenCalledTimes(3);
  expect(f.connection.release).toHaveBeenCalledTimes(3);
});

test('missing and empty diaries cannot receive stickers', async () => {
  const f = fixture();
  f.diaries.delete('2:2026-10-11');
  expect((await f.put()).body.code).toBe('STICKER_DIARY_MISSING');
  f.diaries.set('2:2026-10-11', { id: 22, diary: '  ', is_allowed: 1 });
  expect((await f.put()).status).toBe(404);
  expect(f.rows.size).toBe(0);
});

test.each([
  { stickerId: 42 }, { x: -0.01 }, { y: 1.01 }, { size: 0.01 }, { size: 0.36 },
  { rotation: 181 }, { x: '0.5' }, { x: null }
])('rejects invalid catalog IDs and geometry: %p', async invalid => {
  const f = fixture();
  expect((await f.put(2, 1, randomUUID(), { ...geometry, ...invalid })).status).toBe(400);
  expect(f.rows.size).toBe(0);
  expect(f.pool.getConnection).not.toHaveBeenCalled();
});

test('validates diary dates, target IDs and request UUIDs', async () => {
  const f = fixture();
  for (const path of [`2/2026-02-30/${randomUUID()}`, `0/2026-10-11/${randomUUID()}`, '2/2026-10-11/not-a-uuid']) {
    expect((await request(f.app).put(`/stickers/${path}`).set('x-test-user', '1').send(geometry)).status).toBe(400);
  }
});

test('the authenticated actor cannot be spoofed by request fields', async () => {
  const f = fixture();
  const result = await f.put(2, 1, randomUUID(), { ...geometry, authorId: 2, ownerId: 1 });
  expect(result.body.data).toMatchObject({ authorId: 1, ownerId: 2 });
});

test('lost-response retries return the original receipt exactly once, even after revocation', async () => {
  const f = fixture();
  const id = randomUUID();
  expect((await f.put(2, 1, id)).status).toBe(200);
  f.friends.clear();
  f.diaries.get('2:2026-10-11').is_allowed = 0;
  expect((await f.put(2, 1, id)).status).toBe(200);
  expect(f.rows.size).toBe(1);
  expect(f.connection.query.mock.calls.filter(([sql]) => sql.startsWith('INSERT')).length).toBe(1);
  expect((await f.put(2, 3, id)).status).toBe(409);
  expect((await f.put(2, 1, id, { ...geometry, x: 0.8 })).status).toBe(409);
});

test('only the original author can move a sticker, and moving does not add a placement', async () => {
  const f = fixture();
  const id = randomUUID();
  await f.put(2, 1, id);
  const path = `/stickers/2/2026-10-11/${id}`;
  expect((await request(f.app).patch(path).set('x-test-user', '2').send(geometry)).status).toBe(403);
  const changed = await request(f.app).patch(path).set('x-test-user', '1').send({ ...geometry, x: 0.8, rotation: 90 });
  expect(changed.body.data).toMatchObject({ x: 0.8, rotation: 90 });
  expect((await f.put(2, 1, id)).body.data.x).toBe(0.8);
  expect(f.rows.size).toBe(1);
});

test('the author or diary owner can remove; other friends cannot; retries never resurrect it', async () => {
  const f = fixture();
  const id = randomUUID();
  await f.put(2, 1, id);
  const path = `/stickers/2/2026-10-11/${id}`;
  expect((await request(f.app).delete(path).set('x-test-user', '3')).status).toBe(403);
  expect((await request(f.app).delete(path).set('x-test-user', '2')).status).toBe(200);
  expect((await request(f.app).delete(path).set('x-test-user', '2')).status).toBe(200);
  expect((await f.put(2, 1, id)).body.data.active).toBe(false);
  expect((await request(f.app).get('/stickers/2/2026-10-11').set('x-test-user', '2')).body.data).toEqual([]);
  const second = randomUUID();
  await f.put(2, 1, second);
  expect((await request(f.app).delete(`/stickers/2/2026-10-11/${second}`).set('x-test-user', '1')).status).toBe(200);
});

test('diary deletion receipts persist without transferring stickers to a recreated diary', async () => {
  const f = fixture();
  const id = randomUUID();
  await f.put(2, 1, id);
  f.rows.get(id).daily_entry_id = null; // ON DELETE SET NULL
  f.diaries.set('2:2026-10-11', { id: 99, diary: 'Recreated', is_allowed: 1 });
  expect((await f.put(2, 1, id)).body.data.active).toBe(false);
  expect((await request(f.app).get('/stickers/2/2026-10-11').set('x-test-user', '2')).body.data).toEqual([]);
});

test('cross-diary edits and deletes fail, and sticker cap is enforced', async () => {
  const f = fixture();
  const id = randomUUID();
  await f.put(2, 1, id);
  expect((await request(f.app).patch(`/stickers/2/2026-10-12/${id}`).set('x-test-user', '1').send(geometry)).status).toBe(404);
  expect((await request(f.app).delete(`/stickers/2/2026-10-12/${id}`).set('x-test-user', '1')).status).toBe(404);
  for (let i = 1; i < 50; i++) f.rows.set(`seed-${i}`, { daily_entry_id: 22, removed: 0 });
  expect((await f.put()).body.code).toBe('STICKER_LIMIT');
});

test('database failures roll back, release connections, and return retryable errors', async () => {
  const f = fixture();
  jest.spyOn(console, 'error').mockImplementation(() => {});
  f.connection.query.mockRejectedValueOnce(new Error('Database unavailable'));
  const result = await f.put();
  expect(result.status).toBe(500);
  expect(result.body.code).toBe('STICKER_SERVER');
  expect(f.connection.rollback).toHaveBeenCalledTimes(1);
  expect(f.connection.release).toHaveBeenCalledTimes(1);
  console.error.mockRestore();
});
