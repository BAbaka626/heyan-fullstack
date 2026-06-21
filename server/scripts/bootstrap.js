const { createDb } = require("../src/db");
const { rootDir } = require("../src/config");
const { initSchema } = require("../src/schema");
const {
  importAcceptanceWorkbook,
  importDocuments,
  importEnterpriseWorkbook,
  importSurveyWorkbookAsKnowledge,
} = require("../src/services/etl");

const db = createDb();

initSchema(db);
importEnterpriseWorkbook(db, rootDir);
importSurveyWorkbookAsKnowledge(db, rootDir);
importAcceptanceWorkbook(db, rootDir);
importDocuments(db, rootDir);

console.log("Bootstrap completed.");
