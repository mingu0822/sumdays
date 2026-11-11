const {pool} = require('../db/db');
const path = require("path");
const process = require("process");
const fs = require("fs");


/*
room 관련 내용은 syncdata, fetchServerData 모두 포함
프로필 사진은 fetchServerData (다른 pref 초기화하는 거 추가할 떄 따로 뺴야될 지 고민 필요)
*/

// 1. local to server
exports.syncData = async (req, res) => {
  let connection;
  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    console.log("req.body:", req.body);
    console.log("req.files:", req.files);

    const syncRequest = JSON.parse(req.body.syncRequest);
    const { deleted, edited } = syncRequest;
    const userId = req.user.userId;

    let diaryChanged = false;
    let weekSummaryChanged = false;

    const syncedDailyPhotoUrls = [];
    const filesToDeleteAfterCommit = [];


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
        
        if (deleted.dailyEntry?.length > 0) {
          // 1. 삭제될 일기들의 photoUrls 먼저 조회
          const placeholders = deleted.dailyEntry.map(() => '?').join(',');
          const [rows] = await connection.query(
            `
              SELECT date, photoUrls
              FROM daily_entry
              WHERE user_id = ?
                AND date IN (${placeholders})
            `,
            [userId, ...deleted.dailyEntry]
          );

          // 2. 실제 사진 파일 삭제
          for (const row of rows) {
            if (!row.photoUrls) continue;

            const photoUrls = row.photoUrls
              .split(',')
              .map(url => url.trim())
              .filter(Boolean);

            for (const photoUrl of photoUrls) {
              try {
                let relativePath = photoUrl;

                if (
                  photoUrl.startsWith('http://') ||
                  photoUrl.startsWith('https://')
                ) {
                  const parsedUrl = new URL(photoUrl);
                  relativePath = parsedUrl.pathname;
                }

                relativePath = relativePath.replace(/^\/+/, '');

                const filePath = path.join(
                  process.cwd(),
                  relativePath
                );

                if (fs.existsSync(filePath)) {
                  filesToDeleteAfterCommit.push(filePath);

                  console.log(
                    `🗑️ 일기 삭제에 따른 사진 삭제 완료: ${filePath}`
                  );
                }
              } catch (fileDeleteError) {
                console.error(
                  `❌ 일기 사진 삭제 실패: ${photoUrl}`,
                  fileDeleteError
                );

                throw fileDeleteError;
              }
            }
          }

          // 3. 실제 DB row 삭제
          const actuallyDeleted = await deleteIfExists(
            'daily_entry',
            'date',
            deleted.dailyEntry
          );

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
          ['date', 'diary', 'keywords', 'aiComment', 'emotionScore', 'emotionIcon', 'themeIcon', 'is_allowed']
        );
        if (actuallyCreated) {
          diaryChanged = true;
        }
      }

            
      if (edited.dailyPhotos) {
        for (const photoData of edited.dailyPhotos) {
          const {date, serverPhotoUrls, uploadKeys} = photoData;

          // 1. 해당 date의 기존 photoUrls 조회
          const [rows] = await connection.query(
            `
              SELECT photoUrls
              FROM daily_entry
              WHERE user_id = ? AND date = ?
            `,
            [userId, date]
          );

          const existingPhotoUrls = rows.length > 0 && rows[0].photoUrls
            ? rows[0].photoUrls
                .split(',')
                .map(url => url.trim())
                .filter(Boolean)
            : [];

          // 클라이언트가 "유지하겠다"고 보낸 서버 URL들
          const keepPhotoUrls = serverPhotoUrls
            ? serverPhotoUrls
                .split(',')
                .map(url => url.trim())
                .filter(Boolean)
            : [];

          // -----------------------------------------
          // 2. 삭제된 서버 사진 찾기
          // -----------------------------------------
          const deletedPhotoUrls = existingPhotoUrls.filter(
            oldUrl => !keepPhotoUrls.includes(oldUrl)
          );

          // -----------------------------------------
          // 3. 삭제된 사진 실제 파일 삭제
          // -----------------------------------------
          for (const deletedUrl of deletedPhotoUrls) {
            try {
              let relativePath = deletedUrl;

              // 완전한 URL이면 pathname만 추출
              if (
                deletedUrl.startsWith('http://') ||
                deletedUrl.startsWith('https://')
              ) {
                const parsedUrl = new URL(deletedUrl);
                relativePath = parsedUrl.pathname;
              }

              relativePath = relativePath.replace(/^\/+/, '');

              const filePath = path.join(
                process.cwd(),
                relativePath
              );

              if (fs.existsSync(filePath)) {
                filesToDeleteAfterCommit.push(filePath);

                console.log(
                  `🗑️ 사진 삭제 완료: ${filePath}`
                );
              }
            } catch (fileDeleteError) {
              console.error(
                `❌ 사진 삭제 실패: ${deletedUrl}`,
                fileDeleteError
              );
              throw fileDeleteError;
            }
          }

          // -----------------------------------------
          // 4. 새 사진들이 들어갈 최종 폴더 준비
          // -----------------------------------------
          const finalDir = path.join(
            process.cwd(),
            'storage',
            'diaries',
            String(userId)
          );

          await fs.promises.mkdir(
            finalDir,
            { recursive: true }
          );
          const newPhotoUrls = [];

          // -----------------------------------------
          // 5. uploadKeys와 req.files 매칭
          // -----------------------------------------
          if (Array.isArray(uploadKeys)) {
            for (const uploadKey of uploadKeys) {

              const matchedFile = (req.files || []).find(
                file => file.originalname === uploadKey
              );

              if (!matchedFile) {
                throw new Error(
                  `업로드 파일을 찾을 수 없습니다: ${uploadKey}`
                );
              }

              // 실제 저장 파일명
              const ext = path.extname(uploadKey) || '.jpg';

              const finalFileName =
                `diary_${userId}_${Date.now()}_${Math.random()
                  .toString(36)
                  .substring(2, 10)}${ext}`;

              const finalPath = path.join(
                finalDir,
                finalFileName
              );

              // -----------------------------------------
              // 6. temp → 최종 폴더로 이동
              // -----------------------------------------
              await fs.promises.rename(
                matchedFile.path,
                finalPath
              );

              // 서버 DB에 저장할 URL
              const baseUrl = `${req.protocol}://${req.get('host')}`;
              const newPhotoUrl = `${baseUrl}/storage/diaries/${userId}/${finalFileName}`;


              newPhotoUrls.push(newPhotoUrl);

              console.log(
                `✅ 새 사진 저장: ${newPhotoUrl}`
              );
            }
          }

          // -----------------------------------------
          // 7. 최종 URL 구성
          // 기존 유지 사진 + 신규 사진
          // -----------------------------------------
          const finalPhotoUrls = [
            ...keepPhotoUrls,
            ...newPhotoUrls
          ];

          const finalPhotoUrlsString =
            finalPhotoUrls.length > 0
              ? finalPhotoUrls.join(',')
              : '';

          // -----------------------------------------
          // 8. daily_entry photoUrls UPDATE
          // -----------------------------------------
          await connection.query(
            `
              UPDATE daily_entry
              SET photoUrls = ?
              WHERE user_id = ? AND date = ?
            `,
            [
              finalPhotoUrlsString,
              userId,
              date
            ]
          );

          // -----------------------------------------
          // 9. 클라이언트에 반환할 최종 결과 저장
          // -----------------------------------------
          syncedDailyPhotoUrls.push({
            date,
            photoUrls: finalPhotoUrlsString
          });

          console.log(
            `📷 사진 동기화 완료 [${date}]`,
            {
              existing: existingPhotoUrls,
              keep: keepPhotoUrls,
              deleted: deletedPhotoUrls,
              added: newPhotoUrls,
              final: finalPhotoUrls
            }
          );
        }
      }

      if (edited.weekSummary) {
        const incomingStartDates = edited.weekSummary.map((summary) => summary.startDate);
        const [existingRows] = incomingStartDates.length > 0
          ? await connection.query(
              `SELECT startDate FROM week_summary
               WHERE user_id = ? AND startDate IN (?)`,
              [userId, incomingStartDates]
            )
          : [[]];
        const existingStartDates = new Set(existingRows.map((row) => row.startDate));

        await upsert(
          'week_summary',
          edited.weekSummary,
          ['startDate', 'endDate', 'diaryCount', 'emotionAnalysis', 'highlights', 'insights', 'summary']
        );

        const newlyCreatedStartDates = incomingStartDates.filter(
          (startDate) => !existingStartDates.has(startDate)
        );
        for (const startDate of newlyCreatedStartDates) {
          await connection.query(
            `INSERT IGNORE INTO user_mission_events
             (user_id, event_type, event_key, event_date)
             VALUES (?, 'WEEKLY_SUMMARY_CREATED', ?, DATE(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 9 HOUR)))`,
            [userId, startDate]
          );
        }
        weekSummaryChanged = weekSummaryChanged || newlyCreatedStartDates.length > 0;
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
    
    for (const filePath of filesToDeleteAfterCommit) {
      try {
        if (fs.existsSync(filePath)) {
          await fs.promises.unlink(filePath);
          console.log(`🗑️ 사진 삭제 완료: ${filePath}`);
        }
      } catch (fileDeleteError) {
        console.error(
          `❌ commit 이후 사진 삭제 실패: ${filePath}`,
          fileDeleteError
        );
      }
    }

    res.json({
      status: 'success',
      message: 'Sync completed successfully.',
      dailyPhotos: syncedDailyPhotoUrls
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

// 2. server to local

exports.fetchServerData = async (req, res) => {
  try {
    const userId = req.user.userId;

    
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

    // 프로필 사진 URL 조회
    const [userProfile] = await pool.query(
      `SELECT
          profile_mode,
          profile_image_url,
          avatar_face_id,
          avatar_eyes_id,
          avatar_mouth_id,
          avatar_accessory_id
      FROM users
      WHERE id = ?`,
      [userId]
    );

    let profile;
    if (userProfile[0]?.profile_mode === "AVATAR") {
      profile = {
          mode: 'AVATAR',
          faceId: userProfile[0].avatar_face_id,
          eyesId: userProfile[0].avatar_eyes_id,
          mouthId: userProfile[0].avatar_mouth_id,
          accessoryId: userProfile[0].avatar_accessory_id
      };
  } else {
      let profileImage = null;
      const profileImageUrl = userProfile[0]?.profile_image_url;

      if (profileImageUrl) {
        // URL에서 경로만 추출
        const urlPath = new URL(
          profileImageUrl,
          `${req.protocol}://${req.get('host')}`
        ).pathname;

        const relativePath = decodeURIComponent(urlPath).replace(/^\/+/, '');
        const storageRoot = path.resolve(process.cwd(), 'storage');
        const filePath = path.resolve(process.cwd(), relativePath);

        // storage 폴더 밖 파일 접근 방지
        if (!filePath.startsWith(storageRoot + path.sep)) {
          throw new Error('Invalid profile image path');
        }

        try {
          const imageBuffer = await fs.promises.readFile(filePath);
          profileImage = imageBuffer.toString('base64');
        } catch (err) {
          if (err.code !== 'ENOENT') throw err;
          console.warn('프로필 사진 파일 없음:', filePath);
        }
      }

      profile = {
        mode: 'PHOTO',
        image: profileImage
      };
    }
    
    res.json({
      memo,
      dailyEntry,
      weekSummary,
      userStyle,
      profile
    });

  } catch (e) {
    console.error('[fetchServerData] Error:', e);
    res.status(500).json({
      status: 'error',
      message: e.message
    });
  }
};

