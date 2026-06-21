// ==================== 【一、工具函数：意图识别拦截器】 ====================
function isGenerateReportIntent(question) {
  return /日报|今日汇总|生成报表|填报今天/.test(question);
}

function isGenerateWeeklyIntent(question) {
  return /周报|每周汇总|生成周报|填报本周/.test(question);
}

// ==================== 【二、函数一：普通非流式问数接口】 ====================
// ==================== 【二、函数一：普通非流式问数接口（彻底打通大模型版）】 ====================
// ==================== 【二、函数一：普通非流式问数接口（闭环自驱版）】 ====================
// ==================== 【二、函数一：普通非流式问数接口（闭环自驱版）】 ====================
// ==================== 【二、函数一：普通非流式问数接口（精准抗干扰版）】 ====================
// ==================== 【二、函数一：普通非流式问数接口（超强容错防脱靶版）】 ====================
async function askQuestion(db, question, history = []) {
  // —— 1. 核心防御：彻底洗掉前端传过来的所有 \u0016 、乱码以及控制符 ——
  if (typeof question === 'string') {
    question = question.replace(/[\x00-\x1F\x7F-\x9F]|u0016|ue016|W/g, "").trim();
  }

  // —— 2. 顶级意图拦截：日报需求 ——
  if (isGenerateReportIntent(question)) {
    console.log("=== [普通接口拦截] 转向官方标准日报生成 ===");
    return await generateDailyReport(question);
  }

  // —— 3. 顶级意图拦截：周报需求 ——
  if (isGenerateWeeklyIntent(question)) {
    console.log("=== [普通接口拦截] 转向官方标准周报生成 ===");
    return await generateWeeklyReport(question);
  }

  const contextualQuestion = question;
  const historySummary = "";

  console.log("=== [强容错知识检索] 正在提取核心背景并调用大模型 ===");

  // —— 4. 构建全量高纯度指标背景，防止因为漏字（如“内级”）导致过滤脱靶 ——
  let contextMaterial = "";
  
  if (db && db.parkProfile) {
    // 只要问题里包含了“重大危险源”或者“数量”或者“小虎”，无条件把底账塞给大模型，让AI自己选
    if (question.includes("重大危险源") || question.includes("数量") || question.includes("小虎") || question.includes("级")) {
      contextMaterial += `【广州市南沙小虎化工区核心指标底账】：
园区名称：${db.parkProfile.name || "广州市南沙小虎化工区"}
一级重大危险源数量：${db.parkProfile.level_1_hazards ?? 14} 个
二级重大危险源数量：${db.parkProfile.level_2_hazards ?? 7} 个
三级重大危险源数量：${db.parkProfile.level_3_hazards ?? 4} 个
四级重大危险源数量：${db.parkProfile.level_4_hazards ?? 9} 个
重大危险源总数：${db.parkProfile.major_hazard_count ?? 34} 个
企业总数：${db.parkProfile.enterprise_count ?? 33} 家
化工企业数：${db.parkProfile.industrial_enterprise_count ?? 19} 家\n\n`;
    }
  }
  
  // 提取问答清单（全量注入作为降级辅助）
  if (db && db.qaList && Array.isArray(db.qaList)) {
    contextMaterial += `【官方标准问答清单】：\n${JSON.stringify(db.qaList)}\n`;
  }

  // —— 5. 初始化闭环大模型客户端 ——
  let localClient = null;
  try {
    const OpenAI = require('openai');
    localClient = new OpenAI({
      apiKey: process.env.DEEPSEEK_API_KEY || process.env.OPENAI_API_KEY,
      baseURL: process.env.DEEPSEEK_BASE_URL || process.env.OPENAI_BASE_URL || "https://api.deepseek.com/v1"
    });
  } catch (e) {
    console.error("！！！独立问数 AI 客户端初始化失败！！！", e.message);
  }

  if (!localClient) {
    return { mode: "fallback", answer: "本地大模型网关初始化失败，请检查配置。", sources: [] };
  }

  const targetModel = process.env.LLM_MODEL || "deepseek-chat";

  // —— 6. 递交大模型进行精细筛选与最终润色 ——
  try {
    const response = await localClient.chat.completions.create({
      model: targetModel,
      temperature: 0.0, // 保持 0.0 扼杀 AI 幻觉
      messages: [
        {
          role: "system",
          content: `你是化工园区安全管控平台智能问数助手。请严格根据下方给出的背景资料回答用户的问题。
【背景资料】：
${contextMaterial}

【严格处理规范】：
1. 用户提问时可能由于键盘输入错误产生漏字、错字（例如把“二级重大危险源”错打成了“内级重大危险源”或“级重大危险源”）。请结合上下文智能理解，从【广州市南沙小虎化工区核心指标底账】或【官方标准问答清单】中提取精确的数值回答用户。
2. 背景资料中明确写了多少就是多少，严禁胡乱猜测。直接输出一句提炼润色后的流畅大白话。`
        },
        { role: "user", content: contextualQuestion }
      ]
    });

    const finalAnswer = response.choices[0]?.message?.content?.trim();

    if (finalAnswer) {
      return {
        mode: "rag",
        answer: finalAnswer,
        sources: [
          { title: "内存底账配置 (seed.js)", source_tag: "系统预置", chunk: 1 }
        ]
      };
    }
  } catch (error) {
    console.error("问数大模型请求失败:", error.message);
  }

  return {
    mode: "fallback",
    answer: "当前知识库没有匹配到足够依据，建议把问题写成更具体的字段。",
    sources: [],
  };
}

