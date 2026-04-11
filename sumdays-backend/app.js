require("dotenv").config();
const express = require('express');
const app = express();

app.use(express.json());
app.use((req, res, next) => {
    console.log(`[${req.method}] ${req.url} | Body: ${JSON.stringify(req.body)}`);
    next();
});
app.use('/api', require('./routes'));

// test: GET http://localhost:3000/
app.get('/', (req, res) => {
  res.send('Hello World!')
})

module.exports = app;
