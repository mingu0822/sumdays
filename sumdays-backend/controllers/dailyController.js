const db = require('../db/db');
const path = require("path");
const process = require("process");
const fs = require("fs");
/* -------------------------------------------------------------------------- */
/*  GET /api/db/daily/:date (Total / include / exclude) */
/* -------------------------------------------------------------------------- */
exports.getDailyEntry = async (req, res) => {
  const { date } = req.params;
  const { include, exclude } = req.query;
  const userId = req.user.userId

  try {
    // 1. 기본 entries 
    const [entries] = await db.query(
      'SELECT * FROM daily_entries WHERE user_id = ? AND entry_date = ?',
      [userId,date]
    );

    if (entries.length === 0) {
      return res.status(404).json({ message: 'Daily entry not found' });
    }
    const entry = entries[0];


    // 2. Memos + Photos
    const [memos] = await db.query(
      'SELECT inner_id, memo_order, content, memo_time FROM memos WHERE daily_entry_id = ? ORDER BY memo_order ASC',
      [entry.id]
    );
    const [photos] = await db.query(
      'SELECT inner_id, photo_order, extension FROM photos WHERE daily_entry_id = ? ORDER BY photo_order ASC',
      [entry.id]
    )
    const photoData = photos.map((photo) => {    
      const filePath = path.join(
        process.cwd(),
        "uploads",
        entry.user_id.toString(),
        entry.entry_date,
        `${photo.inner_id}.${photo.extension}`
      );

      // 존재 x => null
      if (!fs.existsSync(filePath)) {
        return {
          inner_id: photo.inner_id,
          order: photo.photo_order,
          base64: null
        };
      }

      // 파일을 base64로 변환
      const imageBuffer = fs.readFileSync(filePath);
      const base64String = imageBuffer.toString("base64");
      // 변환된 데이터 반환
      return {
        inner_id: photo.inner_id,
        order: photo.photo_order,
        base64: `data:image/${photo.extension};base64,${base64String}`
      };
    });


    let data = {
      date: entry.entry_date,
      icon_name: entry.icon_name,
      diary: entry.diary,
      ai_comment: entry.ai_comment,
      memos: memos,
      photos: photoData
    };

    // include / exclude 필드 처리
    if (include) {
      const includeFields = include.split(',').map(f => f.trim());
      data = Object.fromEntries(
        Object.entries(data).filter(([key]) => includeFields.includes(key))
      );
    }
    if (exclude) {
      const excludeFields = exclude.split(',').map(f => f.trim());
      data = Object.fromEntries(
        Object.entries(data).filter(([key]) => !excludeFields.includes(key))
      );
    }

    res.status(200).json(data);
  } catch (error) {
    // console.error('❌ [getDailyEntry] Error:', error);
    res.status(500).json({ error: 'Database query failed' });
  }
};


/* -------------------------------------------------------------------------- */
/*  POST /api/db/daily — new daily entry */                                     
/* -------------------------------------------------------------------------- */
const moment = require('moment-timezone'); // 날짜 계산을 위해 권장!

exports.createDailyEntry = async (req, res) => {
    const { date } = req.body; // 유저가 선택한 일기 날짜 (YYYY-MM-DD)
    const userId = req.user.userId;
    const connection = await pool.getConnection(); // 트랜잭션을 위해 커넥션 직접 사용

    try {
        await connection.beginTransaction();

        // 1. 중복 체크
        const [exists] = await connection.query(
            'SELECT id FROM daily_entries WHERE user_id = ? AND entry_date = ?',
            [userId, date]
        );
        if (exists.length > 0) {
            connection.release();
            return res.status(409).json({ message: '해당 날짜에 이미 일기가 존재합니다.' });
        }

        // 2. 일기 삽입
        await connection.query(
            'INSERT INTO daily_entries (user_id, entry_date) VALUES (?, ?)',
            [userId, date]
        );

        // 3. 스트라이크 및 통계 업데이트 로직
        // 유저의 기존 통계 정보 가져오기
        const [userInfo] = await connection.query(
            'SELECT streak, last_diary_update_date FROM user_info WHERE user_id = ?',
            [userId]
        );
        
        let { streak, last_diary_update_date } = userInfo[0];
        const lastUpdate = last_diary_update_date ? moment(last_diary_update_date).format('YYYY-MM-DD') : null;
        
        // 시간 설정 (한국 시간 기준)
        const now = moment().tz('Asia/Seoul');
        const today = now.format('YYYY-MM-DD');
        const yesterday = now.clone().subtract(1, 'days').format('YYYY-MM-DD');
        const dayBeforeYesterday = now.clone().subtract(2, 'days').format('YYYY-MM-DD');
        const currentHour = now.hour();

        let newStreak = streak;

        // --- 스트라이크 판정 로직 시작 ---
        if (date === today) {
            // Case 1: 오늘 날짜 일기
            if (lastUpdate === yesterday) {
                newStreak += 1; // 어제 썼으면 사슬 유지
            } else if (lastUpdate !== today) {
                newStreak = 1; // 어제 안 썼으면(오늘 이미 쓴 게 아니라면) 초기화
            }
        } else if (date === yesterday) {
            // Case 2: 어제 날짜 일기 (지각 제출)
            if (currentHour < 4) { // 오전 4시 이전 자비 시간
                if (lastUpdate === dayBeforeYesterday) {
                    newStreak += 1; // 그저께 썼으면 세이프
                } else if (lastUpdate !== yesterday) {
                    newStreak = 1; // 그 외엔 초기화
                }
            }
        }
        // --- 스트라이크 판정 로직 끝 ---

        // 4. DB 업데이트 (count_diaries 증가 + 스트라이크 + 날짜 갱신)
        // last_diary_update_date는 새로 쓴 일기 날짜(date)가 더 최신일 때만 업데이트
        const shouldUpdateDate = !lastUpdate || moment(date).isAfter(lastUpdate);

        await connection.query(
            `UPDATE user_info SET 
                streak = ?, 
                count_diaries = count_diaries + 1
                ${shouldUpdateDate ? ', last_diary_update_date = ?' : ''}
             WHERE user_id = ?`,
            shouldUpdateDate ? [newStreak, date, userId] : [newStreak, userId]
        );

        await connection.commit();
        res.status(201).json({ 
            success: true, 
            message: '일기 저장 및 스트라이크 갱신 완료',
            currentStreak: newStreak 
        });

    } catch (error) {
        await connection.rollback();
        console.error('❌ Error:', error);
        res.status(500).json({ error: '서버 오류 발생' });
    } finally {
        connection.release();
    }
};

