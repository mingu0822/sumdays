const { pool } = require('../db/db');

async function getFriendship(u1, u2) {
  console.log(`[getFriendship] u1=${u1}, u2=${u2}`);

  const [rows] = await pool.query(
    'SELECT * FROM friendship WHERE (requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?)',
    [u1, u2, u2, u1]
  );

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
        return res.status(404).json({ message: "존재하지 않는 사용자입니다." });
      }

      const receiverId = users[0].id;

      if (requesterId === receiverId) {
        console.log(`[requestFriend] self request blocked`);
        return res.status(400).json({ message: "자기 자신에게는 요청할 수 없습니다." });
      }

      const existing = await getFriendship(requesterId, receiverId);

      if (existing) {
        console.log(`[requestFriend] existing relationship:`, existing);

        const { status, requester_id } = existing;

        if (status === 'ACCEPTED') {
          console.log(`[requestFriend] already friends`);
          return res.status(409).json({ message: "이미 친구 상태입니다." });
        }

        if (status === 'PENDING') {
          if (requester_id === requesterId) {
            console.log(`[requestFriend] waiting for acceptance`);
            return res.status(409).json({ message: "상대방의 수락을 기다리는 중입니다." });
          } else {
            console.log(`[requestFriend] auto accept triggered`);
            await pool.query(
              'UPDATE friendship SET status = "ACCEPTED" WHERE id = ?',
              [existing.id]
            );
            return res.status(200).json({ message: "상대방의 요청이 있어 즉시 친구가 되었습니다!" });
          }
        }

        if (status === 'REJECTED') {
          console.log(`[requestFriend] rejected state`);
          return res.status(409).json({ message: "이미  상태입니다." });
        }
      }

      console.log(`[requestFriend] inserting new request`);
      await pool.query(
        'INSERT INTO friendship (requester_id, receiver_id, status) VALUES (?, ?, "PENDING")',
        [requesterId, receiverId]
      );

      res.status(201).json({ message: "친구 요청 완료" });

    } catch (error) {
      console.error(`[requestFriend ERROR]`, error);
      res.status(500).json({ error: 'Database process failed' });
    }
  },

  cancelRequest: async (req, res) => {
    const requesterId = req.user.userId;
    const { receiverId } = req.body;

    console.log(`[cancelRequest] requester=${requesterId}, receiver=${receiverId}`);

    try {
      const result = await pool.query(
        'DELETE FROM friendship WHERE requester_id = ? AND receiver_id = ? AND status = "PENDING"',
        [requesterId, receiverId]
      );

      console.log(`[cancelRequest] affectedRows=${result[0].affectedRows}`);

      if (result[0].affectedRows === 0) {
        return res.status(404).json({ message: "취소할 요청이 없습니다." });
      }

      res.status(200).json({ message: "요청 취소 완료" });

    } catch (error) {
      console.error(`[cancelRequest ERROR]`, error);
      res.status(500).json({ error: 'Database delete failed' });
    }
  },

  handleRequest: async (req, res) => {
    const myId = req.user.userId;
    const requestId = req.params.id;
    const { action } = req.body;

    console.log(`[handleRequest] myId=${myId}, requestId=${requestId}, action=${action}`);

    try {
      const [rows] = await pool.query('SELECT * FROM friendship WHERE id = ?', [requestId]);

      console.log(`[handleRequest] request data:`, rows);

      if (rows.length === 0 || rows[0].receiver_id !== myId) {
        console.log(`[handleRequest] invalid request`);
        return res.status(403).json({ message: "유효하지 않은 요청입니다." });
      }

      if (action === 'ACCEPT') {
        console.log(`[handleRequest] accepting request`);
        await pool.query('UPDATE friendship SET status = "ACCEPTED" WHERE id = ?', [requestId]);
        res.status(200).json({ message: "친구 수락 완료" });
      } else {
        console.log(`[handleRequest] rejecting request`);
        await pool.query('DELETE FROM friendship WHERE id = ?', [requestId]);
        res.status(200).json({ message: "친구 요청 거절 완료" });
      }

    } catch (error) {
      console.error(`[handleRequest ERROR]`, error);
      res.status(500).json({ error: 'Database update failed' });
    }
  },

  getPendingRequests: async (req, res) => {
    const myId = req.user.userId;

    console.log(`[getPendingRequests] Fetching all pending for myId=${myId}`);

    try {
        // 받은 요청과 보낸 요청을 동시에 조회 (성능 최적화)
        const [receivedRows, sentRows] = await Promise.all([
            pool.query(
                `SELECT f.id as requestId, u.nickname, u.profile_image_url 
                 FROM friendship f 
                 JOIN users u ON f.requester_id = u.id 
                 WHERE f.receiver_id = ? AND f.status = "PENDING"`, 
                [myId]
            ),
            pool.query(
                `SELECT f.id as requestId, u.nickname, u.profile_image_url 
                 FROM friendship f 
                 JOIN users u ON f.receiver_id = u.id 
                 WHERE f.requester_id = ? AND f.status = "PENDING"`, 
                [myId]
            )
        ]);

        // 결과 구조화
        const response = {
            success: true,
            received: receivedRows[0], // 나에게 친구 신청한 사람들
            sent: sentRows[0]          // 내가 신청하고 수락 대기 중인 사람들
        };

        console.log(`[getPendingRequests] Found received: ${response.received.length}, sent: ${response.sent.length}`);

        res.status(200).json(response);

    } catch (error) {
        console.error(`[getPendingRequests ERROR]`, error);
        res.status(500).json({ success: false, error: '대기 중인 요청 조회 실패' });
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
          // 스트라이크 판정: 마지막 업데이트가 오늘/어제가 아니면 0
          const lastUpdate = friend.last_diary_update_date 
              ? moment(friend.last_diary_update_date).format('YYYY-MM-DD') 
              : null;
          const isStreakValid = (lastUpdate === today || lastUpdate === yesterday);
          const finalStreak = isStreakValid ? friend.streak : 0;

          return {
              // 네이밍 컨벤션: Kotlin 스타일(CamelCase)로 변경
              id: friend.id,
              nickname: friend.nickname,
              profileImageUrl: friend.profile_image_url,
              // created_at -> yyyy-mm-dd 형식
              createdAt: moment(friend.created_at).format('YYYY-MM-DD'),
              countDiaries: friend.count_diaries,
              // 실시간 계산된 스트라이크
              streak: finalStreak,
              countWeeklySummaries: friend.count_weekly_summaries,
              lastDiaryUpdateDate: friend.last_diary_update_date
          };
        });

        res.status(200).json({
            success: true,
            friends: formattedFriends
        });

    } catch (error) {
        console.error('❌ [getMyFriends] Error:', error);
        res.status(500).json({ success: false, message: '친구 목록 조회 실패' });
    }
  },

  deleteFriend: async (req, res) => {
    const myId = req.user.userId;
    const { friendId } = req.params;

    console.log(`[deleteFriend] myId=${myId}, friendId=${friendId}`);

    try {
      await pool.query(
        'DELETE FROM friendship WHERE ((requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?)) AND status = "ACCEPTED"',
        [myId, friendId, friendId, myId]
      );

      console.log(`[deleteFriend] delete success`);

      res.status(200).json({ message: "친구 삭제 완료" });

    } catch (error) {
      console.error(`[deleteFriend ERROR]`, error);
      res.status(500).json({ error: 'Database delete failed' });
    }
  }
};

module.exports = friendController;