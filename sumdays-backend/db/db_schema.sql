-- 🧹 기존 테이블 전부 삭제 (외래키 무시)
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS user_style;
DROP TABLE IF EXISTS week_summary;
DROP TABLE IF EXISTS memo;
DROP TABLE IF EXISTS daily_entries;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS friendship;
DROP TABLE IF EXISTS user_info;
SET FOREIGN_KEY_CHECKS = 1;

-- 👤 users 테이블
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50) UNIQUE NOT NULL
  profile_image_url VARCHAR(500) NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 👤 users_info 테이블 (활동 관련)
CREATE TABLE user_info (
  user_id INT PRIMARY KEY,
  -- 일기 관련 (일기 삭제 or 생성 시 update)
  count_diaries INT NOT NULL DEFAULT 0, -- 총 일기 수 
  last_diary_update_date VARCHAR(50) NULL, -- 일기를 마지막으로 쓴
  -- 주간 통계 관련 (주간 통계 생성 시 update)
  count_weekly_summaries INT NOT NULL DEFAULT 0, -- 나무, 포도 
  -- 기타
  streak INT NOT NULL DEFAULT 0, -- 불

  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 📔 daily_entry 테이블
CREATE TABLE daily_entries (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  date VARCHAR(50) NOT NULL,
  diary TEXT,
  keywords TEXT,
  aiComment TEXT,
  emotionScore FLOAT,
  emotionIcon TEXT,
  themeIcon TEXT,
  photoUrls Text,
  UNIQUE KEY unique_user_date (user_id, date),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


CREATE TABLE friendship (
  id INT AUTO_INCREMENT PRIMARY KEY,
  requester_id INT NOT NULL,
  receiver_id INT NOT NULL,
  status ENUM('PENDING', 'ACCEPTED', 'BLOCKED') DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  -- 중복 관계 방지 (A가 B에게 두 번 신청 불가)
  UNIQUE KEY unique_relationship (requester_id, receiver_id),

  -- 외래키 설정
  FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,

  -- [최적화] 나에게 온 요청 조회를 위한 역방향 인덱스 (왼쪽 일치 원칙 대응)
  INDEX idx_receiver_lookup (receiver_id, requester_id)
);

-- 🗒 memo 테이블
CREATE TABLE memo (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  room_id INT NOT NULL,
  content TEXT,
  timestamp VARCHAR(255),
  date VARCHAR(50),
  memo_order INT,
  type VARCHAR(20),
  UNIQUE KEY unique_user_room (user_id, room_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 🎨 user_style 테이블
CREATE TABLE user_style (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  styleId INT NOT NULL,
  styleName VARCHAR(255) NOT NULL,
  styleVector JSON,
  styleExamples JSON,
  stylePrompt JSON,
  sampleDiary VARCHAR(255), 
  UNIQUE KEY unique_user_style (user_id, styleId),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 📆 week_summary 테이블
CREATE TABLE week_summary (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  startDate VARCHAR(50) NOT NULL,
  endDate VARCHAR(50),
  diaryCount INT,
  emotionAnalysis JSON,
  highlights JSON,
  insights JSON,
  summary JSON,
  UNIQUE KEY unique_user_week (user_id, startDate),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);