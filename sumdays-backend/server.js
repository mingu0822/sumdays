require("dotenv").config();
const app = require("./app");

const port = process.env.NODEJS_SERVER_PORT;

const { pool } = require('./db/db');
const { ensureStickerSchema } = require('./db/stickerSchema');

// Additive migration only; never run the destructive database initializer on startup.
ensureStickerSchema(pool).then(() => {
  app.listen(port, "0.0.0.0", () => {
    console.log(`Sumdays app listening on port ${port}`);
    console.log("Waiting for request...");
  });
}).catch(error => {
  console.error('Sticker schema migration failed:', error.message);
  process.exitCode = 1;
  pool.end();
});
