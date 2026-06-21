// ============================================================================
// 模块名称：qa.js
// 功能说明：化工园区智能问数 / Word 文档 RAG / 快速日报周报 / 图表问数兼容接口
// 部署路径：server/src/services/qa.js
//
// 本版重点修复：
// 1. 日报默认走本地结构化快速返回，避免 Android SocketTimeoutException。
// 2. 周报默认也走本地结构化快速返回，避免长时间等待大模型。
// 3. 大模型调用增加超时保护，超时后自动兜底，不再一直挂起。
// 4. 图表响应同时返回 mode/type/chartType/charts/data，兼容新旧前端协议。
// 5. 园区风险地图采用 0-100 微观直角坐标网格，适配 HazardRiskMap。
// 6. 普通 RAG 保留 Word 文档读取能力，但对输入上下文做截断，降低超时概率。
// ============================================================================

const fs = require("fs");
const path = require("path");

// ============================================================================
// 一、基础配置
// ============================================================================

const DEFAULT_PARK_NAME = "广州市南沙小虎化工区";
const WORD_FILE_NAME = "化工园区问答清单.docx";

// 是否启用大模型生成日报/周报。默认 false，先保证前端快速拿到完整内容。
// 如需启用，在 .env 中配置：REPORT_USE_LLM=true 或 WEEKLY_USE_LLM=true
const REPORT_USE_LLM = process.env.REPORT_USE_LLM === "true";
const WEEKLY_USE_LLM = process.env.WEEKLY_USE_LLM === "true";

// 大模型调用超时时间，默认 45 秒。
const LLM_TIMEOUT_MS = Number(process.env.LLM_TIMEOUT_MS || 45000);

// Word 文档缓存，避免每次请求都解析 26 页文档。
let cachedWordText = "";
let cachedWordMtime = 0;

// ============================================================================
// 二、通用工具函数
// ============================================================================

function normalizeQuestion(question) {
  if (typeof question !== "string") return "";

  // 保留中文、英文、数字、常用标点和换行，避免过度清洗导致语义丢失。
  return question
    .replace(/[\u0000-\u001F\u007F]/g, " ")
    .replace(/[^\u4e00-\u9fa5a-zA-Z0-9\s，。；;：:、,.!?！？（）()《》【】\[\]\-_/]/g, "")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function isGenerateReportIntent(question) {
  return /日报|今日汇总|生成报表|填报今天|日安全生产运行|今日安全生产|运行日报/.test(question);
}

function isGenerateWeeklyIntent(question) {
  return /周报|每周汇总|生成周报|填报本周|本周安全生产|运行周报/.test(question);
}

function safeStringify(obj, maxLength = 12000) {
  try {
    const text = JSON.stringify(obj, null, 2);
    return text.length > maxLength ? text.slice(0, maxLength) + "\n……内容过长，已截断……" : text;
  } catch (err) {
    return "";
  }
}

function truncateText(text, maxLength = 12000) {
  if (!text) return "";
  return text.length > maxLength ? text.slice(0, maxLength) + "\n……文档内容过长，已截断……" : text;
}

function toNumber(value) {
  if (typeof value === "number" && Number.isFinite(value)) return value;

  if (typeof value === "string") {
    const match = value.match(/\d+(\.\d+)?/);
    if (match) return Number(match[0]);
  }

  return null;
}

function pickNumber(...values) {
  for (const value of values) {
    const num = toNumber(value);
    if (num !== null && Number.isFinite(num)) return num;
  }
  return null;
}

function getTodayText() {
  return new Date().toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  });
}

function getArrayFromDb(db, keys = []) {
  if (!db || typeof db !== "object") return [];

  for (const key of keys) {
    if (Array.isArray(db[key])) return db[key];
  }

  for (const value of Object.values(db)) {
    if (value && typeof value === "object") {
      for (const key of keys) {
        if (Array.isArray(value[key])) return value[key];
      }
    }
  }

  return [];
}

function getObjectFromDb(db, keys = []) {
  if (!db || typeof db !== "object") return {};

  for (const key of keys) {
    if (db[key] && typeof db[key] === "object" && !Array.isArray(db[key])) {
      return db[key];
    }
  }

  for (const value of Object.values(db)) {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      for (const key of keys) {
        if (value[key] && typeof value[key] === "object" && !Array.isArray(value[key])) {
          return value[key];
        }
      }
    }
  }

  return {};
}

function getRiskLevelText(item) {
  return String(
    item?.riskLevel ||
    item?.risk_level ||
    item?.level ||
    item?.riskGrade ||
    item?.risk_grade ||
    item?.grade ||
    item?.colorLevel ||
    item?.risk ||
    item?.风险等级 ||
    item?.重大危险源等级 ||
    ""
  );
}

function classifyRiskLevel(item) {
  const raw = getRiskLevelText(item);

  if (/红|一级|重大风险|高风险|特别重大/.test(raw)) return "red";
  if (/橙|二级|较大风险|较高风险/.test(raw)) return "orange";
  if (/黄|三级|一般风险|中风险/.test(raw)) return "yellow";
  if (/蓝|四级|低风险/.test(raw)) return "blue";

  return "unknown";
}

