const { pool } = require('../db/db');
const moment = require('moment-timezone');
const { success, fail } = require('../utils/response');

async function getFriendInfo(friendId) {
  const [friendRows] = await pool.query(`
    SELECT u.id, u.nickname, u.profile_image_url, ui.streak, ui.count_weekly_summaries,
           ui.count_diaries, u.created_at, ui.last_diary_update_date
    FROM users u
    JOIN user_info ui ON u.id = ui.user_id
    WHERE u.id = ?
  `, [friendId]);

  if (friendRows.length === 0) return null;

  const friend = friendRows[0];

  const today = moment().tz('Asia/Seoul').format('YYYY-MM-DD');
  const yesterday = moment().tz('Asia/Seoul').subtract(1, 'days').format('YYYY-MM-DD');

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
}
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
        'SELECT id, nickname, profile_image_url FROM users WHERE email = ?', 
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

            const friendInfo = await getFriendInfo(receiverId);
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
      
      // 🌟 [수정] 일반 요청이지만 클라의 편의를 위해 FriendInfo 규격으로 데이터를 조립합니다.
      console.log(`[requestFriend success0]`);
      const friendInfo = {
        id: users[0].id,                
        nickname: users[0].nickname,     
        profileImageUrl: users[0].profile_image_url, 
      };
      console.log(`[requestFriend success]`, friendInfo);
      return success(res, "REQUEST_SENT", "친구 요청 완료", friendInfo, 201);

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

        const friendInfo = await getFriendInfo(requesterId);
        return success(res, "REQUEST_ACCEPTED", "친구 수락 완료", friendInfo);
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
          streak: finalStreak,
          countWeeklySummaries: friend.count_weekly_summaries,
          createdAt: moment(friend.created_at).format('YYYY-MM-DD'),
          countDiaries: friend.count_diaries,
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
  },

  // 7. 친구 일기 날짜 목록 및 열람 권한 조회 (전체 날짜 대상)
  getFriendDiaryDates: async (req, res) => {
    const myId = req.user.userId;
    const { friendId } = req.params;

    console.log(`[getFriendDiaryDates] myId=${myId}, friendId=${friendId}`);

    try {
      // 1. 실제 친구 관계 검증
      const friendship = await getFriendship(myId, friendId);
      if (!friendship || friendship.status !== 'ACCEPTED') {
        return fail(res, "FORBIDDEN", "친구 관계가 아닙니다.", 403);
      }

      // 2. 해당 친구의 모든 일기 날짜 및 공개 여부 조회
      const [diaries] = await pool.query(
        `SELECT date, is_allowed 
         FROM daily_entry 
         WHERE user_id = ?`,
        [friendId]
      );

      // 3. 클라이언트 대응 객체 생성: {"YYYY-MM-DD": true/false}
      // 해당 일에 일기를 작성했고, 나에게 열람 권한(is_public)이 있다면 true, 아니면 false
      const dateMap = {};
      diaries.forEach(diary => {
        const dateStr = moment(diary.date).format('YYYY-MM-DD');
        dateMap[dateStr] = Boolean(diary.is_allowed);
      });

      return success(res, "FRIEND_DIARY_DATES_FETCHED", "친구 일기 날짜 목록 조회 성공", { dateMap });

    } catch (error) {
      console.error(`[getFriendDiaryDates ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "친구 일기 날짜 조회 실패", 500);
    }
  },

  // 8. 친구 특정 달(한 달 기준) 상세 일기 가져오기
  getFriendMonthlyDiaries: async (req, res) => {
    const myId = req.user.userId;
    const { friendId } = req.params;
    const { yearMonth } = req.query; // 예: "2026-07"

    console.log(`[getFriendMonthlyDiaries] myId=${myId}, friendId=${friendId}, yearMonth=${yearMonth}`);

    if (!yearMonth) {
      return fail(res, "INVALID_PARAM", "yearMonth 쿼리 파라미터가 필요합니다.", 400);
    }

    try {
      // 1. 친구 관계 검증
      const friendship = await getFriendship(myId, friendId);
      if (!friendship || friendship.status !== 'ACCEPTED') {
        return fail(res, "FORBIDDEN", "친구 관계가 아닙니다.", 403);
      }

      // 2. 해당 달("YYYY-MM")의 일기 중 '열람 허용(is_allowed = 1)'된 일기만 조회
      // 🌟 [수정] 테이블명(daily_entry), 공개여부(is_allowed = 1), 별칭 매핑 적용
      const startDate = `${yearMonth}-01`;
      const endDate = moment(startDate).endOf('month').format('YYYY-MM-DD');

      const [diaries] = await pool.query(
        `SELECT date, diary, keywords, aiComment, 
                emotionScore, emotionIcon, 
                themeIcon, photoUrls, is_allowed
         FROM daily_entry 
         WHERE user_id = ? 
           AND date BETWEEN ? AND ? 
           AND is_allowed = 1
         ORDER BY date ASC`,
        [friendId, startDate, endDate]
      );

      // 3. 날짜 포맷팅 정돈 ("YYYY-MM-DD")
      const formattedDiaries = diaries.map(item => ({
        ...item,
        date: moment(item.date).format('YYYY-MM-DD')
      }));

      return success(res, "FRIEND_MONTHLY_DIARIES_FETCHED", "친구 월별 일기 조회 성공", { diaries: formattedDiaries });

    } catch (error) {
      console.error(`[getFriendMonthlyDiaries ERROR]`, error);
      return fail(res, "INTERNAL_SERVER_ERROR", "친구 월별 일기 조회 실패", 500);
    }
  },
};

module.exports = friendController;