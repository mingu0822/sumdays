const mysql = require('mysql2/promise'); // MySQL 데이터베이스와 비동기 통신을 위한 라이브러리

const dbConfig = {
    host: 'sumdays.cjkge2608sxi.ap-northeast-2.rds.amazonaws.com',      // 예: sumdays-db.abcdefg12345.ap-northeast-2.rds.amazonaws.com
    user: 'admin',       // 예: admin
    password: 'kimstyle123##',   // RDS 생성 시 설정한 마스터 암호
    database: 'sumdays_db',         // 연결할 데이터베이스 이름
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
};

// 데이터베이스 커넥션 풀(Pool) 생성. 여러 요청을 효율적으로 처리합니다.
const pool = mysql.createPool(dbConfig);

// ⭐ 반드시 둘 다 export해야 initDB.js에서 pool과 dbConfig 둘 다 사용 가능
module.exports = {
  pool,
  dbConfig
};