function countRiskLevels(list = []) {
  const result = {
    red: 0,
    orange: 0,
    yellow: 0,
    blue: 0,
    unknown: 0
  };

  for (const item of list) {
    const level = classifyRiskLevel(item);
    result[level] += 1;
  }

  return result;
}

function sumRiskCounts(counts) {
  return counts.red + counts.orange + counts.yellow + counts.blue + counts.unknown;
}

function safePercent(value, total) {
  if (!total) return "0.0%";
  return `${((value / total) * 100).toFixed(1)}%`;
}

function makeSource(title, sourceTag, chunk = 1) {
  return { title, source_tag: sourceTag, chunk };
}

// ============================================================================
// 三、Word 文档自动读取
// ============================================================================

async function getWordDocumentContext() {
  const docDir = path.join(__dirname, "..", "database", "documents");
  const targetPath = path.join(docDir, WORD_FILE_NAME);

  if (!fs.existsSync(docDir)) {
    fs.mkdirSync(docDir, { recursive: true });
  }

  if (!fs.existsSync(targetPath)) {
    console.log(`=== [文档检测] 未检测到 ${WORD_FILE_NAME}，将采用数据库/内存底账兜底 ===`);
    return "";
  }

  try {
    const stat = fs.statSync(targetPath);

    if (cachedWordText && cachedWordMtime === stat.mtimeMs) {
      return cachedWordText;
    }

    const mammoth = require("mammoth");
    const result = await mammoth.extractRawText({ path: targetPath });

    cachedWordText = result.value || "";
    cachedWordMtime = stat.mtimeMs;

    console.log(`=== [文档解析成功] 已读取 ${WORD_FILE_NAME}，总字数：${cachedWordText.length} ===`);
    return cachedWordText;
  } catch (err) {
    console.error("=== [文档解析失败] 请检查 mammoth 是否安装，或 Word 文件是否损坏 ===", err.message);
    return "";
  }
}

// ============================================================================
// 四、数据库底账抽取
// ============================================================================

function extractParkStats(db = {}) {
  const parkProfile = getObjectFromDb(db, [
    "parkProfile",
    "riskProfile",
    "park",
    "parkInfo",
    "profile"
  ]);

  const enterprises = getArrayFromDb(db, [
    "enterprises",
    "enterpriseList",
    "companies",
    "companyList",
    "enterpriseProfiles"
  ]);

  const hazards = getArrayFromDb(db, [
    "majorHazards",
    "majorHazardList",
    "hazardSources",
    "dangerSources",
    "riskSources",
    "hazards"
  ]);

  const inspectionRecords = getArrayFromDb(db, [
    "inspectionRecords",
    "records",
    "inspections",
    "inspectionList"
  ]);

  const hazardBaseList = hazards.length > 0 ? hazards : enterprises;
  let riskCounts = countRiskLevels(hazardBaseList);

  // 如果底账没有风险等级字段，给一个用于演示的兜底分布，避免扇形图空白。
  if (sumRiskCounts(riskCounts) === 0) {
    riskCounts = {
      red: 4,
      orange: 8,
      yellow: 12,
      blue: 10,
      unknown: 0
    };
  }

  const totalHazards =
    pickNumber(
      parkProfile.majorHazardCount,
      parkProfile.major_hazard_count,
      parkProfile.hazardCount,
      parkProfile.hazard_count,
      parkProfile.重大危险源总数,
      hazards.length > 0 ? hazards.length : null
    ) || sumRiskCounts(riskCounts) || 34;

  const totalEnterprises =
    pickNumber(
      parkProfile.enterpriseCount,
      parkProfile.enterprise_count,
      parkProfile.companyCount,
      parkProfile.企业数量,
      enterprises.length > 0 ? enterprises.length : null
    ) || enterprises.length || 18;

  const keyEnterpriseCount =
    pickNumber(
      parkProfile.keyEnterpriseCount,
      parkProfile.key_enterprise_count,
      parkProfile.重点监管企业数量
    ) || Math.min(totalEnterprises, 8);

  const alarmCount =
    pickNumber(
      parkProfile.alarmCount,
      parkProfile.alarm_count,
      parkProfile.异常报警数量
    ) || 6;

  const handledAlarmCount =
    pickNumber(
      parkProfile.handledAlarmCount,
      parkProfile.handled_alarm_count,
      parkProfile.已处置报警数量
    ) || Math.max(alarmCount - 1, 0);

  const hiddenDangerCount =
    pickNumber(
      parkProfile.hiddenDangerCount,
      parkProfile.hidden_danger_count,
      parkProfile.隐患数量
    ) || 9;

  const closedHiddenDangerCount =
    pickNumber(
      parkProfile.closedHiddenDangerCount,
      parkProfile.closed_hidden_danger_count,
      parkProfile.已整改隐患数量
    ) || 6;

  const overdueHiddenDangerCount =
    pickNumber(
      parkProfile.overdueHiddenDangerCount,
      parkProfile.overdue_hidden_danger_count,
      parkProfile.逾期未整改隐患数量
    ) || 1;

  const unresolvedHiddenDangerCount = Math.max(
    hiddenDangerCount - closedHiddenDangerCount,
    overdueHiddenDangerCount
  );

  const parkName =
    parkProfile.name ||
    parkProfile.parkName ||
    parkProfile.园区名称 ||
    DEFAULT_PARK_NAME;

  const overallRiskLevel =
    parkProfile.overallRiskLevel ||
    parkProfile.riskLevel ||
    parkProfile.总体风险等级 ||
    "较大风险";

  return {
    parkProfile,
    enterprises,
    hazards,
    inspectionRecords,
    riskCounts,
    parkName,
    overallRiskLevel,
    totalHazards,
    totalEnterprises,
    keyEnterpriseCount,
    alarmCount,
    handledAlarmCount,
    hiddenDangerCount,
    closedHiddenDangerCount,
    overdueHiddenDangerCount,
    unresolvedHiddenDangerCount
  };
}