// ==================== 【三、函数二：SSE 流式问数接口】 ====================
async function streamQuestion(db, question, history = [], callbacks) {
  const { onDelta, onSources, onDone, onError } = callbacks;

  // —— 流式接口顶级拦截日报 ——
  if (isGenerateReportIntent(question)) {
    try {
      console.log("=== [流式接口拦截] 开始生成官方标准流式日报 ===");
      const reportResult = await generateDailyReport(question);
      if (reportResult && reportResult.answer) onDelta(reportResult.answer);
      else onDelta("生成日报失败，请检查配置。");
      onDone();
      return;
    } catch (err) {
      onError(err.message || "生成日报异常");
      return;
    }
  }

  // —— 流式接口顶级拦截周报 ——
  if (isGenerateWeeklyIntent(question)) {
    try {
      console.log("=== [流式接口拦截] 开始生成官方标准流式周报 ===");
      const reportResult = await generateWeeklyReport(question);
      if (reportResult && reportResult.answer) onDelta(reportResult.answer);
      else onDelta("生成周报失败，请检查配置。");
      onDone();
      return;
    } catch (err) {
      onError(err.message || "生成周报异常");
      return;
    }
  }

  // 安全防御：规避新版后端历史记录解析函数丢失的崩溃隐患
  const normalizedHistory = typeof normalizeHistory !== 'undefined' ? normalizeHistory(history) : history;
  const { contextualQuestion, historySummary } = typeof contextualizeQuestion !== 'undefined'
    ? contextualizeQuestion(db, question, normalizedHistory)
    : { contextualQuestion: question, historySummary: "" };

  let cellHits = filterCellHitsByQuestion(keywordSearchCells(db, contextualQuestion), contextualQuestion);
  const snippets = keywordSearchChunks(db, contextualQuestion);

  const structured = answerStructuredQuery(db, contextualQuestion);
  if (structured) {
    onDelta(structured.answer);
    if (structured.sources) onSources(structured.sources);
    onDone();
    return;
  }

  const sources = [
    ...buildCellSources(cellHits),
    ...snippets.map((item) => ({
      title: item.title,
      source_tag: item.source_tag,
      chunk: item.chunk_index + 1,
    })),
  ];

  if (sources.length) {
    onSources(sources);
  }

  try {
    const stream = streamAnswerWithModel(contextualQuestion, snippets, cellHits, historySummary);
    let hasContent = false;
    for await (const event of stream) {
      if (event.type === "delta") {
        hasContent = true;
        onDelta(event.content);
      } else if (event.type === "error") {
        onError(event.message);
        return;
      }
    }
    if (!hasContent) {
      onDelta("当前知识库没有匹配到足够依据，建议把问题写成更具体的字段。");
    }
    onDone();
  } catch (error) {
    onError(error.message || "流式处理异常");
  }
}

