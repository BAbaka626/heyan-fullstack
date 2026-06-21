const OpenAI = require("openai");
const { llmApiKey, llmBaseUrl, llmModel } = require("../config");
const { normalizeText } = require("../utils");
const { getEnterpriseRiskProfile } = require("./store");

function buildSourceLabel(source) {
  if (source.cell_ref) {
    return `${source.title}/${source.sheet_name}/${source.cell_ref}`;
  }
  return `${source.title}${source.chunk_index !== undefined ? `#${source.chunk_index + 1}` : ""}`;
}

function maybeCreateClient() {
  if (!llmApiKey) {
    return null;
  }

  return new OpenAI({
    apiKey: llmApiKey,
    baseURL: llmBaseUrl,
  });
}

function normalizeHistory(history) {
  if (!Array.isArray(history)) {
    return [];
  }

  return history
    .map((item) => ({
      question: normalizeText(item?.question),
      answer: normalizeText(item?.answer),
      sources: Array.isArray(item?.sources) ? item.sources : [],
    }))
    .filter((item) => item.question || item.answer || item.sources.length)
    .slice(-6);
}

function extractTerms(question) {
  const clean = normalizeText(question);
  const segments = clean.split(/[，。！？、；：,.!\s/|]+/).filter((item) => item.length >= 2);
  const grams = [];

  segments.forEach((segment) => {
    if (/[\u4e00-\u9fa5]/.test(segment) && segment.length <= 16) {
      for (let size = 2; size <= Math.min(4, segment.length); size += 1) {
        for (let index = 0; index <= segment.length - size; index += 1) {
          grams.push(segment.slice(index, index + size));
        }
      }
    }
  });

  return Array.from(new Set([...segments, ...grams])).sort((a, b) => b.length - a.length);
}

function scoreText(text, terms, weight) {
  const content = normalizeText(text);
  if (!content) {
    return 0;
  }

  return terms.reduce((total, term) => total + (content.includes(term) ? weight : 0), 0);
}

function findEnterpriseByQuestion(db, question) {
  const normalizedQuestion = normalizeText(question);
  const enterprises = db.prepare(`SELECT * FROM enterprises LIMIT 500`).all();

  return enterprises
    .map((item) => {
      let score = 0;
      if (item.name && normalizedQuestion.includes(item.name)) score += 20;
      if (item.code && normalizedQuestion.includes(item.code)) score += 12;
      if (item.social_credit_code && normalizedQuestion.includes(item.social_credit_code)) score += 12;
      if (item.safety_leader && normalizedQuestion.includes(item.safety_leader)) score += 4;
      return { item, score };
    })
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score)
    .map((entry) => entry.item)[0];
}

function findEnterpriseFromHistory(db, history) {
  for (let index = history.length - 1; index >= 0; index -= 1) {
    const item = history[index];
    const combined = `${item.question} ${item.answer}`.trim();
    const enterprise = findEnterpriseByQuestion(db, combined);
    if (enterprise) {
      return enterprise;
    }
  }

  return null;
}

function latestCellSourceFromHistory(history) {
  for (let index = history.length - 1; index >= 0; index -= 1) {
    const source = history[index].sources.find((item) => item?.cell_ref || item?.sheet_name);
    if (source) {
      return source;
    }
  }

  return null;
}

function latestDocSourceFromHistory(history) {
  for (let index = history.length - 1; index >= 0; index -= 1) {
    const source = history[index].sources[0];
    if (source) {
      return source;
    }
  }

  return null;
}

function isFollowUpQuestion(question) {
  const normalized = normalizeText(question);
  return normalized.length <= 18
    || /这个|那个|这家|那家|它|该企业|该公司|这个格子|那个格子|这一项|那一项|继续|还有|呢|再看|再说|另外/.test(normalized);
}

function needsEnterpriseContext(question) {
  return /在线|风险|状态|活性|人员|重大危险源|报警|打分|画像|情况|这个企业|这家|那家|它|该企业|该公司/.test(question);
}

function needsCellContext(question) {
  return /格子|单元格|字段|表头|这一项|那一项|这一列|那一列|这个表|那个表|怎么填|填什么|在哪/.test(question);
}

function hasWorkbookContext(question) {
  return /普查表|调查表|工作表|sheet|excel|xlsx|模板|文档/.test(question);
}