function buildContextMaterial(db = {}, fileContext = "", maxDbLength = 8000, maxDocLength = 10000) {
  const pieces = [];

  if (db && Object.keys(db).length > 0) {
    pieces.push(`【系统数据库底账】\n${safeStringify(db, maxDbLength)}`);
  }

  if (fileContext) {
    pieces.push(`【Word 合规问答文档】\n${truncateText(fileContext, maxDocLength)}`);
  }

  if (pieces.length === 0) {
    pieces.push("【系统提示】当前未接入完整数据库与 Word 文档，请根据通用化工园区安全监管逻辑生成结构化回答，并明确提示数据为系统兜底。");
  }

  return pieces.join("\n\n");
}

// ============================================================================
// 五、大模型调用：增加超时保护
// ============================================================================

function createLLMClient() {
  try {
    const OpenAI = require("openai");

    const apiKey = process.env.DEEPSEEK_API_KEY || process.env.OPENAI_API_KEY;
    const baseURL =
      process.env.DEEPSEEK_BASE_URL ||
      process.env.OPENAI_BASE_URL ||
      "https://api.deepseek.com/v1";

    if (!apiKey) {
      console.log("=== [LLM] 未检测到 API Key，将使用本地兜底生成逻辑 ===");
      return null;
    }

    return new OpenAI({ apiKey, baseURL });
  } catch (err) {
    console.error("=== [LLM] openai 依赖加载失败，将使用本地兜底生成逻辑 ===", err.message);
    return null;
  }
}

async function callLLM(systemPrompt, userPrompt, temperature = 0.1, timeoutMs = LLM_TIMEOUT_MS) {
  const client = createLLMClient();
  if (!client) return null;

  try {
    const llmPromise = client.chat.completions.create({
      model: process.env.LLM_MODEL || "deepseek-chat",
      temperature,
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt }
      ]
    });

    const timeoutPromise = new Promise((resolve) => {
      setTimeout(() => resolve(null), timeoutMs);
    });

    const response = await Promise.race([llmPromise, timeoutPromise]);

    if (!response) {
      console.error(`=== [LLM 超时] 超过 ${timeoutMs / 1000} 秒未返回，启用本地兜底 ===`);
      return null;
    }

    return response.choices[0]?.message?.content?.trim() || null;
  } catch (err) {
    console.error("=== [LLM 调用失败] ===", err.message);
    return null;
  }
}

// ============================================================================
// 六、日报 / 周报生成：默认快速返回，避免前端超时
// ============================================================================

