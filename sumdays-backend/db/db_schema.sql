-- 🧹 기존 테이블 전부 삭제 (외래키 무시)
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS google_play_rtdn_messages;
DROP TABLE IF EXISTS user_merge_passes;
DROP TABLE IF EXISTS merge_pass_purchases;
DROP TABLE IF EXISTS merge_pass_products;
DROP TABLE IF EXISTS google_play_point_purchases;
DROP TABLE IF EXISTS google_play_point_products;
DROP TABLE IF EXISTS merge_ticket_transactions;
DROP TABLE IF EXISTS user_merge_ticket_balances;
DROP TABLE IF EXISTS merge_ticket_products;
DROP TABLE IF EXISTS inventory_consumptions;
DROP TABLE IF EXISTS merge_operations;
DROP TABLE IF EXISTS daily_merge_usage;
DROP TABLE IF EXISTS user_shop_inventory;
DROP TABLE IF EXISTS user_mission_events;
DROP TABLE IF EXISTS user_mission_claims;
DROP TABLE IF EXISTS daily_ad_rewards;
DROP TABLE IF EXISTS point_transactions;
DROP TABLE IF EXISTS point_wallets;
DROP TABLE IF EXISTS shop_products;
DROP TABLE IF EXISTS mission_definitions;
DROP TABLE IF EXISTS economy_settings;
DROP TABLE IF EXISTS user_style;
DROP TABLE IF EXISTS week_summary;
DROP TABLE IF EXISTS memo;
DROP TABLE IF EXISTS daily_entry;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS friendship;
DROP TABLE IF EXISTS user_info;
SET FOREIGN_KEY_CHECKS = 1;

