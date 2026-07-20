const {pool} = require('../db/db');
const path = require("path");
const process = require("process");
const fs = require("fs");


// 1. local to server
exports.syncData = async (req, res) => {
  let connection;
  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    console.log(JSON.stringify(req.body, null, 2));
    const {deleted, edited } = req.body;
    const userId = req.user.userId

    let diaryChanged = false;
    let weekSummaryChanged = false;


     /** ------------------------------
     * 🧩 공통 INSERT or UPDATE 함수
     * ------------------------------ */
    const upsert = async (table, data, columns) => {
      if (!Array.isArray(data) || data.length === 0) return false;

      const fields = columns.join(', ');
      const placeholders = columns.map(() => '?').join(', ');
      const updates = columns.map(col => `${col}=VALUES(${col})`).join(', ');

      const sql = `
        INSERT INTO ${table} (user_id, ${fields})
        VALUES ${data.map(() => `(?, ${placeholders})`).join(', ')}
        ON DUPLICATE KEY UPDATE ${updates};
      `;

      const values = data.flatMap(item => [
        userId,
        ...columns.map(c => {
          const value = item[c];
          // JSON 컬럼이면 stringify
          if (typeof value === 'object' && value !== null) {
            return JSON.stringify(value);
          }
          return value;
        })
      ]);
      
      const [result] = await connection.query(sql, values);
      const hasCreated = result.affectedRows > 0 && result.affectedRows !== (data.length * 2);
      console.log(`📊 ${table} - 보낸 개수: ${data.length}, affectedRows: ${result.affectedRows} -> 신규 생성 여부: ${hasCreated}`);
      return hasCreated;
    };


    // -------------------------------------------------------------------------------
    
    // 1. 삭제 data
    if (deleted) {
        const deleteIfExists = async (table, keyField, ids) => {
          if (!Array.isArray(ids) || ids.length === 0) return false;
          const placeholders = ids.map(() => '?').join(',');
          const sql = `DELETE FROM ${table} WHERE ${keyField} IN (${placeholders}) AND user_id = ?`;
          const [result] = await connection.query(sql, [...ids, userId]);
          console.log(`✅ Deleted from ${table}: ${ids.length} rows`);
          return result.affectedRows > 0;
        };

    // 🧱 각 테이블별 삭제 반영
        if (deleted.memo) {
            await deleteIfExists('memo', 'room_id', deleted.memo);
        }
        if (deleted.dailyEntry) {
            const actuallyDeleted = await deleteIfExists('daily_entry', 'date', deleted.dailyEntry);
            if (actuallyDeleted) {
              diaryChanged = true;
            }
        }
        if (deleted.userStyle) {
            await deleteIfExists('user_style', 'styleId', deleted.userStyle);
        }
        if (deleted.weekSummary) {
            const actuallyDeleted = await deleteIfExists('week_summary', 'startDate', deleted.weekSummary);
            if (actuallyDeleted) {
              weekSummaryChanged = true;
            }
        }
    }
    // 2. 추가, 수정 data 
    if (edited) {
      if (edited.memo) {
        await upsert(
          'memo',
          edited.memo,
          ['room_id', 'content', 'timestamp', 'date', 'memo_order', 'type']
        );

      }

      if (edited.dailyEntry) {
        const actuallyCreated = await upsert(
          'daily_entry',
          edited.dailyEntry,
          ['date', 'diary', 'keywords', 'aiComment', 'emotionScore', 'emotionIcon', 'themeIcon', 'photoUrls', 'is_allowed']
        );
        if (actuallyCreated) {
          diaryChanged = true;
        }
      }

      if (edited.weekSummary) {
        const actuallyCreated = await upsert(
          'week_summary',
          edited.weekSummary,
          ['startDate', 'endDate', 'diaryCount', 'emotionAnalysis', 'highlights', 'insights', 'summary']
        );
        if (actuallyCreated) {
              weekSummaryChanged = true;
        }
      }

      if (edited.userStyle) {
        await upsert(
          'user_style',
          edited.userStyle,
          ['styleId', 'styleName', 'styleVector', 'styleExamples', 'stylePrompt', 'sampleDiary']
        );
      }
    }

    await connection.commit();
    connection.release();
    connection = null;
  

    res.json({
      status: 'success',
      message: 'Sync completed successfully.',
    });

    if (diaryChanged) {
      setImmediate(async () => {
        try {
          console.log(`⚡ [백그라운드] 일기 변경 감지 -> user_info (일기수, 마지막일자, 스트릭) 갱신 시작 (user_id=${userId})`);

          await pool.query(`INSERT IGNORE INTO user_info (user_id) VALUES (?)`, [userId]);

          await pool.query(`
            UPDATE user_info ui
            JOIN (
              SELECT 
                ? AS user_id,
                
                -- ① 총 일기 개수
                (SELECT COUNT(*) FROM daily_entry WHERE user_id = ?) AS calc_count,
                
                -- ② 가장 최근 일기 작성 날짜
                (SELECT DATE_FORMAT(MAX(STR_TO_DATE(date, '%Y-%m-%d')), '%Y-%m-%d') FROM daily_entry WHERE user_id = ?) AS calc_last_date,
                
                -- ④ 연속 작성일 (Streak) 연산 (형의 프로시저 알고리즘)
                COALESCE((
                  WITH converted_diaries AS (
                    SELECT DISTINCT STR_TO_DATE(date, '%Y-%m-%d') AS diary_date
                    FROM daily_entry
                    WHERE user_id = ?
                      AND STR_TO_DATE(date, '%Y-%m-%d') <= DATE(CONVERT_TZ(NOW(), '+00:00', '+09:00'))
                  ),
                  anchor_info AS (
                    SELECT MAX(diary_date) AS anchor_date
                    FROM converted_diaries
                  ),
                  numbered_dates AS (
                    SELECT 
                      cd.diary_date,
                      ai.anchor_date,
                      ROW_NUMBER() OVER (ORDER BY cd.diary_date DESC) AS row_num
                    FROM converted_diaries cd
                    CROSS JOIN anchor_info ai
                    WHERE ai.anchor_date IS NOT NULL 
                      AND ai.anchor_date >= DATE_SUB(DATE(CONVERT_TZ(NOW(), '+00:00', '+09:00')), INTERVAL 1 DAY)
                  )
                  SELECT COUNT(*)
                  FROM numbered_dates
                  WHERE DATEDIFF(anchor_date, diary_date) = row_num - 1
                ), 0) AS calc_streak
            ) AS stats ON ui.user_id = stats.user_id
            SET 
              ui.count_diaries = stats.calc_count,
              ui.last_diary_update_date = stats.calc_last_date,
              ui.streak = stats.calc_streak;
          `, [userId, userId, userId, userId]);

          console.log(`✅ [백그라운드] 일기 관련 user_info 갱신 완료 (user_id=${userId})`);
        } catch (bgErr) {
          console.error(`💥 [백그라운드] 일기 통계 갱신 에러 (user_id=${userId}):`, bgErr);
        }
      });
    }

    // 2️⃣ [주간 통계 생성/삭제 발생 시] -> 3.주간 요약 개수만 갱신
    if (weekSummaryChanged) {
      setImmediate(async () => {
        try {
          console.log(`⚡ [백그라운드] 주간통계 변경 감지 -> user_info (주간 요약 개수) 갱신 시작 (user_id=${userId})`);

          await pool.query(`INSERT IGNORE INTO user_info (user_id) VALUES (?)`, [userId]);

          await pool.query(`
            UPDATE user_info
            SET count_weekly_summaries = (SELECT COUNT(*) FROM week_summary WHERE user_id = ?)
            WHERE user_id = ?;
          `, [userId, userId]);

          console.log(`✅ [백그라운드] 주간 요약 개수 갱신 완료 (user_id=${userId})`);
        } catch (bgErr) {
          console.error(`💥 [백그라운드] 주간 요약 통계 갱신 에러 (user_id=${userId}):`, bgErr);
        }
      });
    }

  } catch (error) {
    if (connection) await connection.rollback();
    console.error('[syncController] Error:', error);
    res.status(500).json({ status: 'error', message: error.message });
  } finally {
    if (connection) {
      connection.release();
    }
  }
};


function safeParse(str, fallback) {
  try {
    return str ? JSON.parse(str) : fallback;
  } catch (e) {
    return fallback;
  }
}

// 2. server to local (아직 코드 미완성)
exports.fetchServerData = async (req, res) => {
  try {
    const userId = req.user.userId

    const [memo] = await pool.query(
      `SELECT * FROM memo WHERE user_id=?`, [userId]
    );

    const [dailyEntry] = await pool.query(
      `SELECT * FROM daily_entry WHERE user_id=?`, [userId]
    );

    const [weekSummary] = await pool.query(
      `SELECT * FROM week_summary WHERE user_id=?`, [userId]
    );

    const [userStyle] = await pool.query(
      `SELECT * FROM user_style WHERE user_id=?`, [userId]
    );
    

    res.json({
      memo,
      dailyEntry,
      weekSummary,
      userStyle
    });

  } catch (e) {
    res.status(500).json({ status: "error", message: e.message });
  }
};