function contextualizeQuestion(db, question, history) {
  const normalizedQuestion = normalizeText(question);
  if (!normalizedQuestion || !history.length) {
    return { contextualQuestion: normalizedQuestion, historySummary: "" };
  }

  const additions = [];
  const currentEnterprise = findEnterpriseByQuestion(db, normalizedQuestion);
  const followUp = isFollowUpQuestion(normalizedQuestion);

  if (!currentEnterprise && followUp && needsEnterpriseContext(normalizedQuestion)) {
    const historyEnterprise = findEnterpriseFromHistory(db, history);
    if (historyEnterprise) {
      additions.push(historyEnterprise.name);
    }
  }

  if (followUp && needsCellContext(normalizedQuestion) && !hasWorkbookContext(normalizedQuestion)) {
    const source = latestCellSourceFromHistory(history) || latestDocSourceFromHistory(history);
    if (source) {
      additions.push([
        source.title,
        source.sheet_name || "",
        source.section_title || "",
        source.column_header || "",
        source.row_header || "",
      ].filter(Boolean).join(" "));
    }
  }

  const contextualQuestion = normalizeText([...new Set(additions.filter(Boolean)), normalizedQuestion].join(" "));
  const historySummary = history
    .slice(-3)
    .map((item, index) => `上文${index + 1}：问"${item.question}" 答"${item.answer}"`)
    .join("\n");

  return { contextualQuestion, historySummary };
}

function keywordSearchCells(db, question) {
  const terms = extractTerms(question);
  if (!terms.length) {
    return [];
  }

  const rows = db.prepare(`
    SELECT id, doc_id, title, source_tag, sheet_name, section_title, row_index, col_index, cell_ref,
           row_header, column_header, value, is_blank, search_text
    FROM knowledge_cells
  `).all();

  return rows
    .map((row) => {
      let score = 0;
      score += scoreText(row.value, terms, row.is_blank ? 0 : 8);
      score += scoreText(row.column_header, terms, 7);
      score += scoreText(row.row_header, terms, 6);
      score += scoreText(row.section_title, terms, 5);
      score += scoreText(row.sheet_name, terms, 4);
      score += scoreText(row.title, terms, 4);
      score += scoreText(row.search_text, terms, 1);
      if (row.value && normalizeText(question).includes(row.value)) {
        score += 18;
      }
      return { ...row, score };
    })
    .filter((row) => row.score > 0)
    .sort((a, b) => b.score - a.score || a.row_index - b.row_index || a.col_index - b.col_index)
    .slice(0, 8);
}

function keywordSearchChunks(db, question) {
  const keywords = extractTerms(question);
  if (!keywords.length) {
    return [];
  }

  const rows = db.prepare(`
    SELECT kc.id, kc.chunk_index, kc.content, kd.title, kd.source_tag
    FROM knowledge_chunks kc
    JOIN knowledge_docs kd ON kd.id = kc.doc_id
  `).all();

  return rows
    .map((row) => ({
      ...row,
      score: scoreText(row.content, keywords, 2) + scoreText(row.title, keywords, 4),
    }))
    .filter((row) => row.score > 0)
    .sort((a, b) => b.score - a.score || a.chunk_index - b.chunk_index)
    .slice(0, 5);
}

function formatCellHit(hit) {
  const parts = [
    hit.title,
    hit.sheet_name ? `工作表 ${hit.sheet_name}` : "",
    hit.section_title ? `分区 ${hit.section_title}` : "",
    hit.row_header ? `行标题 ${hit.row_header}` : "",
    hit.column_header ? `列标题 ${hit.column_header}` : "",
    `单元格 ${hit.cell_ref}`,
    hit.value ? `内容 ${hit.value}` : "该格当前为空，属于待填项",
  ].filter(Boolean);

  return parts.join("，");
}

function buildCellSources(hits) {
  return hits.map((item) => ({
    title: item.title,
    source_tag: item.source_tag,
    sheet_name: item.sheet_name,
    section_title: item.section_title,
    row_header: item.row_header,
    column_header: item.column_header,
    cell_ref: item.cell_ref,
    excerpt: item.value || "空白待填",
  }));
}

function buildRiskWarning(profile) {
  if (!profile?.summary) {
    return null;
  }

  if (profile.summary.activeAlarmCount <= 0 && profile.summary.openIssueCount <= 0) {
    return null;
  }

  return {
    title: `${profile.enterprise.name} 风险提醒`,
    level: profile.summary.riskLevel,
    content: `当前活跃报警 ${profile.summary.activeAlarmCount} 项，待闭环问题 ${profile.summary.openIssueCount} 项。`,
  };
}

function isCellIntent(question) {
  return /表格|单元格|格子|字段|表头|excel|xlsx|模板|普查表|调查表/.test(question);
}