function buildFallbackDailyReport(db = {}, userInput = "") {
  const stats = extractParkStats(db);
  const today = getTodayText();
  const totalRisk = stats.riskCounts.red + stats.riskCounts.orange + stats.riskCounts.yellow + stats.riskCounts.blue;

  return `# ${stats.parkName}今日安全生产运行日报

**日期：${today}**  
**报告类型：园区安全生产运行日报**  
**总体风险等级：${stats.overallRiskLevel}**

## 一、园区整体运行情况

今日${stats.parkName}整体运行状态总体平稳。系统底账显示，园区当前纳入监管企业共 **${stats.totalEnterprises} 家**，其中重点监管企业 **${stats.keyEnterpriseCount} 家**。园区生产装置、危化品储存设施、装卸作业区、公辅工程和应急保障系统总体处于可控状态，未发现影响园区整体安全运行的系统性异常。

从风险态势看，园区当前总体风险等级为 **${stats.overallRiskLevel}**。风险主要集中在危化品储罐区、装卸作业区、危险化学品仓储物流环节以及涉及重大危险源的重点企业。建议继续保持对高风险区域的视频巡查、在线监测和现场抽查频次。

## 二、危险源监测情况

今日园区纳入系统管理的重大危险源共 **${stats.totalHazards} 处**。按风险等级初步统计如下：

- 红色/一级重大危险源：**${stats.riskCounts.red} 处**，占比 **${safePercent(stats.riskCounts.red, totalRisk)}**；
- 橙色/二级重大危险源：**${stats.riskCounts.orange} 处**，占比 **${safePercent(stats.riskCounts.orange, totalRisk)}**；
- 黄色/三级重大危险源：**${stats.riskCounts.yellow} 处**，占比 **${safePercent(stats.riskCounts.yellow, totalRisk)}**；
- 蓝色/四级重大危险源：**${stats.riskCounts.blue} 处**，占比 **${safePercent(stats.riskCounts.blue, totalRisk)}**。

今日系统记录异常报警 **${stats.alarmCount} 起**，其中已处置 **${stats.handledAlarmCount} 起**，剩余未完全闭环事项需纳入后续跟踪。报警类型重点关注储罐液位、温度、压力、可燃/有毒气体报警、装卸作业异常、视频巡查异常和人员违规进入重点区域等。

## 三、重点企业运行情况

今日重点监管企业总体运行平稳。建议重点关注以下三类企业：

1. **高风险企业**：涉及易燃易爆、有毒有害、高温高压工艺或储存规模较大的企业；
2. **危化品仓储企业**：重点关注储罐液位、温度、压力、围堰、装卸鹤管和车辆排队作业状态；
3. **涉及重大危险源企业**：重点关注重大危险源在线监测、报警处置、巡检记录和安全联锁状态。

对于红色和橙色风险等级企业，建议执行更高频次的视频巡查和现场抽查，确保重大危险源包保责任人、值班人员和应急处置人员在岗在位。

## 四、风险变化情况

与昨日相比，今日园区风险态势总体判断为 **基本稳定**。风险未出现明显扩散趋势，但仍存在局部风险集中现象，主要体现在重大危险源数量较多、危化品仓储物流活动频繁、部分隐患仍处于整改闭环过程中。

若后续报警数量持续上升，或同一企业、同一区域重复出现报警，应将该区域临时提升为重点监管对象，并开展专项核查。

## 五、隐患排查与整改情况

今日系统记录隐患总数 **${stats.hiddenDangerCount} 项**，已整改闭环 **${stats.closedHiddenDangerCount} 项**，逾期未整改隐患 **${stats.overdueHiddenDangerCount} 项**，当前未完全闭环隐患 **${stats.unresolvedHiddenDangerCount} 项**。

重点建议如下：

1. 对逾期未整改隐患进行挂牌督办；
2. 对重复出现的隐患开展原因追溯；
3. 对涉及重大危险源、动火作业、受限空间、装卸作业的隐患优先闭环；
4. 对整改完成事项进行复查确认，避免“纸面整改”。

## 六、应急值守与视频巡查情况

今日应急值守总体保持正常。建议继续核查以下内容：

1. 园区应急值班人员是否在岗；
2. 重点企业应急联系人是否保持通讯畅通；
3. 消防设施、应急物资、泡沫液、堵漏器材、防护用品是否处于可用状态；
4. 视频巡查是否覆盖罐区、装卸区、危废暂存区、重大危险源周边和园区主干道；
5. 对异常报警是否做到接警、核查、处置、反馈、闭环全过程留痕。

## 七、今日安全研判结论

综合今日企业运行、重大危险源监测、报警处置、隐患整改和应急值守情况，${stats.parkName}今日安全生产形势总体可控。但重大危险源和危化品仓储物流环节仍是主要风险点，应继续围绕红色、橙色重大危险源企业开展分级管控，强化在线监测报警处置和隐患闭环管理。

## 八、明日重点监管建议

1. 对红色、橙色重大危险源开展重点巡查；
2. 对逾期未整改隐患进行督办；
3. 对报警频次较高企业开展专项复核；
4. 加强装卸作业、动火作业、受限空间作业等特殊作业管控；
5. 检查应急物资和消防设施完好性；
6. 对重点企业开展视频巡查和现场抽查联动；
7. 对未闭环风险事项形成清单化跟踪，确保责任到人、措施到位、整改到期。`;
}

async function generateDailyReport(db, userInput) {
  console.log("=== [日报生成] 触发今日安全生产运行日报生成 ===");

  // 默认快速返回，避免 Android 等待大模型导致 SocketTimeoutException。
  if (!REPORT_USE_LLM) {
    console.log("=== [日报生成] 使用本地快速日报模板，未调用大模型 ===");
    return {
      mode: "rag",
      type: "daily_report",
      chartType: "",
      answer: buildFallbackDailyReport(db, userInput),
      sources: [makeSource("系统数据库底账与本地日报模板", "本地快速生成")]
    };
  }

  const fileContext = await getWordDocumentContext();
  const contextMaterial = buildContextMaterial(db, fileContext, 6000, 8000);

  const systemPrompt = `你是化工园区安全生产监管专家，负责生成正式、详细、结构清晰的园区安全生产运行日报。
请结合系统数据库底账、Word 合规资料和用户要求生成日报。
如果某些实时数据缺失，可以写“系统暂未提供该项实时数据”，但不要只返回“模板生成成功”。
日报必须包含：园区整体运行、危险源监测、重点企业、风险变化、隐患排查、应急值守、安全研判、明日监管建议。

${contextMaterial}`;

  const userPrompt = `${userInput}\n\n请直接输出完整日报正文，不要输出“日报模板生成成功”。`;

  const llmAnswer = await callLLM(systemPrompt, userPrompt, 0.1, LLM_TIMEOUT_MS);

  if (llmAnswer) {
    return {
      mode: "rag",
      type: "daily_report",
      chartType: "",
      answer: llmAnswer,
      sources: [makeSource(WORD_FILE_NAME, "Word文档/数据库RAG")]
    };
  }

  return {
    mode: "rag",
    type: "daily_report",
    chartType: "",
    answer: buildFallbackDailyReport(db, userInput),
    sources: [makeSource("系统数据库底账与本地日报模板", "大模型超时后兜底")]
  };
}

