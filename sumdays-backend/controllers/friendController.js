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
    const { type } = req.query;

    console.log(`[getPendingRequests] myId=${myId}, type=${type}`);

    try {
      const isReceived = type === 'received';

      const sql = isReceived 
        ? 'SELECT f.id, u.nickname FROM friendship f JOIN users u ON f.requester_id = u.id WHERE f.receiver_id = ? AND f.status = "PENDING"'
        : 'SELECT f.id, u.nickname FROM friendship f JOIN users u ON f.receiver_id = u.id WHERE f.requester_id = ? AND f.status = "PENDING"';

      const [requests] = await pool.query(sql, [myId]);

      console.log(`[getPendingRequests] result:`, requests);

      res.status(200).json(requests);

    } catch (error) {
      console.error(`[getPendingRequests ERROR]`, error);
      res.status(500).json({ error: 'Database select failed' });
    }
  },

  getMyFriends: async (req, res) => {
    const myId = req.user.userId;

    console.log(`[getMyFriends] myId=${myId}`);

    try {
      const [friends] = await pool.query(`
        SELECT u.id, u.nickname, u.profile_image_url, u.created_at,
        ui.count_diaries, ui.streak, ui.count_weekly_summaries, ui.last_diary_update_date,
        FROM friendship f
        JOIN users u ON (f.requester_id = u.id OR f.receiver_id = u.id)
        WHERE (f.requester_id = ? OR f.receiver_id = ?) 
          AND f.status = "ACCEPTED" AND u.id != ?`, 
        [myId, myId, myId]
      );

      // 오늘 update 안된 친구들 
      const staleFriendIds = friends
      .filter((friend) => {
        if (!friend.user_info_updated_at) return true;

        const updatedAt = new Date(friend.user_info_updated_at);
        const updatedAtKST = new Date(updatedAt.getTime() + 9 * 60 * 60 * 1000)
          .toISOString()
          .slice(0, 10);

        return updatedAtKST !== today;
      })
      .map((friend) => friend.id);
      

      

      console.log(`[getMyFriends] result:`, friends);

      res.status(200).json(friends);

    } catch (error) {
      console.error(`[getMyFriends ERROR]`, error);
      res.status(500).json({ error: 'Database select failed' });
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