function filterCellHitsByQuestion(cellHits, question) {
  let result = cellHits.slice();
  if (/2026/.test(question)) {
    const matched = result.filter((item) => /2026/.test(item.title));
    if (matched.length) {
      result = matched;
    }
  }

  if (/普查表|调查表/.test(question)) {
    const matched = result.filter((item) => item.source_tag === "普查表模板");
    if (matched.length) {
      result = matched;
    }
  }

  return result;
}

function buildCellIntentAnswer(cellHits) {
  const top = cellHits[0];
  const tail = top?.value
    ? `字段标题在 ${top.cell_ref}${top.is_blank ? "" : `，当前命中的内容为"${top.value}"`}`
    : `待填格位于 ${top.cell_ref}`;
  return `已定位到最相关表格字段：${top.title}，工作表 ${top.sheet_name}，分区 ${top.section_title || "未识别"}，${tail}。`;
}

function answerEnterpriseRiskQuery(db, enterprise, question) {
  if (!enterprise) {
    return null;
  }

  if (!/状态|风险|在线|动态|活性|双重预防|重大危险源|打分|画像|情况/.test(question)) {
    return null;
  }

  const profile = getEnterpriseRiskProfile(db, enterprise.id);
  if (!profile) {
    return null;
  }

  return {
    mode: "structured",
    answer: `${enterprise.name} 当前风险评分 ${profile.summary.riskScore} 分，风险等级 ${profile.summary.riskLevel}，在线人员 ${profile.summary.onlinePersonnel} 人，重大危险源 ${profile.summary.majorHazardCount} 个，在途问题 ${profile.summary.openIssueCount} 项，近 7 日活性指数 ${profile.summary.activityIndex}。`,
    sources: [{ title: "企业风险画像", source_tag: "企业动态画像" }],
    cards: [
      {
        type: "enterprise_risk_profile",
        title: `${enterprise.name} 风险画像`,
        data: profile,
      },
    ],
    charts: [
      { type: "line", title: "近7日风险评分", data: profile.charts.riskTrend },
      { type: "bar", title: "近7日活性变化", data: profile.charts.activityTrend },
      { type: "bar", title: "报警与问题走势", data: profile.charts.issueAlarmTrend },
    ],
    warningReminder: buildRiskWarning(profile),
  };
}