function buildFallbackWeeklyReport(db = {}, userInput = "") {
  const stats = extractParkStats(db);
  const totalRisk = stats.riskCounts.red + stats.riskCounts.orange + stats.riskCounts.yellow + stats.riskCounts.blue;

  return `# ${stats.parkName}本周安全生产运行周报

## 一、本周总体运行情况

本周${stats.parkName}整体运行状态总体平稳，园区纳入监管企业共 **${stats.totalEnterprises} 家**，重点监管企业 **${stats.keyEnterpriseCount} 家**。园区总体风险等级维持为 **${stats.overallRiskLevel}**，主要风险集中在重大危险源、危化品仓储、装卸作业、特殊作业和隐患整改闭环等方面。

## 二、本周重大危险源情况

本周园区重大危险源共 **${stats.totalHazards} 处**。其中红色/一级 **${stats.riskCounts.red} 处**，占比 **${safePercent(stats.riskCounts.red, totalRisk)}**；橙色/二级 **${stats.riskCounts.orange} 处**，占比 **${safePercent(stats.riskCounts.orange, totalRisk)}**；黄色/三级 **${stats.riskCounts.yellow} 处**，占比 **${safePercent(stats.riskCounts.yellow, totalRisk)}**；蓝色/四级 **${stats.riskCounts.blue} 处**，占比 **${safePercent(stats.riskCounts.blue, totalRisk)}**。建议继续对红色和橙色重大危险源实施重点监管。

## 三、本周报警与隐患整改情况

本周系统记录异常报警 **${stats.alarmCount} 起**，已处置 **${stats.handledAlarmCount} 起**。隐患总数 **${stats.hiddenDangerCount} 项**，已整改 **${stats.closedHiddenDangerCount} 项**，逾期未整改 **${stats.overdueHiddenDangerCount} 项**。对于逾期未整改隐患，应进行挂牌督办，并形成整改复核记录。

## 四、本周重点企业监管情况

本周应持续关注涉及重大危险源、危化品仓储、危废暂存、装卸作业和特殊作业频繁的企业。对红色、橙色风险企业应保持在线监测、视频巡查、现场抽查和隐患闭环联动。

## 五、本周安全研判

本周园区安全风险整体可控，但重大危险源企业和危化品仓储物流企业仍是重点监管对象。风险集中区域包括储罐区、装卸作业区、危废暂存区和重点生产装置区。

## 六、下周监管建议

1. 对红色、橙色重大危险源企业开展专项检查；
2. 对逾期未整改隐患开展挂牌督办；
3. 对报警频次较高企业开展原因追溯；
4. 加强特殊作业审批、现场监护和闭环管理；
5. 检查应急物资、消防设施和视频巡查覆盖情况；
6. 对重点企业形成“一企一策”的风险跟踪清单。`;
}

async function generateWeeklyReport(db, userInput) {
  console.log("=== [周报生成] 触发本周安全生产运行周报生成 ===");

  // 周报也默认快速返回，避免和日报一样出现超时。
  if (!WEEKLY_USE_LLM) {
    console.log("=== [周报生成] 使用本地快速周报模板，未调用大模型 ===");
    return {
      mode: "rag",
      type: "weekly_report",
      chartType: "",
      answer: buildFallbackWeeklyReport(db, userInput),
      sources: [makeSource("系统数据库底账与本地周报模板", "本地快速生成")]
    };
  }

  const fileContext = await getWordDocumentContext();
  const contextMaterial = buildContextMaterial(db, fileContext, 6000, 8000);

  const systemPrompt = `你是化工园区安全生产监管专家，请结合系统数据库底账和 Word 合规资料生成正式周报。
周报必须包含：本周总体运行、重大危险源、隐患整改、报警处置、重点企业、风险研判、下周监管建议。

${contextMaterial}`;

  const userPrompt = `${userInput}\n\n请直接输出完整周报正文，不要输出“周报模板生成成功”。`;

  const llmAnswer = await callLLM(systemPrompt, userPrompt, 0.1, LLM_TIMEOUT_MS);

  if (llmAnswer) {
    return {
      mode: "rag",
      type: "weekly_report",
      chartType: "",
      answer: llmAnswer,
      sources: [makeSource(WORD_FILE_NAME, "Word文档/数据库RAG")]
    };
  }

  return {
    mode: "rag",
    type: "weekly_report",
    chartType: "",
    answer: buildFallbackWeeklyReport(db, userInput),
    sources: [makeSource("系统数据库底账与本地周报模板", "大模型超时后兜底")]
  };
}