/* -------------------------------------------------------------------------- */
/*  3️⃣ PATCH /api/db/daily/:date — 일기 수정 (icon_name, diary, ai_comment)   */
/* -------------------------------------------------------------------------- */
exports.updateDailyEntry = async (req, res) => {
  const { date } = req.params;
  const { icon_name, diary, ai_comment } = req.body;
  const userId = req.user.userId

  try {
    // 1. find dailyEntry
    const [entries] = await db.query(
      'SELECT id FROM daily_entries WHERE user_id = ? AND entry_date = ?',
      [userId, date]
    );
    if (entries.length === 0) {
      return res.status(404).json({ message: 'Daily entry not found' });
    }

    // 2. make query (icon_name, diary, ai_comment)
    const updates = [];
    const values = [];

    if (icon_name !== undefined) {
      updates.push('icon_name = ?');
      values.push(icon_name);
    }
    if (diary !== undefined) {
      updates.push('diary = ?');
      values.push(diary);
    }
    if (ai_comment !== undefined) {
      updates.push('ai_comment = ?');
      values.push(ai_comment);
    }

    if (updates.length === 0) {
      return res.status(400).json({ message: 'No valid fields provided for update' });
    }

    values.push(userId, date);
    const sql = `UPDATE daily_entries SET ${updates.join(', ')} WHERE user_id = ? AND entry_date = ?`;
    await db.query(sql, values);

    res.status(200).json({ message: 'Daily entry updated successfully' });
  } catch (error) {
    // console.error('❌ [updateDailyEntry] Error:', error);
    res.status(500).json({ error: 'Database update failed' });
  }
};


/* -------------------------------------------------------------------------- */
/*  4️⃣ DELETE /api/db/daily/:date — 일기 삭제                                 */
/* -------------------------------------------------------------------------- */
exports.deleteDailyEntry = async (req, res) => {
    const { date } = req.params; // 경로 파라미터에서 날짜를 가져옴
    const userId = req.user.userId;
    const connection = await pool.getConnection(); // 두 번의 쿼리를 위해 커넥션 사용

    try {
        await connection.beginTransaction();

        // 1. 일기 삭제
        const [result] = await connection.query(
            'DELETE FROM daily_entries WHERE user_id = ? AND entry_date = ?',
            [userId, date]
        );

        // 삭제된 행이 있을 때만 카운트를 깎음 (잘못된 날짜 요청 방지)
        if (result.affectedRows > 0) {
            // 2. 전체 일기 개수 -1 (0 이하로 내려가지 않게 GREATEST 사용)
            await connection.query(
                'UPDATE user_info SET count_diaries = GREATEST(0, count_diaries - 1) WHERE user_id = ?',
                [userId]
            );
        } else {
            // 삭제할 데이터가 없으면 굳이 에러낼 필요 없이 그냥 롤백하고 종료
            await connection.rollback();
            return res.status(404).json({ success: false, message: "삭제할 일기가 없습니다." });
        }

        await connection.commit();
        console.log(`[일기 삭제 성공] userId=${userId}, date=${date}`);
        res.status(200).json({ success: true, message: 'Daily entry deleted successfully' });

    } catch (error) {
        await connection.rollback();
        console.error('❌ [deleteDailyEntry] Error:', error);
        res.status(500).json({ success: false, error: 'Database delete failed' });
    } finally {
        connection.release();
    }
};
