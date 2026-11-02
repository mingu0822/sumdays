const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

process.env.DB_TARGET = 'local';
require('dotenv').config({ path: path.join(__dirname, '..', '.env.local') });

const { dbConfig } = require('./db');

async function initializeLocalDatabase() {
  const isSafeLocalTarget = ['127.0.0.1', 'localhost', '::1'].includes(dbConfig.host)
    && /^[A-Za-z0-9_]+_local$/.test(dbConfig.database);
  if (!isSafeLocalTarget) {
    throw new Error('안전하지 않은 DB 대상입니다. 로컬 초기화를 중단합니다.');
  }

  const connection = await mysql.createConnection({
    host: dbConfig.host,
    port: dbConfig.port,
    user: dbConfig.user,
    password: dbConfig.password,
    multipleStatements: true,
  });

  try {
    await connection.query(
      `DROP DATABASE IF EXISTS \`${dbConfig.database}\`;
       CREATE DATABASE \`${dbConfig.database}\`
       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
    );
    await connection.changeUser({ database: dbConfig.database });
    const schema = fs.readFileSync(path.join(__dirname, 'db_schema.sql'), 'utf8');
    await connection.query(schema);
    console.log(`✅ 로컬 기본 DB 초기화 완료: ${dbConfig.database}`);
  } finally {
    await connection.end();
  }
}

initializeLocalDatabase().catch((error) => {
  console.error('❌ 로컬 DB 초기화 실패:', error.message);
  process.exit(1);
});
