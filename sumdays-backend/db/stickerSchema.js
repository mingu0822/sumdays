const fs = require('fs');
const path = require('path');

async function ensureStickerSchema(pool) {
  const sql = fs.readFileSync(path.join(__dirname, 'migrations/001_diary_stickers.sql'), 'utf8');
  await pool.query(sql);
}

module.exports = { ensureStickerSchema };

if (require.main === module) {
  const { pool } = require('./db');
  ensureStickerSchema(pool)
    .then(() => console.log('Diary sticker schema ready'))
    .catch(error => { console.error(error.message); process.exitCode = 1; })
    .finally(() => pool.end());
}