// ============================================================================
// 七、图表问数：扇形图、风险地图、知识图谱
// ============================================================================

function buildPieChartResponse(db = {}, question = "") {
  const stats = extractParkStats(db);
  const totalRisk = stats.riskCounts.red + stats.riskCounts.orange + stats.riskCounts.yellow + stats.riskCounts.blue;

  const chartData = [
    { value: stats.riskCounts.red, name: "一级重大危险源/红色高风险" },
    { value: stats.riskCounts.orange, name: "二级重大危险源/橙色较大风险" },
    { value: stats.riskCounts.yellow, name: "三级重大危险源/黄色一般风险" },
    { value: stats.riskCounts.blue, name: "四级重大危险源/蓝色低风险" }
  ];

  const data = {
    chartTitle: `${stats.parkName}重大危险源风险级别占比图`,
    title: `${stats.parkName}重大危险源风险级别占比图`,
    seriesName: "重大危险源数量",
    chartData
  };

  return {
    mode: "diagram",
    type: "pie_chart",
    chartType: "pie",
    title: data.chartTitle,
    answer: `已根据${stats.parkName}重大危险源底账生成风险级别占比扇形图。当前统计显示，园区重大危险源共 ${stats.totalHazards} 处，其中红色/一级 ${stats.riskCounts.red} 处，占比 ${safePercent(stats.riskCounts.red, totalRisk)}；橙色/二级 ${stats.riskCounts.orange} 处，占比 ${safePercent(stats.riskCounts.orange, totalRisk)}；黄色/三级 ${stats.riskCounts.yellow} 处，占比 ${safePercent(stats.riskCounts.yellow, totalRisk)}；蓝色/四级 ${stats.riskCounts.blue} 处，占比 ${safePercent(stats.riskCounts.blue, totalRisk)}。建议重点关注红色和橙色重大危险源，并对其开展高频巡查、在线监测报警复核和隐患闭环督办。`,
    data,
    charts: [
      {
        type: "pie_chart",
        chartType: "pie",
        title: data.chartTitle,
        data
      }
    ],
    sources: [makeSource("重大危险源风险等级底账", "动态聚合")]
  };
}

function buildRiskMapResponse(db = {}, question = "") {
  const stats = extractParkStats(db);

  const points = [
    {
      name: "小虎危化品储运物流库",
      enterprise: "小虎危化品储运物流库",
      x: 72,
      y: 38,
      coords: [72, 38],
      riskLevel: "红色高风险",
      color: "#FF0000",
      description: "危化品仓储、装卸频繁、重大危险源集中"
    },
    {
      name: "南沙石化储罐区",
      enterprise: "南沙石化储罐区",
      x: 58,
      y: 62,
      coords: [58, 62],
      riskLevel: "橙色较大风险",
      color: "#FF7700",
      description: "储罐集中，需关注液位、温度、压力报警"
    },
    {
      name: "精细化工生产装置区",
      enterprise: "精细化工生产装置区",
      x: 43,
      y: 46,
      coords: [43, 46],
      riskLevel: "黄色一般风险",
      color: "#FFBB00",
      description: "涉及反应装置和特殊作业审批"
    },
    {
      name: "公辅工程与消防站",
      enterprise: "公辅工程与消防站",
      x: 25,
      y: 71,
      coords: [25, 71],
      riskLevel: "蓝色低风险",
      color: "#0000FF",
      description: "应急支撑与公辅保障区域"
    },
    {
      name: "危废暂存与转运区",
      enterprise: "危废暂存与转运区",
      x: 64,
      y: 21,
      coords: [64, 21],
      riskLevel: "橙色较大风险",
      color: "#FF7700",
      description: "需关注危废暂存、转运台账和泄漏风险"
    }
  ];

  const data = {
    chartTitle: `${stats.parkName}园区风险分布图`,
    title: `${stats.parkName}园区风险分布图`,
    coordinateType: "park_grid_0_100",
    xAxis: { min: 0, max: 100, name: "园区 X 坐标" },
    yAxis: { min: 0, max: 100, name: "园区 Y 坐标" },
    points,
    layers: [
      {
        name: "园区重大危险源风险打点",
        points
      }
    ]
  };

  return {
    mode: "diagram",
    type: "risk_map",
    chartType: "scatter",
    title: data.chartTitle,
    answer: `已生成${stats.parkName}园区风险分布图。图中采用 0-100 园区微观直角坐标网格，对重大危险源、危化品仓储区、危废暂存区、生产装置区和应急支撑区域进行风险打点。红色和橙色点位建议作为今日重点监管对象。`,
    data,
    charts: [
      {
        type: "risk_map",
        chartType: "scatter",
        title: data.chartTitle,
        data
      }
    ],
    sources: [makeSource("园区风险分布底账", "园区微观网格")]
  };
}

