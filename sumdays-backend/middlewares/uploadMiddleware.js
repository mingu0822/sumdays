const multer = require('multer');
const path = require('path');
const fs = require('fs');

// 공통 디렉터리 자동 생성 및 경로 반환 헬퍼 함수
const getStorageDirectory = (req, subType) => {
  // authMiddleware를 거쳐 들어온 req.user.userId 사용 (없을 경우 fallback)
  const userId = req.user && req.user.userId ? req.user.userId : 'unknown_user';
  
  // 예: storage/profile/12/ 또는 storage/diaries/12/
  const dir = path.join('storage', subType, String(userId));

  // 사용자 ID 전용 폴더가 없으면 에러 없이 자동 생성 (recursive: true)
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  return dir;
};

// 1️⃣ [프로필 이미지 전용 Storage] -> storage/profile/{userId}/
const profileStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    const targetDir = getStorageDirectory(req, 'profile');
    cb(null, targetDir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    const userId = req.user ? req.user.userId : 'user';
    // 예: profile_12_1721453200123.jpg
    cb(null, `profile_${userId}_${Date.now()}${ext}`);
  }
});

// 2️⃣ [일기 첨부 이미지 전용 Storage] -> storage/diaries/{userId}/
const diaryStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    const targetDir = getStorageDirectory(req, 'diaries');
    cb(null, targetDir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    const userId = req.user ? req.user.userId : 'user';
    // 동시 업로드 시 파일명 중복 방지를 위한 난수 추가
    const randomSuffix = Math.round(Math.random() * 1e9);
    // 예: diary_12_1721453200123_849201.jpg
    cb(null, `diary_${userId}_${Date.now()}_${randomSuffix}${ext}`);
  }
});

module.exports = {
  uploadProfile: multer({ storage: profileStorage }),
  uploadDiary: multer({ storage: diaryStorage })
};