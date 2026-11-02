const express = require('express');
const router = express.Router();
const verifyToken = require('../middlewares/authMiddleware');
const requireLogin = require('../middlewares/requireLogin');


// 세부 라우터 import
const authRoutes = require('./auth');
const aiRoutes = require('./ai');
const dailyRoutes = require('./db/daily');
const memosRoutes = require('./db/memos');
const photosRoutes = require('./db/photos');
const syncRoutes = require('./db/sync');
const friendRoutes = require('./friend');
const missionRoutes = require('./missions');
const googlePlayRoutes = require('./googlePlay');

// 모든 요청에서 토큰이 있으면 req.user 세팅, 없으면 req.user = null
router.use(verifyToken);

router.use('/auth', authRoutes);   // /api/auth/...
router.use('/ai', aiRoutes);       // /api/ai/...

router.use('/db', requireLogin);
router.use('/db/sync', syncRoutes); 
router.use('/db/daily', dailyRoutes);        
router.use('/db/daily/memos', memosRoutes);       
router.use('/db/daily/photos', photosRoutes);  


router.use('/friend', requireLogin);
router.use('/friend', friendRoutes);
router.use('/stickers', requireLogin, require('./stickers'));

router.use('/missions', requireLogin);
router.use('/missions', missionRoutes);
router.use('/google-play', googlePlayRoutes);

module.exports = router;
