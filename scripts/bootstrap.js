const path = require("path");
const { createDb } = require("../src/db");
const { dbPath, rootDir } = require("../src/config");
const { initSchema } = require("../src/schema");
const {
  importAcceptanceWorkbook,
  importDocuments,
  importEnterpriseWorkbook,
  importSurveyWorkbookAsKnowledge,
} = require("../src/services/etl");

function uniquePaths(paths) {
  return Array.from(new Set(paths.map((item) => path.resolve(item))));
}

function bootstrapDatabase(targetDbPath) {
  const db = createDb(targetDbPath);
  try {
    initSchema(db);
    importEnterpriseWorkbook(db, rootDir);
    importSurveyWorkbookAsKnowledge(db, rootDir);
    importAcceptanceWorkbook(db, rootDir);
    importDocuments(db, rootDir);
  } finally {
    db.close();
  }

  console.log(`Bootstrap completed: ${targetDbPath}`);
}

uniquePaths([
  dbPath,
  path.resolve(rootDir, "data", "chemical_ai.db"),
  path.resolve(rootDir, "server", "data", "chemical_ai.db"),
]).forEach(bootstrapDatabase);