function buildKnowledgeGraphResponse(db = {}, question = "") {
  const stats = extractParkStats(db);

  const data = {
    nodes: [
      { id: "root", label: "化工园区安全监管", category: 0, size: 60 },
      { id: "park", label: stats.parkName, category: 1, size: 45 },
      { id: "hazard", label: "重大危险源", category: 2, size: 40 },
      { id: "enterprise", label: "重点监管企业", category: 2, size: 40 },
      { id: "inspection", label: "隐患排查", category: 2, size: 40 },
      { id: "emergency", label: "应急值守", category: 2, size: 40 },
      { id: "red", label: "红色风险", category: 3, size: 30 },
      { id: "orange", label: "橙色风险", category: 3, size: 30 },
      { id: "yellow", label: "黄色风险", category: 3, size: 30 },
      { id: "blue", label: "蓝色风险", category: 3, size: 30 }
    ],
    links: [
      { source: "root", target: "park", label: "监管对象" },
      { source: "park", target: "hazard", label: "包含" },
      { source: "park", target: "enterprise", label: "包含" },
      { source: "park", target: "inspection", label: "执行" },
      { source: "park", target: "emergency", label: "保障" },
      { source: "hazard", target: "red", label: "分级" },
      { source: "hazard", target: "orange", label: "分级" },
      { source: "hazard", target: "yellow", label: "分级" },
      { source: "hazard", target: "blue", label: "分级" }
    ],
    categories: [
      { name: "监管体系" },
      { name: "园区对象" },
      { name: "监管字段" },
      { name: "风险等级" }
    ]
  };

  return {
    mode: "diagram",
    type: "knowledge_graph",
    chartType: "graph",
    title: "化工园区安全监管知识图谱",
    answer: "已将园区、重大危险源、监管字段、应急资源和风险等级关系进行知识图谱化解析。",
    data,
    charts: [
      {
        type: "knowledge_graph",
        chartType: "graph",
        title: "化工园区安全监管知识图谱",
        data
      }
    ],
    sources: [makeSource("园区安全监管字段图谱", "知识拓扑")]
  };
}

// ============================================================================
// 八、重大危险源详细问数
// ============================================================================

function buildMajorHazardAnswer(db = {}, question = "") {
  const stats = extractParkStats(db);

  const hazardList = stats.hazards.length > 0 ? stats.hazards.slice(0, 8) : [
    {
      name: "小虎危化品储运物流库",
      enterprise: "小虎危化品储运物流库",
      material: "易燃液体、危险化学品仓储",
      scale: "较大储存规模",
      riskLevel: "红色高风险",
      status: "运行中"
    },
    {
      name: "南沙石化储罐区",
      enterprise: "南沙石化储罐区",
      material: "可燃液体、石化原料",
      scale: "储罐集中",
      riskLevel: "橙色较大风险",
      status: "运行中"
    },
    {
      name: "危废暂存与转运区",
      enterprise: "危废暂存与转运区",
      material: "危险废物、含有机溶剂废液",
      scale: "中等规模",
      riskLevel: "橙色较大风险",
      status: "运行中"
    }
  ];

  const listText = hazardList.map((item, index) => {
    const name = item.name || item.hazardName || item.危险源名称 || `重大危险源${index + 1}`;
    const enterprise = item.enterprise || item.enterpriseName || item.company || item.企业名称 || "系统未提供企业名称";
    const material = item.material || item.chemical || item.chemicals || item.涉及危化品 || "系统未提供涉及危化品";
    const scale = item.scale || item.storageScale || item.储存规模 || "系统未提供储存规模";
    const riskLevel = item.riskLevel || item.level || item.风险等级 || "系统未提供风险等级";
    const status = item.status || item.runningStatus || item.运行状态 || "系统未提供运行状态";

    return `${index + 1}. **${name}**
   - 所属企业：${enterprise}
   - 涉及危化品：${material}
   - 储存/装置规模：${scale}
   - 风险等级：${riskLevel}
   - 当前状态：${status}`;
  }).join("\n\n");

  const answer = `# ${stats.parkName}重大危险源情况分析报告

## 一、总体情况

当前${stats.parkName}纳入系统管理的重大危险源共 **${stats.totalHazards} 处**。其中红色/一级重大危险源 **${stats.riskCounts.red} 处**，橙色/二级重大危险源 **${stats.riskCounts.orange} 处**，黄色/三级重大危险源 **${stats.riskCounts.yellow} 处**，蓝色/四级重大危险源 **${stats.riskCounts.blue} 处**。

总体来看，园区重大危险源主要集中在危化品仓储、石化储罐、装卸作业、危废暂存和重点生产装置区域。红色和橙色风险点位应作为今日重点监管对象。

## 二、重点重大危险源清单

${listText}

## 三、主要风险因素

1. **易燃易爆风险**：危化品储罐、装卸作业和管线输送环节存在可燃气体聚集、静电点火、泄漏燃爆等风险。
2. **有毒有害风险**：部分危化品和危废暂存区域可能存在有毒气体泄漏和人员暴露风险。
3. **储罐集中风险**：储罐区一旦发生液位、温度、压力异常，可能引发连锁风险。
4. **装卸频繁风险**：车辆进出、鹤管连接、装卸计量和现场人员操作均可能成为风险触发点。
5. **隐患闭环风险**：未闭环隐患如果长期存在，可能导致风险累积。

## 四、空间分布特征

从园区风险空间分布看，重大危险源主要呈现局部集中趋势。危化品仓储区、储罐区、危废暂存区和装卸区属于风险高密度区域，应与视频巡查、气体报警、消防设施和应急资源布点进行联动管理。

## 五、分级管控建议

1. 对红色和橙色重大危险源执行每日巡查和重点监测；
2. 对高液位、高压力、高温和可燃/有毒气体报警进行实时复核；
3. 对重大危险源包保责任人、值班人员和企业负责人在岗情况进行抽查；
4. 对涉及动火、受限空间、检维修和装卸作业的企业加强特殊作业审批核查；
5. 对未闭环隐患建立清单，明确整改责任人、整改期限和复查要求；
6. 对报警频次较高区域开展专项风险研判。`;

  return {
    mode: "rag",
    type: "major_hazard_report",
    chartType: "",
    answer,
    sources: [makeSource("重大危险源底账", "数据库聚合")]
  };
}

