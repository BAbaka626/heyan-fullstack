const fs = require("fs");
const path = require("path");
const Database = require("better-sqlite3");
const { dbPath } = require("./config");

function ensureDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
}

function createDb() {
  ensureDir(dbPath);
  const db = new Database(dbPath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  return db;
}

module.exports = { createDb };
