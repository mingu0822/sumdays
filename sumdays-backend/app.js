require("dotenv").config();
const express = require('express');
const path = require('path');
const app = express();

app.use(express.json());

// 정적 파일 제공 (사진)
app.use('/storage', express.static(path.join(__dirname, 'storage')));

// 모든 요청 로그 찍기
app.use((req, res, next) => {
  console.log(`[REQUEST] ${req.method} ${req.originalUrl}`);
  const { purchaseToken, ...safeBody } = req.body || {};
  console.log("body:", purchaseToken ? { ...safeBody, purchaseToken: '[redacted]' } : safeBody);
  next();
});


app.use('/api', require('./routes'));

// test: GET http://localhost:3000/
app.get('/', (req, res) => {
  res.send('Hello World!')
})

module.exports = app;
