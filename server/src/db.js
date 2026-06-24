const fs = require("fs");
const path = require("path");
const Database = require("better-sqlite3");
const { dbPath } = require("./config");

function ensureDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
}

function ensureEnterpriseQyfxdjColumn(db) {
  const enterpriseTable = db
    .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'enterprises'")
    .get();
  if (!enterpriseTable) {
    return;
  }

  const columns = db.prepare("PRAGMA table_info(enterprises)").all();
  if (!columns.some((column) => column.name === "qyfxdj")) {
    db.exec("ALTER TABLE enterprises ADD COLUMN qyfxdj INTEGER");
  }
}

function createDb(targetPath = dbPath) {
  const resolvedPath = path.resolve(targetPath);
  ensureDir(resolvedPath);
  const db = new Database(resolvedPath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  ensureEnterpriseQyfxdjColumn(db);
  return db;
}

module.exports = { createDb };
