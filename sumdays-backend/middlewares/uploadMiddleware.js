const multer = require('multer');
const path = require('path');
const fs = require('fs');

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        const uploadPath = 'storage/profile/';
        if (!fs.existsSync(uploadPath)) {
            fs.mkdirSync(uploadPath, { recursive: true });
        }
        cb(null, uploadPath);
    },
    filename: (req, file, cb) => {
        const ext = path.extname(file.originalname);
        // req.user.userId를 쓰려면 이 미들웨어 앞에 authMiddleware가 먼저 실행되어야 해!
        cb(null, `profile_${req.user.userId}_${Date.now()}${ext}`);
    }
});

module.exports = multer({ storage: storage });

