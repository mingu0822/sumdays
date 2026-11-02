class StickerError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

const reject = (status, code, message) => { throw new StickerError(status, code, message); };
const stickerIds = new Set([1001, 1002, 1003, 1004, 1005, 1006]);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function validateTarget(owner, date) {
  if (!Number.isSafeInteger(owner) || owner <= 0 || typeof date !== 'string' ||
      !/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(Date.parse(`${date}T00:00:00Z`)) ||
      new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) !== date) {
    reject(400, 'STICKER_INVALID', '일기 정보가 올바르지 않습니다.');
  }
}

function validateGeometry(body) {
  for (const [key, min, max] of [['x', 0, 1], ['y', 0, 1], ['size', 0.08, 0.35], ['rotation', -180, 180]]) {
    if (typeof body[key] !== 'number' || !Number.isFinite(body[key]) || body[key] < min || body[key] > max) {
      reject(400, 'STICKER_INVALID', '스티커 위치나 크기가 올바르지 않습니다.');
    }
  }
  return { x: body.x, y: body.y, size: body.size, rotation: body.rotation };
}

function serialize(row) {
  return {
    id: row.id, ownerId: row.owner_id, authorId: row.author_id,
    authorName: row.author_name || '', date: row.diary_date, stickerId: row.sticker_id,
    x: row.x, y: row.y, size: row.size, rotation: row.rotation,
    active: !row.removed && row.daily_entry_id != null
  };
}

function createStickerService(pool) {
  async function transaction(actor, action) {
    const connection = await pool.getConnection();
    try {
      await connection.beginTransaction();
      // Serializes an author's retries, including retries for different diary targets.
      const [users] = await connection.query('SELECT id FROM users WHERE id = ? FOR UPDATE', [actor]);
      if (!users.length) reject(401, 'STICKER_LOGIN', '다시 로그인해 주세요.');
      const result = await action(connection);
      await connection.commit();
      return result;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally { connection.release(); }
  }

  async function access(connection, actor, owner, date) {
    const [diaries] = await connection.query(
      'SELECT id, diary, is_allowed FROM daily_entry WHERE user_id = ? AND date = ? FOR UPDATE', [owner, date]);
    if (!diaries.length || !diaries[0].diary?.trim()) {
      reject(404, 'STICKER_DIARY_MISSING', '일기를 찾을 수 없습니다.');
    }
    if (actor !== owner) {
      const [friends] = await connection.query(
        `SELECT id FROM friendship WHERE status = 'ACCEPTED'
         AND ((requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?)) FOR UPDATE`,
        [actor, owner, owner, actor]);
      if (!diaries[0].is_allowed || !friends.length) {
        reject(403, 'STICKER_FORBIDDEN', '친구의 공개 일기에만 스티커를 붙일 수 있어요.');
      }
    }
    return diaries[0];
  }

  async function find(connection, id) {
    const [rows] = await connection.query(
      `SELECT s.*, u.nickname AS author_name FROM diary_stickers s
       JOIN users u ON u.id = s.author_id WHERE s.id = ? FOR UPDATE`, [id]);
    return rows[0];
  }

  return {
    async list(actor, owner, date) {
      validateTarget(owner, date);
      return transaction(actor, async connection => {
        const diary = await access(connection, actor, owner, date);
        const [rows] = await connection.query(
          `SELECT s.*, u.nickname AS author_name FROM diary_stickers s JOIN users u ON u.id = s.author_id
           WHERE s.daily_entry_id = ? AND s.removed = 0 ORDER BY s.created_at, s.id`, [diary.id]);
        return rows.map(serialize);
      });
    },

    async place(actor, owner, date, id, body) {
      validateTarget(owner, date);
      if (!uuid.test(id) || !stickerIds.has(body.stickerId)) reject(400, 'STICKER_INVALID', '스티커 정보가 올바르지 않습니다.');
      const geometry = validateGeometry(body);
      return transaction(actor, async connection => {
        const previous = await find(connection, id);
        if (previous) {
          const initial = typeof previous.initial_geometry === 'string'
            ? JSON.parse(previous.initial_geometry) : previous.initial_geometry;
          if (previous.author_id !== actor || previous.owner_id !== owner || previous.diary_date !== date ||
              previous.sticker_id !== body.stickerId || Object.keys(geometry).some(key => initial[key] !== geometry[key])) {
            reject(409, 'STICKER_CONFLICT', '이미 처리된 요청과 정보가 다릅니다.');
          }
          // A receipt survives removal / diary deletion / access revocation. Retrying never spends twice.
          return serialize(previous);
        }
        const diary = await access(connection, actor, owner, date);
        const [[{ count }]] = await connection.query(
          'SELECT COUNT(*) AS count FROM diary_stickers WHERE daily_entry_id = ? AND removed = 0', [diary.id]);
        if (count >= 50) reject(409, 'STICKER_LIMIT', '한 일기에 스티커를 최대 50개까지 붙일 수 있어요.');
        await connection.query(
          `INSERT INTO diary_stickers (id, daily_entry_id, owner_id, author_id, diary_date, sticker_id, x, y, size, rotation, initial_geometry)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [id, diary.id, owner, actor, date, body.stickerId, geometry.x, geometry.y, geometry.size, geometry.rotation, JSON.stringify(geometry)]);
        return serialize(await find(connection, id));
      });
    },

    async update(actor, owner, date, id, body) {
      validateTarget(owner, date);
      if (!uuid.test(id)) reject(400, 'STICKER_INVALID', '스티커 정보가 올바르지 않습니다.');
      const geometry = validateGeometry(body);
      return transaction(actor, async connection => {
        const diary = await access(connection, actor, owner, date);
        const row = await find(connection, id);
        if (!row || row.daily_entry_id !== diary.id || row.removed) reject(404, 'STICKER_MISSING', '스티커를 찾을 수 없습니다.');
        if (row.author_id !== actor) reject(403, 'STICKER_FORBIDDEN', '내가 붙인 스티커만 옮길 수 있어요.');
        await connection.query('UPDATE diary_stickers SET x = ?, y = ?, size = ?, rotation = ? WHERE id = ?',
          [geometry.x, geometry.y, geometry.size, geometry.rotation, id]);
        return serialize({ ...row, ...geometry });
      });
    },

    async remove(actor, owner, date, id) {
      validateTarget(owner, date);
      if (!uuid.test(id)) reject(400, 'STICKER_INVALID', '스티커 정보가 올바르지 않습니다.');
      return transaction(actor, async connection => {
        const diary = await access(connection, actor, owner, date);
        const row = await find(connection, id);
        if (!row || row.daily_entry_id !== diary.id) reject(404, 'STICKER_MISSING', '스티커를 찾을 수 없습니다.');
        if (row.author_id !== actor && owner !== actor) reject(403, 'STICKER_FORBIDDEN', '이 스티커를 삭제할 수 없습니다.');
        await connection.query('UPDATE diary_stickers SET removed = 1 WHERE id = ?', [id]);
        return { id };
      });
    }
  };
}

module.exports = { createStickerService, StickerError, validateTarget, validateGeometry };
