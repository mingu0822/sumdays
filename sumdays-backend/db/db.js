const mysql = require('mysql2/promise'); // MySQL 데이터베이스와 비동기 통신을 위한 라이브러리

const remoteDbConfig = {
    host: 'sumdays.cjkge2608sxi.ap-northeast-2.rds.amazonaws.com',      // 예: sumdays-db.abcdefg12345.ap-northeast-2.rds.amazonaws.com
    user: 'admin',       // 예: admin
    password: 'kimstyle123##',   // RDS 생성 시 설정한 마스터 암호
    database: 'sumdays_db',         // 연결할 데이터베이스 이름
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

const localDbConfig = {
    host: process.env.LOCAL_DB_HOST || '127.0.0.1',
    port: Number(process.env.LOCAL_DB_PORT || 3306),
    user: process.env.LOCAL_DB_USER || 'root',
    password: process.env.LOCAL_DB_PASSWORD || '',
    database: process.env.LOCAL_DB_NAME || 'sumdays_local',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

// 로컬 테스트 진입점에서만 DB_TARGET=local을 설정합니다.
// 일반 server.js 실행은 기존 원격 설정을 그대로 사용합니다.
const dbConfig = process.env.DB_TARGET === 'local'
  ? localDbConfig
  : remoteDbConfig;

// 데이터베이스 커넥션 풀(Pool) 생성. 여러 요청을 효율적으로 처리합니다.
const pool = mysql.createPool(dbConfig);

// ⭐ 반드시 둘 다 export해야 initDB.js에서 pool과 dbConfig 둘 다 사용 가능
module.exports = {
  pool,
  dbConfig,
  remoteDbConfig,
  localDbConfig
};


