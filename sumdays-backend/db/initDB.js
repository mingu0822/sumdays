// db/initDB.js
const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

// dbConfig는 실제 DB 설정과 동일하게 적어야 함
const { dbConfig } = require('./db');

async function initializeDatabase() {
  try {
    console.log('🛠 데이터베이스 초기화 중...');

    const schemaPath = path.join(__dirname, 'db_schema.sql');
    const schemaSQL = fs.readFileSync(schemaPath, 'utf8');

    console.log('🔗 RDS 기본 연결 중...');

    // 1️⃣ DB 없이 RDS에 연결
    const connection = await mysql.createConnection({
      host: dbConfig.host,
      user: dbConfig.user,
      password: dbConfig.password,
      multipleStatements: true   // ★ 여러 SQL 문장을 허용
    });

    // 2️⃣ DB 생성 (없으면 생성)
    await connection.query(
      `CREATE DATABASE IF NOT EXISTS \`${dbConfig.database}\`
       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
    );
    console.log(`📌 Database '${dbConfig.database}' 생성 또는 이미 존재`);

    // 3️⃣ 생성한 DB를 사용하도록 변경
    await connection.changeUser({ database: dbConfig.database });
    console.log('🔄 스키마 적용 중...');

    // 4️⃣ db_schema.sql의 전체 문장을 그대로 실행
    await connection.query(schemaSQL);
    await connection.query(fs.readFileSync(path.join(__dirname, 'migrations/001_diary_stickers.sql'), 'utf8'));
    console.log('🎉 DB 스키마 적용 완료!');

    await connection.end();
  } catch (err) {
    console.error('❌ DB 초기화 실패:', err.message);
  }
}

initializeDatabase().then(() => {
  console.log('✅ 초기화 완료!');
  process.exit(0);
});
