
const { pool } = require('../db/db');
const { buildProfilePayload } = require('../dto/profilePayload');

async function getInitialData(userId) {
    const [
        [memo],
        [dailyEntry],
        [weekSummary],
        [userStyle],
        [userProfile]
    ] = await Promise.all([
        pool.query('SELECT * FROM memo WHERE user_id = ?', [userId]),
        pool.query('SELECT * FROM daily_entry WHERE user_id = ?', [userId]),
        pool.query('SELECT * FROM week_summary WHERE user_id = ?', [userId]),
        pool.query('SELECT * FROM user_style WHERE user_id = ?', [userId]),
        pool.query(`
            SELECT
                profile_mode,
                profile_image_url,
                avatar_face_id,
                avatar_eyes_id,
                avatar_mouth_id,
                avatar_accessory_id
            FROM users
            WHERE id = ?
        `, [userId])
    ]);

    const profile = userProfile[0]
        ? buildProfilePayload(userProfile[0])
        : null;

    return {
        memo,
        dailyEntry,
        weekSummary,
        userStyle,
        prefs: {
            profile
        }
    };
}

module.exports = { getInitialData };
