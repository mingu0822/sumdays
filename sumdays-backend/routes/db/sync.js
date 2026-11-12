const express = require('express');
const router = express.Router();
const syncController = require('../../controllers/syncController');

const multer = require('multer');
const path = require('path');
const fs = require('fs');


// 데이터 저장시 multpart로 사진이 들어오므로 임시저장하고 controller로 넘어감
// 임시 사진 저장 폴더
const tempDir = path.join(process.cwd(), 'storage', 'temp', 'sync');

if (!fs.existsSync(tempDir)) {
  fs.mkdirSync(tempDir, { recursive: true });
}


// multer 설정
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, tempDir);
  },

  filename: (req, file, cb) => {
    cb(null, file.originalname);
  }
});

const upload = multer({
  storage: storage
});


// Local → Server
router.post(
  '/',
  upload.array('photos'),
  syncController.syncData
);


// Server → Local
router.get(
  '/',
  syncController.fetchServerData
);

module.exports = router; 