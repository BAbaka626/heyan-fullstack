require("dotenv").config();
const path = require("path");

const deepSeekApiKey = process.env.DEEPSEEK_API_KEY || "";
const openAiApiKey = process.env.OPENAI_API_KEY || "";
const deepSeekBaseUrl = process.env.DEEPSEEK_BASE_URL || "";
const openAiBaseUrl = process.env.OPENAI_BASE_URL || "";
const deepSeekModel = process.env.DEEPSEEK_MODEL || "";
const openAiModel = process.env.OPENAI_MODEL || "";

module.exports = {
  port: Number(process.env.PORT || 3100),
  dbPath: process.env.DB_PATH || path.resolve(__dirname, "..", "data", "chemical_ai.db"),
  rootDir: path.resolve(__dirname, "..", ".."),
  llmApiKey: deepSeekApiKey || openAiApiKey,
  llmBaseUrl: deepSeekBaseUrl || openAiBaseUrl || "https://api.deepseek.com",
  llmModel: deepSeekModel || openAiModel || "deepseek-v4-flash",
};