function answerStructuredQuery(db, question) {
  const park = db.prepare(`SELECT * FROM parks LIMIT 1`).get();
  const latestScore = db.prepare(`SELECT * FROM monthly_scores ORDER BY month DESC LIMIT 1`).get();
  const warnings = db.prepare(`
    SELECT level, COUNT(*) AS count
    FROM warning_events
    GROUP BY level
  `).all();

  const enterprise = findEnterpriseByQuestion(db, question);
  const enterpriseRiskAnswer = answerEnterpriseRiskQuery(db, enterprise, question);
  if (enterpriseRiskAnswer) {
    return enterpriseRiskAnswer;
  }

  if (/产值|企业数|面积|危险源|化工区/.test(question)) {
    return {
      mode: "structured",
      answer: `${park.name}，2025年产值 ${park.output_2025} 亿元，认定面积 ${park.approved_area} km2，建成面积 ${park.built_area} km2，企业总数 ${park.enterprise_count} 家，重大危险源 ${park.major_hazard_count} 个。`,
      sources: [{ title: "园区统计样例", source_tag: "园区基础画像" }],
    };
  }

  if (/(月度评分|平台评分|平台打分|运行效果)/.test(question) && !/双重预防机制/.test(question) && latestScore) {
    return {
      mode: "structured",
      answer: `${latestScore.month} 平台月度评分为 ${latestScore.final_score} 分，等级 ${latestScore.grade}。计算方式：上月基础分 ${latestScore.previous_score} + 访问量加分 + 专项加分 ${latestScore.bonus_score} - 扣分 ${latestScore.penalty_score}。`,
      sources: [{ title: "月度评分结果", source_tag: "平台评分" }],
      charts: [{
        type: "bar",
        title: "月度评分构成",
        data: [
          { name: "上月基础分", value: latestScore.previous_score },
          { name: "专项加分", value: latestScore.bonus_score },
          { name: "专项扣分", value: latestScore.penalty_score },
          { name: "最终得分", value: latestScore.final_score },
        ],
      }],
    };
  }

  if (/(预警|报警|风险提醒|告警|重大危险源监测)/.test(question) && warnings.length) {
    const summary = warnings.map((item) => `${item.level}${item.count}条`).join("，");
    return {
      mode: "structured",
      answer: `当前预警统计为：${summary}。其中重大危险源监测预警按扰动率、重复报警、销警时长和及时率联合判定。`,
      sources: [{ title: "重大危险风险预警模型的算法说明.doc", source_tag: "预警规则" }],
      warningReminder: {
        title: "风险提醒",
        level: warnings[0]?.level || "预警",
        content: `当前仍需关注：${summary}。`,
      },
    };
  }

  if (/双重预防机制/.test(question) && /评分|公式|计算/.test(question)) {
    return {
      mode: "structured",
      answer:
        "双重预防机制运行效果总分公式为 E=(A+B)×α。A 为排查任务完成情况，按已完成任务数/计划完成任务数×50，再扣包保责任履职不到位分；B 为隐患治理情况，按已验收隐患数/应整改隐患数×50，再扣隐患过少分；存在否决项时 α=0，否则 α=1。",
      sources: [{ title: "双重预防机制数字化系统运行效果评估模型.docx", source_tag: "评分公式" }],
    };
  }

  if (enterprise) {
    const hazardCount = db.prepare(`
      SELECT COUNT(*) AS count
      FROM major_hazards
      WHERE enterprise_code IN (?, ?)
    `).get(enterprise.code || "", enterprise.social_credit_code || "");
    return {
      mode: "structured",
      answer: `${enterprise.name}，统一社会信用代码 ${enterprise.social_credit_code || "未录入"}，所属园区 ${enterprise.park_name || "未录入"}，员工数 ${enterprise.employee_count || 0}，企业风险等级 ${enterprise.hazard_level || "未录入"}，重大危险源 ${hazardCount?.count || 0} 个。`,
      sources: [{ title: "一企一档表结构.xlsx", source_tag: "企业基础信息" }],
    };
  }

  if (/巡检|路线|打卡/.test(question)) {
    const routes = db.prepare(`SELECT route_name, frequency FROM inspection_routes ORDER BY route_name LIMIT 5`).all();
    return {
      mode: "structured",
      answer: `当前已配置巡检路线 ${routes.length} 条：${routes.map((item) => `${item.route_name}（${item.frequency}）`).join("，")}。`,
      sources: [{ title: "巡检路线配置", source_tag: "巡检管理" }],
    };
  }

  if (/问题整改|隐患|问题单/.test(question)) {
    const stats = db.prepare(`
      SELECT status, COUNT(*) AS count
      FROM issues
      GROUP BY status
      ORDER BY count DESC
    `).all();
    return {
      mode: "structured",
      answer: `当前问题整改统计：${stats.map((item) => `${item.status}${item.count}条`).join("，") || "暂无问题单"}。`,
      sources: [{ title: "问题登记台账", source_tag: "巡检问题" }],
    };
  }

  return null;
}

function buildPrompt(question, snippets, cellHits, historySummary) {
  const cellContext = cellHits
    .map((item, index) => `[C${index + 1}] ${formatCellHit(item)}`)
    .join("\n");
  const chunkContext = snippets
    .map((item, index) => `[D${index + 1}] ${item.title} ${item.source_tag || ""}\n${item.content}`)
    .join("\n\n");

  const parts = [
    `这是用户原文：${question}`,
    "",
    "这是根据用户原文在系统找到的相关信息：",
    cellContext || "无单元格命中",
    chunkContext || "无文档片段",
  ];

  if (historySummary) {
    parts.push("", `历史对话摘要：\n${historySummary}`);
  }

  parts.push("", "请回复用户");

  return {
    system: "你是化工园区安全智能问数助手。优先引用具体 Excel 单元格、字段名、工作表名和企业动态画像回答，不要把整段原文照搬出来。若命中的是空白待填单元格，要明确说明这是模板中的待填字段。必须基于给定资料作答；若依据不足，明确回答\"资料不足，建议查看原文\"。风险提醒不要省略。",
    user: parts.join("\n"),
  };
}

async function answerWithModel(question, snippets, cellHits, historySummary = "") {
  const client = maybeCreateClient();
  if (!client || (!snippets.length && !cellHits.length)) {
    return null;
  }

  const prompt = buildPrompt(question, snippets, cellHits, historySummary);

  const response = await client.chat.completions.create({
    model: llmModel,
    temperature: 0.1,
    messages: [
      { role: "system", content: prompt.system },
      { role: "user", content: prompt.user },
    ],
  });

  return response.choices[0]?.message?.content?.trim() || null;
}

