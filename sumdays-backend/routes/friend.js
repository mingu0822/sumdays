const express = require('express');
const router = express.Router();
const friendController = require('../../controllers/friendController');


// ✅ 1️⃣ 특정 날짜 일기 조회 (전체, include, exclude 모두 지원)
// router.get('/:date', friendController.getDailyEntry);

module.exports = router;