-- 👤 users 테이블
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  google_sub VARCHAR(255) NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50) UNIQUE NOT NULL,
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
CREATE TABLE daily_entry (
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
  is_allowed TINYINT(1) NOT NULL DEFAULT 0,
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

-- user_info 통계는 syncController에서 갱신합니다.
-- 사용하지 않는 프로시저/트리거 초안은 중첩 블록 주석 때문에 SQL로 일부 실행되므로 제거했습니다.

-- 포인트·미션·상점
CREATE TABLE mission_definitions (
  code VARCHAR(80) PRIMARY KEY,
  category ENUM('DAILY', 'WEEKLY', 'REWARD') NOT NULL,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(500) NOT NULL,
  target_count INT NOT NULL,
  reward_points INT NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE point_wallets (
  user_id INT PRIMARY KEY,
  balance BIGINT NOT NULL DEFAULT 0,
  initialization_source ENUM('DEFAULT', 'LEGACY_APP') NOT NULL DEFAULT 'DEFAULT',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_point_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE shop_products (
  product_type ENUM('THEME', 'ITEM') NOT NULL,
  item_id INT NOT NULL,
  price INT NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (product_type, item_id)
);

CREATE TABLE point_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  change_amount BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  transaction_type VARCHAR(50) NOT NULL,
  reference_id VARCHAR(120) NULL,
  idempotency_key VARCHAR(190) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_point_transaction_idempotency (idempotency_key),
  INDEX idx_point_transaction_user_created (user_id, created_at),
  CONSTRAINT fk_point_transaction_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_mission_claims (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  mission_code VARCHAR(80) NOT NULL,
  period_key VARCHAR(50) NOT NULL,
  reward_points INT NOT NULL,
  claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_mission_period (user_id, mission_code, period_key),
  CONSTRAINT fk_mission_claim_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_mission_claim_definition FOREIGN KEY (mission_code) REFERENCES mission_definitions(code)
);

CREATE TABLE user_mission_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  event_type VARCHAR(80) NOT NULL,
  event_key VARCHAR(120) NOT NULL,
  event_date DATE NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_mission_event (user_id, event_type, event_key),
  INDEX idx_user_mission_event_date (user_id, event_type, event_date),
  CONSTRAINT fk_mission_event_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE daily_ad_rewards (
  user_id INT NOT NULL,
  reward_date DATE NOT NULL,
  claim_count INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, reward_date),
  CONSTRAINT fk_daily_ad_reward_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE economy_settings (
  config_key VARCHAR(80) PRIMARY KEY,
  int_value INT NOT NULL,
  description VARCHAR(255) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_shop_inventory (
  user_id INT NOT NULL,
  product_type ENUM('THEME', 'ITEM') NOT NULL,
  item_id INT NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, product_type, item_id),
  CONSTRAINT fk_shop_inventory_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_shop_inventory_product FOREIGN KEY (product_type, item_id)
    REFERENCES shop_products(product_type, item_id)
);

CREATE TABLE inventory_consumptions (
  user_id INT NOT NULL,
  request_id VARCHAR(80) NOT NULL,
  item_ids VARCHAR(256) NOT NULL,
  error_code VARCHAR(80) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, request_id),
  CONSTRAINT fk_inventory_consumption_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 여우구슬: 매일 무료분, 포인트 횟수권, 무제한 패스를 함께 관리한다.
CREATE TABLE daily_merge_usage (
  user_id INT NOT NULL,
  usage_date DATE NOT NULL,
  used_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, usage_date),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE merge_operations (
  user_id INT NOT NULL,
  request_id VARCHAR(80) NOT NULL,
  operation_type ENUM('MERGE', 'PURCHASE') NOT NULL,
  marble_source ENUM('FREE', 'TICKET', 'PASS') NULL,
  usage_date DATE NOT NULL,
  input_hash CHAR(64) NOT NULL,
  status ENUM('PENDING', 'SUCCEEDED', 'FAILED') NOT NULL,
  error_code VARCHAR(80) NULL,
  result_json MEDIUMTEXT NULL,
  expires_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, request_id),
  INDEX idx_merge_pending (user_id, status, expires_at),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE merge_ticket_products (
  pack_id VARCHAR(40) PRIMARY KEY,
  ticket_count INT NOT NULL,
  point_price INT NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_merge_ticket_product_count CHECK (ticket_count > 0),
  CONSTRAINT chk_merge_ticket_product_price CHECK (point_price >= 0)
);

CREATE TABLE user_merge_ticket_balances (
  user_id INT PRIMARY KEY,
  balance INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_merge_ticket_balance_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_merge_ticket_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE merge_ticket_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  change_amount INT NOT NULL,
  balance_after INT NOT NULL,
  transaction_type VARCHAR(50) NOT NULL,
  reference_id VARCHAR(120) NULL,
  idempotency_key VARCHAR(190) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_merge_ticket_transaction_idempotency (idempotency_key),
  INDEX idx_merge_ticket_transaction_user_created (user_id, created_at),
  CONSTRAINT fk_merge_ticket_transaction_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Google Play 포인트 결제
CREATE TABLE google_play_point_products (
  product_id VARCHAR(80) PRIMARY KEY,
  point_amount INT NOT NULL,
  display_order INT NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_google_play_point_amount CHECK (point_amount > 0)
);

CREATE TABLE google_play_point_purchases (
  purchase_token VARCHAR(512) PRIMARY KEY,
  user_id INT NOT NULL,
  product_id VARCHAR(80) NOT NULL,
  point_amount INT NOT NULL,
  order_id VARCHAR(120) NULL,
  purchased_at DATETIME NULL,
  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  consumed_at TIMESTAMP NULL,
  INDEX idx_google_play_point_purchase_user (user_id, granted_at),
  CONSTRAINT fk_google_play_point_purchase_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_google_play_point_purchase_product FOREIGN KEY (product_id) REFERENCES google_play_point_products(product_id),
  CONSTRAINT chk_google_play_point_purchase_amount CHECK (point_amount > 0)
);

-- Google Play 선불 1개월 합치기 패스
CREATE TABLE merge_pass_products (
  product_id VARCHAR(80) PRIMARY KEY,
  display_name VARCHAR(100) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE merge_pass_purchases (
  purchase_token VARCHAR(512) PRIMARY KEY,
  user_id INT NOT NULL,
  product_id VARCHAR(80) NOT NULL,
  expires_at DATETIME NOT NULL,
  acknowledged_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_merge_pass_purchase_user (user_id, created_at),
  CONSTRAINT fk_merge_pass_purchase_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_merge_pass_purchase_product FOREIGN KEY (product_id) REFERENCES merge_pass_products(product_id)
);

CREATE TABLE user_merge_passes (
  user_id INT PRIMARY KEY,
  product_id VARCHAR(80) NOT NULL,
  purchase_token VARCHAR(512) NOT NULL,
  expires_at DATETIME NOT NULL,
  status ENUM('ACTIVE', 'EXPIRED', 'REVOKED') NOT NULL DEFAULT 'ACTIVE',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_merge_pass_token (purchase_token),
  INDEX idx_user_merge_pass_status_expiry (status, expires_at),
  CONSTRAINT fk_user_merge_pass_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_merge_pass_product FOREIGN KEY (product_id) REFERENCES merge_pass_products(product_id),
  CONSTRAINT fk_user_merge_pass_purchase FOREIGN KEY (purchase_token) REFERENCES merge_pass_purchases(purchase_token)
);

CREATE TABLE google_play_rtdn_messages (
  message_id VARCHAR(190) PRIMARY KEY,
  notification_type VARCHAR(80) NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO mission_definitions
  (code, category, title, description, target_count, reward_points, display_order)
VALUES
  ('DAILY_WRITE_MEMO', 'DAILY', '메모 3회 작성하기', '오늘 작성한 메모가 진행도에 반영돼요', 3, 100, 0),
  ('DAILY_COMPLETE_DIARY', 'DAILY', '오늘의 일기 완성하기', '메모를 모아 오늘의 일기를 완성해 보세요', 1, 200, 1),
  ('DAILY_VIEW_FRIEND_DIARY', 'DAILY', '친구 일기 구경하기', '친구의 공개된 일기를 한 번 읽어 보세요', 1, 50, 2),
  ('WEEKLY_DIARY_STREAK', 'WEEKLY', '3일 연속 일기 작성하기', '이번 주에 연속으로 일기를 완성해 보세요', 3, 300, 0),
  ('WEEKLY_WRITE_DIARY', 'WEEKLY', '이번 주 일기 5회 작성하기', '이번 주에 완성한 일기 수를 확인해요', 5, 500, 1),
  ('WEEKLY_SUMMARY_REWARD', 'REWARD', '주간 통계 확인하기', '새로운 주간 통계를 얻어 확인해 보세요', 1, 300, 0);

INSERT INTO economy_settings (config_key, int_value, description)
VALUES
  ('AD_REWARD_POINTS', 200, '광고 1회 시청 보상'),
  ('AD_DAILY_LIMIT', 3, '하루 광고 보상 최대 횟수'),
  ('DEFAULT_POINT_BALANCE', 9000, '신규 설치 포인트 지갑 최초 생성 잔액'),
  ('DAILY_MERGE_FREE_LIMIT', 5, '한국 시간 기준 하루 기본 합치기 횟수');

INSERT INTO shop_products (product_type, item_id, price, is_active)
VALUES
  ('THEME', 2, 280, 1),
  ('ITEM', 1, 300, 1),
  ('ITEM', 2, 200, 1),
  ('ITEM', 3, 200, 1),
  ('ITEM', 101, 400, 1),
  ('ITEM', 102, 200, 1),
  ('ITEM', 201, 200, 1),
  ('ITEM', 301, 200, 1),
  ('ITEM', 401, 200, 1),
  ('ITEM', 402, 200, 1),
  ('ITEM', 404, 200, 1),
  ('ITEM', 405, 200, 1);

INSERT INTO merge_ticket_products (pack_id, ticket_count, point_price, is_active)
VALUES
  ('MERGE_TICKET_1', 1, 300, 1),
  ('MERGE_TICKET_5', 5, 1350, 1),
  ('MERGE_TICKET_10', 10, 2550, 1),
  ('MERGE_TICKET_20', 20, 4800, 1);

INSERT INTO google_play_point_products (product_id, point_amount, display_order, is_active)
VALUES
  ('sumdays_points_1000', 1000, 0, 1),
  ('sumdays_points_3300', 3300, 1, 1),
  ('sumdays_points_5800', 5800, 2, 1),
  ('sumdays_points_12000', 12000, 3, 1);

INSERT INTO merge_pass_products (product_id, display_name, is_active)
VALUES ('sumdays_merge_pass_1m', '합치기 무제한 패스 1개월', 1);