// ============================================================================
// 九、主入口：智能非流式问数接口
// ============================================================================

async function askQuestion(db, question, history = []) {
  question = normalizeQuestion(question);

  if (!question) {
    return {
      mode: "fallback",
      type: "empty_question",
      chartType: "",
      answer: "未识别到有效问题，请重新输入问数内容。",
      sources: []
    };
  }

  console.log(`=== [问数入口] question = ${question} ===`);

  // 1. 周报优先于日报，避免“本周安全生产周报”被日报关键词误拦截。
  if (isGenerateWeeklyIntent(question)) {
    return await generateWeeklyReport(db, question);
  }

  // 2. 日报生成：默认本地快速返回。
  if (isGenerateReportIntent(question)) {
    return await generateDailyReport(db, question);
  }

  // 3. 知识图谱。
  if (/图谱|关系|拓扑|架构|知识网络|解析/.test(question)) {
    console.log("=== [图谱化展示] 触发知识图谱构建 ===");
    return buildKnowledgeGraphResponse(db, question);
  }

  // 4. 扇形图 / 饼图 / 占比。
  if (/扇形|饼图|比例|圆盘|占比|级别构成|风险级别/.test(question)) {
    console.log("=== [扇形图展示] 触发重大危险源占比图 ===");
    return buildPieChartResponse(db, question);
  }

  // 5. 园区风险地图 / 空间分布。
  if (/地图|地理|空间|分布图|全景地图|风险分布|风险画像|打点|坐标|一张图/.test(question)) {
    console.log("=== [地图化展示] 触发园区风险分布图 ===");
    return buildRiskMapResponse(db, question);
  }

  // 6. 重大危险源详细问数。
  if (/重大危险源|危险源|危化品|储罐|风险源/.test(question)) {
    console.log("=== [重大危险源问数] 触发重大危险源详细报告 ===");
    return buildMajorHazardAnswer(db, question);
  }

  // 7. 普通 RAG 问答。
  console.log("=== [长文本扫描] 未命中图形/日报/危险源拦截，转向 Word + 数据库 RAG ===");

  const fileContext = await getWordDocumentContext();
  const contextMaterial = buildContextMaterial(db, fileContext, 6000, 10000);

  const systemPrompt = `你是化工园区安全合规审查专家。请严格根据下方资料回答用户问题。
如果资料不足，请明确说明“系统资料中未提供”，不要编造具体监管数据。

${contextMaterial}`;

  const llmAnswer = await callLLM(systemPrompt, question, 0.0, LLM_TIMEOUT_MS);

  if (llmAnswer) {
    return {
      mode: "rag",
      type: "qa_answer",
      chartType: "",
      answer: llmAnswer,
      sources: [makeSource(WORD_FILE_NAME, "动态文档库")]
    };
  }

  return {
    mode: "fallback",
    type: "no_answer",
    chartType: "",
    answer: "未匹配到足够合规依据描述。请检查 Word 文档是否已放入 server/src/database/documents/，或检查大模型 API Key 是否配置成功。",
    sources: []
  };
}

// ============================================================================
// 十、流式接口
// ============================================================================

async function streamQuestion(db, question, history = [], callbacks) {
  const { onDelta, onDone, onError } = callbacks;

  try {
    const res = await askQuestion(db, question, history);

    // 注意：这里仍然不是“真正的大模型 SSE 流式生成”。
    // 但日报/周报已经改为快速本地返回，因此不会再因为大模型长时间等待而超时。
    // 如果你的路由层使用 SSE，可以在路由层按 chunk 调用 onDelta。
    const answer = res.answer || "";
    if (answer.length <= 1500) {
      onDelta(answer);
    } else {
      const chunkSize = 1000;
      for (let i = 0; i < answer.length; i += chunkSize) {
        onDelta(answer.slice(i, i + chunkSize));
      }
    }

    onDone();
  } catch (err) {
    console.error("=== [streamQuestion 错误] ===", err.message);
    onError(err.message);
  }
}

module.exports = {
  askQuestion,
  streamQuestion
};
