require("dotenv").config();
const express = require('express');
const app = express();

app.use(express.json());

// 모든 요청 로그 찍기
app.use((req, res, next) => {
  console.log(`[REQUEST] ${req.method} ${req.originalUrl}`);
  console.log("body:", req.body);
  next();
});


app.use('/api', require('./routes'));

// test: GET http://localhost:3000/
app.get('/', (req, res) => {
  res.send('Hello World!')
})

module.exports = app;
