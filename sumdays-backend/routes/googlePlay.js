const express = require('express');
const controller = require('../controllers/googlePlayRtdnController');

const router = express.Router();
router.post('/rtdn', controller.receive);   // 구글에서 결제 상태 변경 정보 확인

module.exports = router;
