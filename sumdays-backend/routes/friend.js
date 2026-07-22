const express = require('express');
const router = express.Router();
const friendController = require('../controllers/friendController');


// 1. 친구 요청 및 취소
// POST /api/friend/request (Body: { receiverId })
router.post('/request', friendController.requestFriend);

// DELETE /api/friend/request/cancel (Body: { receiverId })때
router.delete('/request/cancel', friendController.cancelRequest);


// 2. 받은 요청 처리 (수락/거절)
// PATCH /api/friend/request (Body: { requesterId : 3, action: 'ACCEPT' | 'REJECT' })
router.patch('/request', friendController.handleRequest);


// 3. 목록 조회
// GET /api/friend/requests
router.get('/requests', friendController.getPendingRequests);

// GET /api/friend/friends (내 전체 친구 목록 조회)
router.get('/friends', friendController.getMyFriends);


// 4. 친구 삭제
// DELETE /api/friend/friends/:friendId
router.delete('/friends/:friendId', friendController.deleteFriend);

// 5. 친구 일기 
// GET /api/friend/friends/:friendId/diaries/dates
router.get(
  '/friends/:friendId/diaries/dates',
  friendController.getFriendDiaryDates
);

// GET /api/friend/friends/:friendId/diaries?yearMonth=2026-06
router.get(
  '/friends/:friendId/diaries',
  friendController.getFriendMonthlyDiaries
);


module.exports = router;