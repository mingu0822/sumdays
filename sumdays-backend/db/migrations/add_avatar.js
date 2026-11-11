
require('dotenv').config();

const { pool } = require('../db');

async function migrate() {
    try {
        // 1. 프로필 관련 컬럼 추가
        await pool.query(`
            ALTER TABLE users
                ADD COLUMN profile_mode ENUM('AVATAR', 'PHOTO')
                    NOT NULL DEFAULT 'AVATAR',
                ADD COLUMN avatar_face_id INT NOT NULL DEFAULT -1,
                ADD COLUMN avatar_eyes_id INT NOT NULL DEFAULT -1,
                ADD COLUMN avatar_mouth_id INT NOT NULL DEFAULT -1,
                ADD COLUMN avatar_accessory_id INT NOT NULL DEFAULT -1
        `);

        console.log('프로필 관련 컬럼 추가 완료');

        // 2. 기존 프로필 사진이 있는 사용자는 PHOTO 모드로 변경
        const [result] = await pool.query(`
            UPDATE users
            SET profile_mode = 'PHOTO'
            WHERE profile_image_url IS NOT NULL
              AND profile_image_url <> ''
        `);

        console.log(
            `기존 사진 사용자 ${result.affectedRows}명 PHOTO 모드로 변경`
        );

        // 3. 변경된 DB 구조 확인
        const [columns] = await pool.query(`
            DESCRIBE users
        `);

        console.table(columns);

        console.log('DB 마이그레이션 성공');

    } catch (error) {
        console.error('DB 마이그레이션 실패:', error);

    } finally {
        await pool.end();
    }
}

migrate();