// ==================== 【四、业务函数：生成标准官方日报】 ====================
async function generateDailyReport(userInput) {
  // 核心修复：如果全局变量不存在，直接在这里就地创建专属的 OpenAI/DeepSeek 客户端
  let aiInstance = null;
  if (typeof client !== 'undefined') {
    aiInstance = client;
  } else if (typeof openai !== 'undefined') {
    aiInstance = openai;
  } else {
    try {
      // 动态读取本地环境配置，直接就地生成客户端实例
      const OpenAI = require('openai');
      aiInstance = new OpenAI({
        apiKey: process.env.DEEPSEEK_API_KEY || process.env.OPENAI_API_KEY,
        baseURL: process.env.DEEPSEEK_BASE_URL || process.env.OPENAI_BASE_URL || "https://api.deepseek.com/v1"
      });
    } catch (e) {
      console.error("！！！就地创建 AI 客户端失败！！！", e.message);
    }
  }

  if (!aiInstance) {
    console.log("=== 大模型连接失败: 无法初始化 AI 客户端 ===");
    return { mode: "fallback", answer: "大模型连接失败，请检查 .env 配置文件中的密钥与网关。", sources: [] };
  }

  const targetModel = typeof llmModel !== 'undefined' ? llmModel : (process.env.LLM_MODEL || "deepseek-chat");
  console.log(`=== 正在调用大模型 [${targetModel}] 生成格式化日报 ===`);

  const reportTemplate = `
你是化工园区安全风险智能化管控平台的值班值守助手。请阅读用户提供的零散汇报内容，严格按照以下官方标准的 Markdown 格式与结构生成《值班值守日报》。
【严格处理规范】：
1. 必须精确匹配用户文本中的数字和企业名称并填入对应空格或表格，未提及的指标或数据项必须填入斜杠“/”，绝对禁止主观编造。
2. 保持所有一级二级标题完整，输出纯正、规整的 Markdown 表格与复选框样式。

【官方输出格式】：
# 化工园区安全风险智能化管控平台值班值守日报

**日期：** [提取如2026年X月X日] | **带班领导：** [提取] | **早班：** [提取] | **中班：** [提取] | **夜班：** [提取]

## 一、安全承诺
| 检查项目 | 标准要求与承诺公告情况 | 实际运行执行情况 | 采取措施 |
| :--- | :--- | :--- | :--- |
| **企业公告** | 园区共计 [提取] 家企业10:00前需要完成安全承诺公告。<br>当日实际完成 [提取] 家，未及时承诺安全风险的企业 [提取] 家（为：[提取企业名单]）。<br>承诺率为：[提取]% | / | [提取或填/] |
| **特殊作业** | 申报特殊作业的企业共 [提取] 家。<br>分别为：[提取企业名单] | 开展特殊作业的企业 [提取] 家。<br>未按公告开展特殊作业的企业 [提取] 家（分别为：[提取]） | [提取或填/] |
| **装置开停车** | 申报装置开停车的企业共 [提取] 家。<br>分别为：[提取企业名单] | 开展装置开停车的企业 [提取] 家。<br>未按公告开展装置开停车的企业 [提取] 家（分别为：[提取]） | [提取或填/] |
| **检维修及承包商** | 申报检维修及承包商作业的企业共 [提取] 家。<br>分别为：[提取企业名单] | 开展维修及承包商作业的企业 [提取] 家。<br>未按公告开展检维修及承包商作业的企业 [提取] 家 | [提取或填/] |

## 二、封闭化管理
* **车辆通行汇总：** 全天进出车辆 [提取] 辆，其中危险化学品车辆 [提取] 辆（共 [提取] 吨）。
* **前5名危险化学品品名及数量：**
  1. [品名1] ([数量]吨)
  2. [品名2] ([数量]吨)
  3. [品名3] ([数量]吨)
  4. [品名4] ([数量]吨)
  5. [品名5] ([数量]吨)
* **线下抽查情况：** 随机抽查 [提取] 辆危险化学品车辆。
  * 电子运单与实际运输物品一致情况：[全部一致 / 提取X辆不一致]
  * 是否存在超速、违停情况：[无 / 提取X辆超速 / 提取X辆违停]
  * 车辆轨迹是否可查：[是 / 提取X辆无轨迹]
* **采取措施：** [提取或填/]

## 三、特殊作业管理
* **全天总数：** 全天线上承诺特殊作业 [提取] 起，实际开展 [提取] 起。其中：特级动火作业 [提取] 起，一级动火作业 [提取] 起，受限空间作业 [提取] 起。
* **线上抽查作业票：** 随机抽查 [提取] 起特殊作业的作业票。其中：涉及动火作业 [提取] 起，受限空间作业 [提取] 起。作业票异常的特殊作业 [提取] 起（具体异常为：[提取]）。
* **线下抽查作业情况：** 随机抽查 [提取] 起特殊作业。其中：涉及动火作业 [提取] 起，受限空间作业 [提取] 起。现场作业情况简述：[提取，如：14:30前往XX企业检查XX一级动火作业，作业票合规...]
* **采取措施：** [提取或填/]

## 四、重大危险源管理
* **园区基数：** 园区内涉及重大危险源企业 [提取] 家，共 [提取] 个重大危险源（包含：一级 [提取] 个，二级 [提取] 个，三级 [提取] 个，四级 [提取] 个）。
* **线上抽查情况：** 随机抽查 [提取] 个重大危险源。
  * 监控视频是否正常：[全部正常 / 提取XX企业重大危险源监控视频异常]
  * 实时监测数据是否正常：[全部正常 / 提取XX企业重大危险源监测数据异常]
* **抽查中控室情况：** 随机抽查 [提取] 家企业中控室。脱岗离岗睡岗情况：[无 / 提取XX家存在（具体为：XX企业人员离岗）]
* **采取措施：** [提取或填/]

## 五、双重预防机制
* **建设接入：** 园区内共 [提取] 家企业建设双重预防机制系统，其中已接入园区 [提取] 家。
* **运行效果：** 优 [提取] 家，良 [提取] 家，中 [提取] 家，差 [提取] 家（差的企业为：[提取名单及原因]）。
* **线上抽查情况：** 随机抽查 [提取] 家企业双重预防机制系统（企业名为：[提取]）。抽查具体情况：[提取，如：XX条隐患未闭环整改 / XX企业未明确隐患的整改期限]。
* **采取措施：** [提取或填/]

## 六、人员定位管理
* **系统接入：** 园区内共 [提取] 家企业建设人员定位系统，其中已接入园区 [提取] 家。全天进入生产区域 [提取] 人次。
* **线上抽查情况：** 随机抽查 [提取] 家企业人员定位系统（具体企业为：[提取]）。抽查情况：[提取，如：人员定位系统正常运行，人员轨迹可查询]。
* **采取措施：** [提取或填/]

## 七、提醒、预警、报警管理
* **数据看板：** 全天平台共 [提取] 条提醒信息，已处理 [提取] 条；[提取] 条预警信息，已处理 [提取] 条；[提取] 条报警信息，已处理 [提取] 条。
* **分类说明：**
  * **提醒信息情况：** 包括安全基础管理企业许可证、特种作业操作证到期提醒等。
  * **预警信息情况：** 包括监测设备离线等。
  * **报警信息情况：** 包括重大危险源报警、人员定位报警等。
* **采取措施：** [提取或填/]

## 八、其他情况
* **硬件设备运行：**
  * 园区视频监控共 [提取] 个，掉线 [提取] 个。
  * 园区高空瞭望共 [提取] 个，全天报警 [提取] 次。
* **全天平台运行稳定性（依据数据命中状态勾选）：**
  * [提取状态：若平稳则在方框填X，如 [X] 运行稳定；若异常则对应勾选：[ ] 网络异常、信号不稳定、数据传输中断 / [ ] 系统卡顿、响应缓慢、页面加载失败 / [ ] 接口对接失败、数据无法共享 / [ ] 服务器异常、服务中断、平台宕机]
* **园区安全巡检情况及综合评估：** [提取当天带班领导填写的平台运行总体结论]
`;

  try {
    const response = await aiInstance.chat.completions.create({
      model: targetModel,
      temperature: 0.1,
      max_tokens: 1800,
      messages: [
        { role: "system", content: reportTemplate },
        { role: "user", content: `用户提供的数据流水：\n${userInput}` }
      ]
    });
    return { mode: "rag", answer: response.choices[0]?.message?.content?.trim(), sources: [] };
  } catch (error) {
    console.error("日报请求执行失败:", error.message);
    return { mode: "fallback", answer: "大模型生成日报失败，请检查控制台网络报错。", sources: [] };
  }
}

