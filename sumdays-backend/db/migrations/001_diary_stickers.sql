CREATE TABLE IF NOT EXISTS diary_stickers (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  daily_entry_id INT NULL,
  owner_id INT NOT NULL,
  author_id INT NOT NULL,
  diary_date CHAR(10) NOT NULL,
  sticker_id INT NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  size DOUBLE NOT NULL,
  rotation DOUBLE NOT NULL,
  initial_geometry JSON NOT NULL,
  removed TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sticker_diary (daily_entry_id, removed, created_at),
  FOREIGN KEY (daily_entry_id) REFERENCES daily_entry(id) ON DELETE SET NULL,
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);
