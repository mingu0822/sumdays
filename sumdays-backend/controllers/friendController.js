const { pool } = require('../db/db');
const moment = require('moment-timezone');
const { success, fail } = require('../utils/response');

async function getFriendship(u1, u2, isDirected = false) {
  console.log(`[getFriendship] u1=${u1}, u2=${u2}`);

  let query;
  let params;

  if (isDirected) {
    query = `
      SELECT * FROM friendship 
      WHERE requester_id = ? AND receiver_id = ?
    `;
    params = [u1, u2];
  } else {
    // 기존: 양방향 검사
    query = `
      SELECT * FROM friendship 
      WHERE (requester_id = ? AND receiver_id = ?) 
         OR (requester_id = ? AND receiver_id = ?)
    `;
    params = [u1, u2, u2, u1];
  }

  const [rows] = await pool.query(query, params);

  console.log(`[getFriendship] result:`, rows);

  return rows.length > 0 ? rows[0] : null;
}

const friendController = {

  requestFriend: async (req, res) => {
    const requesterId = req.user.userId;
    const { receiverEmail } = req.body;

    console.log(`[requestFriend] requester=${requesterId}, email=${receiverEmail}`);

    try {
      const [users] = await pool.query(
        'SELECT id FROM users WHERE email = ?', 
        [receiverEmail]
      );

      console.log(`[requestFriend] user lookup result:`, users);

      if (users.length === 0) {
        console.log(`[requestFriend] user not found`);
        return fail(res, "USER_NOT_FOUND", "존재하지 않는 사용자입니다.", 404);
      }

      const receiverId = users[0].id;

      if (requesterId === receiverId) {
        console.log(`[requestFriend] self request blocked`);
        return fail(res, "SELF_REQUEST", "자기 자신에게는 요청할 수 없습니다.", 400);
      }

      const existing = await getFriendship(requesterId, receiverId);

      if (existing) {
        console.log(`[requestFriend] existing relationship:`, existing);

        const { status, requester_id } = existing;

        if (status === 'ACCEPTED') {
          console.log(`[requestFriend] already friends`);
          return fail(res, "ALREADY_FRIEND", "이미 친구 상태입니다.", 409);
        }

        if (status === 'PENDING') {
          if (requester_id === requesterId) {
            console.log(`[requestFriend] waiting for acceptance`);
            return fail(res, "REQUEST_ALREADY_SENT", "상대방의 수락을 기다리는 중입니다.", 409);
          } else {
            console.log(`[requestFriend] auto accept triggered`);
            await pool.query(
              'UPDATE friendship SET status = "ACCEPTED" WHERE id = ?',
              [existing.id]
            );

            const [friendRows] = await pool.query(`
              SELECT u.id, u.nickname, u.profile_image_url, u.created_at,
                    ui.count_diaries, ui.streak, ui.count_weekly_summaries, ui.last_diary_update_date
              FROM users u
              JOIN user_info ui ON u.id = ui.user_id
              WHERE u.id = ?
            `, [receiverId]);

            const friend = friendRows[0];

            const today = moment().tz('Asia/Seoul').format('YYYY-MM-DD');
            const yesterday = moment().tz('Asia/Seoul').subtract(1, 'days').format('YYYY-MM-DD');

            const lastUpdate = friend.last_diary_update_date
              ? moment(friend.last_diary_update_date).format('YYYY-MM-DD')
              : null;

            const isStreakValid = lastUpdate === today || lastUpdate === yesterday;
            const finalStreak = isStreakValid ? friend.streak : 0;

            const friendInfo = {
              id: friend.id,
              nickname: friend.nickname,
              profileImageUrl: friend.profile_image_url,
              createdAt: moment(friend.created_at).format('YYYY-MM-DD'),
              countDiaries: friend.count_diaries,
              streak: finalStreak,
              countWeeklySummaries: friend.count_weekly_summaries,
              lastDiaryUpdateDate: friend.last_diary_update_date
            };
            return success(res, "AUTO_ACCEPTED", "상대방의 요청이 있어 즉시 친구가 되었습니다!", friendInfo);
          }
        }

        if (status === 'REJECTED') {
          console.log(`[requestFriend] rejected state`);
          return fail(res, "ALREADY_REJECTED", "이미 거절된 요청입니다.", 409);
        }
      }

      console.log(`[requestFriend] inserting new request`);
      await pool.query(
        'INSERT INTO friendship (requester_id, receiver_id, status) VALUES (?, ?, "PENDING")',
        [requesterId, receiverId]
      );

      return success(res, "REQUEST_SENT", "친구 요청 완료", null, 201);

    } catch (error) {
      console.error(`[requestFriend ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "Database process failed", 500);
    }
  },

  cancelRequest: async (req, res) => {
    const requesterId = req.user.userId;
    const { receiverId } = req.body;

    console.log(`[cancelRequest] requester=${requesterId}, receiver=${receiverId}`);

    try {
      const [result] = await pool.query(
        'DELETE FROM friendship WHERE requester_id = ? AND receiver_id = ? AND status = "PENDING"',
        [requesterId, receiverId]
      );

      console.log(`[cancelRequest] affectedRows=${result.affectedRows}`);

      if (result.affectedRows === 0) {
        return fail(res, "REQUEST_NOT_FOUND", "취소할 요청이 없습니다.", 404);
      }

      return success(res, "REQUEST_CANCELLED", "요청 취소 완료");

    } catch (error) {
      console.error(`[cancelRequest ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "Database delete failed", 500);
    }
  },

  handleRequest: async (req, res) => {
    const myId = req.user.userId;
    const { requesterId, action } = req.body;

    console.log(`[handleRequest] myId=${myId}, requesterId=${requesterId}, action=${action}`);

    try {
      const [rows] = await pool.query(
        'SELECT * FROM friendship WHERE requester_id = ? AND receiver_id = ? AND status = "PENDING"',
        [requesterId, myId]
      );

      console.log(`[handleRequest] request data:`, rows);

      if (rows.length === 0) {
        return fail(res, "INVALID_REQUEST", "유효하지 않은 요청입니다.", 403);
      }

      const request = rows[0];
      if (request.status !== "PENDING") {
        return fail(res, "ALREADY_PROCESSED", "이미 처리된 요청입니다.", 409);
      }

      if (action === 'ACCEPT') {
        await pool.query(
          'UPDATE friendship SET status = "ACCEPTED" WHERE requester_id = ? AND receiver_id = ? AND status = "PENDING"',
          [requesterId, myId]
        );

        return success(res, "REQUEST_ACCEPTED", "친구 수락 완료");
      }

      if (action === 'REJECT') {
        await pool.query(
          'DELETE FROM friendship WHERE requester_id = ? AND receiver_id = ? AND status = "PENDING"',
          [requesterId, myId]
        );
        return success(res, "REQUEST_REJECTED", "친구 요청 거절 완료");
      }

      return fail(res, "INVALID_ACTION", "올바르지 않은 요청 처리 방식입니다.", 400);

    } catch (error) {
      console.error(`[handleRequest ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "Database update failed", 500);
    }
  },

  getPendingRequests: async (req, res) => {
    const myId = req.user.userId;

    console.log(`[getPendingRequests] Fetching all pending for myId=${myId}`);

    try {
      const [receivedRows, sentRows] = await Promise.all([
        pool.query(
          `SELECT u.id AS userId, u.nickname, u.profile_image_url 
          FROM friendship f 
          JOIN users u ON f.requester_id = u.id 
          WHERE f.receiver_id = ? AND f.status = "PENDING"`,
          [myId]
        ),
        pool.query(
          `SELECT u.id AS userId, u.nickname, u.profile_image_url 
          FROM friendship f 
          JOIN users u ON f.receiver_id = u.id 
          WHERE f.requester_id = ? AND f.status = "PENDING"`,
          [myId]
        )
      ]);

    const received = receivedRows[0];
    const sent = sentRows[0];

    const data = {
      received,
      sent
    };

    // 👉 여기 변경
    const receivedIds = received.map(u => u.userId);
    const sentIds = sent.map(u => u.userId);

    console.log(
      `[getPendingRequests] receivedIds=${JSON.stringify(receivedIds)}, sentIds=${JSON.stringify(sentIds)}`
    );

      return success(res, "REQUEST_LIST_FETCHED", "요청 목록 조회 성공", data);

    } catch (error) {
      console.error(`[getPendingRequests ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "대기 중인 요청 조회 실패", 500);
    }
  },

  getMyFriends: async (req, res) => {
    const myId = req.user.userId;
    console.log(`[getMyFriends] myId=${myId}`);

    try {
      const [friends] = await pool.query(`
        SELECT u.id, u.nickname, u.profile_image_url, u.created_at,
        ui.count_diaries, ui.streak, ui.count_weekly_summaries, ui.last_diary_update_date
        FROM friendship f
        JOIN users u ON (f.requester_id = u.id OR f.receiver_id = u.id)
        JOIN user_info ui ON u.id = ui.user_id
        WHERE (f.requester_id = ? OR f.receiver_id = ?) 
          AND f.status = "ACCEPTED" AND u.id != ?`, 
        [myId, myId, myId]
      );
      
      const today = moment().tz('Asia/Seoul').format('YYYY-MM-DD');
      const yesterday = moment().tz('Asia/Seoul').subtract(1, 'days').format('YYYY-MM-DD');

      const formattedFriends = friends.map(friend => {
        const lastUpdate = friend.last_diary_update_date
          ? moment(friend.last_diary_update_date).format('YYYY-MM-DD')
          : null;

        const isStreakValid = lastUpdate === today || lastUpdate === yesterday;
        const finalStreak = isStreakValid ? friend.streak : 0;

        return {
          id: friend.id,
          nickname: friend.nickname,
          profileImageUrl: friend.profile_image_url,
          createdAt: moment(friend.created_at).format('YYYY-MM-DD'),
          countDiaries: friend.count_diaries,
          streak: finalStreak,
          countWeeklySummaries: friend.count_weekly_summaries,
          lastDiaryUpdateDate: friend.last_diary_update_date
        };
      });

      return success(res, "FRIEND_LIST_FETCHED", "친구 목록 조회 성공", formattedFriends);

    } catch (error) {
      console.error('❌ [getMyFriends] Error:', error);
      return fail(res, "INTERNAL_SERVER_ERROR", "친구 목록 조회 실패", 500);
    }
  },

  deleteFriend: async (req, res) => {
    const myId = req.user.userId;
    const { friendId } = req.params;

    console.log(`[deleteFriend] myId=${myId}, friendId=${friendId}`);

    try {
      const [result] = await pool.query(
        `DELETE FROM friendship 
        WHERE ((requester_id = ? AND receiver_id = ?) 
            OR (requester_id = ? AND receiver_id = ?)) 
          AND status = "ACCEPTED"`,
        [myId, friendId, friendId, myId]
      );

      if (result.affectedRows === 0) {
        return fail(res, "FRIEND_NOT_FOUND", "삭제할 친구 관계가 없습니다.", 404);
      }

      console.log(`[deleteFriend] delete success`);

      return success(res, "FRIEND_DELETED", "친구 삭제 완료");

    } catch (error) {
      console.error(`[deleteFriend ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "Database delete failed", 500);
    }
  }
};

module.exports = friendController;