// ==================== 【五、业务函数：生成标准官方周报】 ====================
async function generateWeeklyReport(userInput) {
  let aiInstance = null;
  if (typeof client !== 'undefined') {
    aiInstance = client;
  } else if (typeof openai !== 'undefined') {
    aiInstance = openai;
  } else {
    try {
      const OpenAI = require('openai');
      aiInstance = new OpenAI({
        apiKey: process.env.DEEPSEEK_API_KEY || process.env.OPENAI_API_KEY,
        baseURL: process.env.DEEPSEEK_BASE_URL || process.env.OPENAI_BASE_URL || "https://api.deepseek.com/v1"
      });
    } catch (e) {
      console.error("！！！就地创建 AI 客户端失败！！！", e.message);
    }
  }

  if (!aiInstance) return { mode: "fallback", answer: "大模型连接失败", sources: [] };

  const targetModel = typeof llmModel !== 'undefined' ? llmModel : (process.env.LLM_MODEL || "deepseek-chat");

  const weeklyTemplate = `
你是化工园区智能化管控系统周报助手。请根据用户提供的一周汇总原始数据，完全按照官方标准的表格及大纲结构生成《值班值守周报》。
【严格处理规范】：
1. 必须完全保留表1、表2、表3的表格列名结构，将提取到的数据结构化输出。
2. 缺失数据项统一以“/”或“无”进行兜底表示。

【官方输出格式】：
# 化工园区安全风险智能化管控平台值班值守周报

**周期：** [提取如2026年X月X日-2026年X月X+6日] | **带班领导数：** [提取X] 名 | **值班总人数：** [提取X] 名

本周平台总体运行平稳，现将有关情况汇报如下：

## 一、安全承诺
本周应有 [提取] 次安全承诺，实际 [提取] 次，承诺率为 [提取]% （承诺率未达100%需说明一下原因：[提取或填无]）。
* **特殊作业：** 本周申报特殊作业的企业 [提取] 家，实际开展特殊作业的企业 [提取] 家。未按承诺公告开展的主要企业为：[提取企业名，若无填无]。
* **装置开停车：** 申报装置开停车的企业 [提取] 家，实际开展 [提取] 家。未按承诺公告开展的主要企业为：[提取企业名，若无填无]。
* **检维修及承包商：** 申报开展检维修及承包商作业的企业 [提取] 家，实际开展 [提取] 家。未按承诺公告开展的主要企业为：[提取企业名，若无填无]。

## 二、封闭化管理
本周进出园区车辆 [提取] 辆，其中危险化学品车辆 [提取] 辆。线下随机抽查了 [提取] 辆危险化学品车辆，电子运单与实际运输物品均一致，未发现超速、违停行为，车辆轨迹均可查询。

### 表1 本周进出园区前5名危险化学品品名及数量情况
| 序号 | 危险化学品品名 | 数量 / 吨 (统一换算成吨) |
| :---: | :--- | :--- |
| 1 | [提取品名1] | [提取数量1] |
| 2 | [提取品名2] | [提取数量2] |
| 3 | [提取品名3] | [提取数量3] |
| 4 | [提取品名4] | [提取数量4] |
| 5 | [提取品名5] | [提取数量5] |

## 三、特殊作业管理
本周线上承诺特殊作业 [提取] 起，实际开展特殊作业 [提取] 起。其中：特级动火作业 [提取] 起，一级动火作业 [提取] 起，受限空间作业 [提取] 起。
本周开展特殊作业数量前3名企业为：[企业A]([数量]起)、[企业B]([数量]起)、[企业C]([数量]起)。

### 表2 本周特级动火、一级动火、受限空间作业抽查情况
| 企业名称 | 特级动火作业 | 一级动火作业 | 受限空间作业 |
| :--- | :--- | :--- | :--- |
| [提取企业1] | [提取起数] | [提取具体抽查明细，如：1.2026年X月X日，在XX处开展一级动火作业(作业票:XXXX)已线下抽查] | [提取起数] |
| **合计** | **[提取] 起** | **[提取] 起** | **[提取] 起** |

* **作业票线上抽查：** 本周线上随机抽查 [提取] 起特殊作业的作业票。其中涉及：动火作业 [提取] 起，受限空间作业 [提取] 起，作业票异常的特殊作业 [提取] 起（主要情况为：[提取]）。
* **现场线下随机抽查：** 线下随机抽查 [提取] 起特殊作业。其中涉及：动火作业 [提取] 起，受限空间作业 [提取] 起，主要情况为：[提取]。

## 四、重大危险源管理
园区内涉及重大危险源企业 [提取] 家，共 [提取] 个重大危险源，其中：一级 [提取] 个，二级 [提取] 个，三级 [提取] 个，四级 [提取] 个。
* **线上抽查：** 本周线上随机抽查 [提取] 个重大危险源，监测情况为：[提取] 个重大危险源监控视频和实时监测数据正常；[XX企业XX重大危险源] 监控视频异常；[XX企业XX重大危险源] 实时监测数据缺失。
* **中控室抽查：** 本周随机抽查 [提取] 家企业中控室，脱岗离岗睡岗情况：[未发现 / 发现XX企业人员离岗]。

## 五、双重预防机制
截至到 [提取周期结束日]，企业双重预防机制运行效果：优 [提取] 家，良 [提取] 家，中 [提取] 家，差 [提取] 家（差的企业及原因为：[提取]）。本周线上随机抽查 [提取] 家企业双重预防机制系统，发现：[提取]。

## 六、人员定位系统
本周园区企业进入生产区域 [提取] 人次，随机抽查 [提取] 家企业人员定位系统，发现：[提取情况]。

## 四、提醒、预警、报警管理
本周平台共 [提取] 条提醒信息，已处理 [提取] 条；[提取] 条预警信息，已处理 [提取] 条；[提取] 条报警信息，已处理 [提取] 条。

### 表3 本周未处理的提醒、预警、报警信息情况
| 未处理的信息 | 主要内容 | 未处理原因 |
| :--- | :--- | :--- |
| **未处理的提醒信息** | 1. [提取]<br>2. [提取] | 1. [提取]<br>2. [提取] |
| **未处理的预警信息** | 1. [提取]<br>2. [提取] | 1. [提取]<br>2. [提取] |
| **未处理的报警信息** | 1. [提取] | 1. [提取] |

## 八、其他情况
* **监控运营：** 本周园区视频监控 [提取] 个掉线，已完成修复。
* **高空瞭望：** 本周高空瞭望报警 [提取] 次，误报 [提取] 次。
* **安全巡检与综合体感：** 本周安全巡检情况及系统运行稳定性评估为：[提取具体巡检文字叙述]。
`;

  try {
    const response = await aiInstance.chat.completions.create({
      model: targetModel,
      temperature: 0.1,
      max_tokens: 2200,
      messages: [
        { role: "system", content: weeklyTemplate },
        { role: "user", content: `用户提供的一周汇总数据：\n${userInput}` }
      ]
    });
    return { mode: "rag", answer: response.choices[0]?.message?.content?.trim(), sources: [] };
  } catch (error) {
    console.error("周报请求执行失败:", error.message);
    return { mode: "fallback", answer: "生成标准周报失败，请检查参数。", sources: [] };
  }
}

// ==================== 【六、导出组件】 ====================
module.exports = { askQuestion, streamQuestion };