async function* streamAnswerWithModel(question, snippets, cellHits, historySummary = "") {
  const client = maybeCreateClient();
  if (!client) {
    yield { type: "error", message: "AI 服务未配置" };
    return;
  }
  if (!snippets.length && !cellHits.length) {
    yield { type: "error", message: "本地知识库未找到相关内容" };
    return;
  }

  const prompt = buildPrompt(question, snippets, cellHits, historySummary);

  try {
    const stream = await client.chat.completions.create({
      model: llmModel,
      temperature: 0.1,
      stream: true,
      messages: [
        { role: "system", content: prompt.system },
        { role: "user", content: prompt.user },
      ],
    });

    for await (const chunk of stream) {
      const content = chunk.choices[0]?.delta?.content || "";
      if (content) {
        yield { type: "delta", content };
      }
    }
  } catch (error) {
    yield { type: "error", message: error.message || "AI 流式请求失败" };
  }
}

async function askQuestion(db, question, history = []) {
  const normalizedHistory = normalizeHistory(history);
  const { contextualQuestion, historySummary } = contextualizeQuestion(db, question, normalizedHistory);

  let cellHits = filterCellHitsByQuestion(keywordSearchCells(db, contextualQuestion), contextualQuestion);
  if (isCellIntent(contextualQuestion) && cellHits.length) {
    return {
      mode: "cell",
      answer: `${buildCellIntentAnswer(cellHits)} 其他相关格子：${cellHits.slice(1, 4).map((item) => formatCellHit(item)).join("，") || "无"}`,
      sources: buildCellSources(cellHits),
    };
  }

  const structured = answerStructuredQuery(db, contextualQuestion);
  if (structured) {
    return structured;
  }

  const snippets = keywordSearchChunks(db, contextualQuestion);
  const modelAnswer = await answerWithModel(contextualQuestion, snippets, cellHits, historySummary);

  if (modelAnswer) {
    return {
      mode: "rag",
      answer: modelAnswer,
      sources: [
        ...buildCellSources(cellHits),
        ...snippets.map((item) => ({
          title: item.title,
          source_tag: item.source_tag,
          chunk: item.chunk_index + 1,
        })),
      ],
    };
  }

  if (cellHits.length) {
    return {
      mode: "retrieval",
      answer: `已定位到 ${cellHits.length} 个最相关表格格子：${cellHits.slice(0, 4).map((item) => formatCellHit(item)).join("，")}。`,
      sources: buildCellSources(cellHits),
    };
  }

  if (snippets.length) {
    return {
      mode: "retrieval",
      answer: `已检索到 ${snippets.length} 条相关依据。优先建议查看这些原文片段：${snippets.map((item) => buildSourceLabel(item)).join("、")}。`,
      sources: snippets.map((item) => ({
        title: item.title,
        source_tag: item.source_tag,
        chunk: item.chunk_index + 1,
        excerpt: item.content.slice(0, 120),
      })),
    };
  }

  return {
    mode: "fallback",
    answer: "当前知识库没有匹配到足够依据，建议把问题写成更具体的字段、工作表、企业名称、重大危险源、评分或预警问题。",
    sources: [],
  };
}

async function streamQuestion(db, question, history = [], callbacks) {
  const { onDelta, onSources, onDone, onError } = callbacks;
  const normalizedHistory = normalizeHistory(history);
  const { contextualQuestion, historySummary } = contextualizeQuestion(db, question, normalizedHistory);

  let cellHits = filterCellHitsByQuestion(keywordSearchCells(db, contextualQuestion), contextualQuestion);

  // Cell intent: direct answer
  if (isCellIntent(contextualQuestion) && cellHits.length) {
    onDelta(`${buildCellIntentAnswer(cellHits)} 其他相关格子：${cellHits.slice(1, 4).map((item) => formatCellHit(item)).join("，") || "无"}`);
    onSources(buildCellSources(cellHits));
    onDone();
    return;
  }

  // Structured query: direct answer
  const structured = answerStructuredQuery(db, contextualQuestion);
  if (structured) {
    onDelta(structured.answer);
    if (structured.sources) onSources(structured.sources);
    onDone();
    return;
  }

  const snippets = keywordSearchChunks(db, contextualQuestion);
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

  // Stream from AI model
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
      // No AI content, fallback to retrieval
      if (cellHits.length) {
        onDelta(`已定位到 ${cellHits.length} 个最相关表格格子：${cellHits.slice(0, 4).map((item) => formatCellHit(item)).join("，")}。`);
      } else if (snippets.length) {
        onDelta(`已检索到 ${snippets.length} 条相关依据。优先建议查看这些原文片段：${snippets.map((item) => buildSourceLabel(item)).join("、")}。`);
      } else {
        onDelta("当前知识库没有匹配到足够依据，建议把问题写成更具体的字段、工作表、企业名称、重大危险源、评分或预警问题。");
      }
    }
    onDone();
  } catch (error) {
    onError(error.message || "流式处理异常");
  }
}

module.exports = { askQuestion, streamQuestion };
