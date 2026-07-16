-- 🧹 기존 테이블 전부 삭제 (외래키 무시)
SET FOREIGN_KEY_CHECKS = 0;
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


-----------------------------
-- 트리거 및 프로시저 모음
-----------------------------

DELIMITER $$
DROP PROCEDURE IF EXISTS sync_user_diary_info $$

CREATE PROCEDURE sync_user_diary_info(IN p_user_id INT)
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE v_last_date DATE DEFAULT NULL;
    DECLARE v_anchor_date DATE DEFAULT NULL;
    DECLARE v_today DATE;
    DECLARE v_streak INT DEFAULT 0;

    /* 한국 날짜 기준 */
    SET v_today = DATE(
        CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00')
    );

    /* user_info 행이 없다면 생성 */
    INSERT IGNORE INTO user_info (user_id)
    VALUES (p_user_id);

    /* 전체 일기 개수와 가장 최근 일기 날짜 */
    SELECT
        COUNT(*),
        MAX(STR_TO_DATE(de.`date`, '%Y-%m-%d'))
    INTO
        v_count,
        v_last_date
    FROM daily_entry de
    WHERE de.user_id = p_user_id;

    /*
      오늘 이전 일기 중 가장 최근 날짜.
      미래 날짜의 일기가 있어도 현재 streak 계산에서는 제외한다.
    */
    SELECT
        MAX(STR_TO_DATE(de.`date`, '%Y-%m-%d'))
    INTO
        v_anchor_date
    FROM daily_entry de
    WHERE de.user_id = p_user_id
      AND STR_TO_DATE(de.`date`, '%Y-%m-%d') <= v_today;

    /*
      가장 최근 일기가 오늘 또는 어제인 경우에만
      최신 날짜부터 연속된 일수를 계산한다.
    */
    IF v_anchor_date IS NOT NULL
       AND v_anchor_date >= DATE_SUB(v_today, INTERVAL 1 DAY)
    THEN

        SELECT COUNT(*)
        INTO v_streak
        FROM (
            SELECT
                diary_date,
                ROW_NUMBER() OVER (
                    ORDER BY diary_date DESC
                ) AS row_num
            FROM (
                SELECT DISTINCT
                    STR_TO_DATE(de.`date`, '%Y-%m-%d') AS diary_date
                FROM daily_entry de
                WHERE de.user_id = p_user_id
                  AND STR_TO_DATE(
                        de.`date`,
                        '%Y-%m-%d'
                      ) <= v_anchor_date
            ) AS diary_dates
        ) AS numbered_dates
        WHERE DATEDIFF(v_anchor_date, diary_date)
              = row_num - 1;

    ELSE
        SET v_streak = 0;
    END IF;

    /* 계산된 세 값을 user_info에 저장 */
    UPDATE user_info
    SET
        count_diaries = v_count,
        last_diary_update_date =
            DATE_FORMAT(v_last_date, '%Y-%m-%d'),
        streak = v_streak
    WHERE user_id = p_user_id;
END $$


/* =========================================================
   2. 일기 생성 후 동기화
   ========================================================= */

DROP TRIGGER IF EXISTS trg_daily_entry_after_insert $$

CREATE TRIGGER trg_daily_entry_after_insert
AFTER INSERT ON daily_entry
FOR EACH ROW
BEGIN
    CALL sync_user_diary_info(NEW.user_id);
END $$


/* =========================================================
   3. 일기 삭제 후 동기화
   ========================================================= */

DROP TRIGGER IF EXISTS trg_daily_entry_after_delete $$

CREATE TRIGGER trg_daily_entry_after_delete
AFTER DELETE ON daily_entry
FOR EACH ROW
BEGIN
    CALL sync_user_diary_info(OLD.user_id);
END $$


DELIMITER ;