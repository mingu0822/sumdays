const db = require('../db/db');

// Helper Functions 1 : 두 유저 사이의 관계를 찾아주는 헬퍼 함수
async function getFriendship(u1, u2) {
  const [rows] = await db.query(
    'SELECT * FROM friendship WHERE (requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?)',
    [u1, u2, u2, u1]
  );
  return rows.length > 0 ? rows[0] : null;
}


/* -------------------------------------------------------------------------- */
/* 1️⃣ FriendController Object: 도메인 중심 설계                            */
/* -------------------------------------------------------------------------- */

const friendController = {

  // 🤝 친구 요청하기 (POST /request)
  requestFriend: async (req, res) => {
    const requesterId = req.user.userId;
    const { receiverId } = req.body;

    if (requesterId === parseInt(receiverId)) {
      return res.status(400).json({ message: "자기 자신에게는 베팅(요청)할 수 없습니다." });
    }

    try {
      // 헬퍼 함수로 기존 관계 체크
      const existing = await getFriendship(requesterId, receiverId);
      if (existing) {
        const msg = existing.status === 'PENDING' ? "이미 대기 중인 요청이 있습니다." : "이미 친구 상태입니다.";
        return res.status(409).json({ message: msg });
      }

      await db.query(
        'INSERT INTO friendship (requester_id, receiver_id, status) VALUES (?, ?, "PENDING")',
        [requesterId, receiverId]
      );
      res.status(201).json({ message: "친구 요청 완료" });
    } catch (error) {
      res.status(500).json({ error: 'Database insert failed' });
    }
  },

  // 🚫 친구 요청 취소 (DELETE /request/cancel)
  cancelRequest: async (req, res) => {
    const requesterId = req.user.userId;
    const { receiverId } = req.body;

    try {
      const result = await db.query(
        'DELETE FROM friendship WHERE requester_id = ? AND receiver_id = ? AND status = "PENDING"',
        [requesterId, receiverId]
      );
      if (result[0].affectedRows === 0) return res.status(404).json({ message: "취소할 요청이 없습니다." });
      res.status(200).json({ message: "요청 취소 완료" });
    } catch (error) {
      res.status(500).json({ error: 'Database delete failed' });
    }
  },

  // ✅ 요청 수락/거절 (PATCH /request/:id)
  handleRequest: async (req, res) => {
    const myId = req.user.userId;
    const requestId = req.params.id;
    const { action } = req.body; // 'ACCEPT' or 'REJECT'

    try {
      // 1. 요청 존재 및 수신자 본인 확인
      const [rows] = await db.query('SELECT * FROM friendship WHERE id = ?', [requestId]);
      if (rows.length === 0 || rows[0].receiver_id !== myId) {
        return res.status(403).json({ message: "유효하지 않은 요청입니다." });
      }

      if (action === 'ACCEPT') {
        await db.query('UPDATE friendship SET status = "ACCEPTED" WHERE id = ?', [requestId]);
        res.status(200).json({ message: "친구 수락 완료" });
      } else {
        await db.query('DELETE FROM friendship WHERE id = ?', [requestId]);
        res.status(200).json({ message: "친구 요청 거절 완료" });
      }
    } catch (error) {
      res.status(500).json({ error: 'Database update failed' });
    }
  },

  // 🔍 요청 목록 조회 (GET /requests)
  getPendingRequests: async (req, res) => {
    const myId = req.user.userId;
    const { type } = req.query; // 'received' or 'sent'

    try {
      const isReceived = type === 'received';
      const sql = isReceived 
        ? 'SELECT f.id, u.nickname FROM friendship f JOIN users u ON f.requester_id = u.id WHERE f.receiver_id = ? AND f.status = "PENDING"'
        : 'SELECT f.id, u.nickname FROM friendship f JOIN users u ON f.receiver_id = u.id WHERE f.requester_id = ? AND f.status = "PENDING"';
      
      const [requests] = await db.query(sql, [myId]);
      res.status(200).json(requests);
    } catch (error) {
      res.status(500).json({ error: 'Database select failed' });
    }
  },

  // 👥 내 친구 목록 조회 (GET /friends)
  getMyFriends: async (req, res) => {
    const myId = req.user.userId;

    try {
      const [friends] = await db.query(`
        SELECT u.id, u.nickname 
        FROM friendship f
        JOIN users u ON (f.requester_id = u.id OR f.receiver_id = u.id)
        WHERE (f.requester_id = ? OR f.receiver_id = ?) 
          AND f.status = "ACCEPTED" AND u.id != ?`, 
        [myId, myId, myId]
      );
      res.status(200).json(friends);
    } catch (error) {
      res.status(500).json({ error: 'Database select failed' });
    }
  },

  // 🗑️ 친구 삭제 (DELETE /friends/:friendId)
  deleteFriend: async (req, res) => {
    const myId = req.user.userId;
    const { friendId } = req.params;

    try {
      await db.query(
        'DELETE FROM friendship WHERE ((requester_id = ? AND receiver_id = ?) OR (requester_id = ? AND receiver_id = ?)) AND status = "ACCEPTED"',
        [myId, friendId, friendId, myId]
      );
      res.status(200).json({ message: "친구 삭제 완료" });
    } catch (error) {
      res.status(500).json({ error: 'Database delete failed' });
    }
  }
};

module.exports = friendController;