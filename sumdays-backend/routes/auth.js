// routes/auth.js
const express = require('express');
const router = express.Router();
const verifyToken = require('../middlewares/authMiddleware');

// 로그인 / 회원가입 로직이 있는 컨트롤러
const authController = require('../controllers/authController');

// 로그인 엔드포인트
router.post('/login', authController.login);

// 회원가입 엔드포인트
router.post('/signup', authController.signup);


// 비밀번호, 닉네임 변경은 토큰 필요 
router.use(verifyToken);
router.get('/me', authController.getMe);
router.put('/password', authController.changePassword);
router.put('/nickname', authController.changeNickname);
router.post('/profile-image', authController.updateProfileImage);

module.exports = router;
