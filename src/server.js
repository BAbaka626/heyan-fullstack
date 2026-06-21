const express = require("express");
const cors = require("cors");
const dayjs = require("dayjs");
const { port } = require("./config");
const { createDb } = require("./db");
const { askQuestion, streamQuestion } = require("./services/qa");
const { generateMonthlyReport, generateWeeklyReport } = require("./services/report");
const { computeMonthlyScore, computePreventionScore, evaluateWarningLevel, gradeForScore } = require("./services/rules");
const {
  createInspectionRecord,
  createIssue,
  createWorkRecord,
  getEnterpriseDetail,
  getEnterpriseRiskProfile,
  getKnowledgeDoc,
  listEnterprises,
  listInspectionRecords,
  listInspectionRoutes,
  listIssues,
  listKnowledgeDocs,
  listWorkRecords,
} = require("./services/store");

const app = express();
const db = createDb();

app.use(cors());
app.use(express.json());

// Request logging middleware
app.use((req, res, next) => {
  const start = Date.now();
  const bodyPreview = req.body && typeof req.body === 'object'
    ? JSON.stringify(req.body).slice(0, 500)
    : String(req.body).slice(0, 100);
  console.log(`[REQ] ${req.method} ${req.url} | IP: ${req.ip || req.socket?.remoteAddress || '-'} | Body: ${bodyPreview}`);
  res.on('finish', () => {
    console.log(`[RES] ${req.method} ${req.url} | Status: ${res.statusCode} | Time: ${Date.now() - start}ms`);
  });
  next();
});

app.get("/api/health", (_req, res) => {
  res.json({ ok: true, time: dayjs().format("YYYY-MM-DD HH:mm:ss") });
});

app.get("/api/dashboard", (_req, res) => {
  const park = db.prepare(`SELECT * FROM parks LIMIT 1`).get();
  const enterprises = db.prepare(`SELECT COUNT(*) AS count FROM enterprises`).get();
  const hazards = db.prepare(`SELECT COUNT(*) AS count FROM major_hazards`).get();
  const latestScore = db.prepare(`SELECT * FROM monthly_scores ORDER BY month DESC LIMIT 1`).get();
  const access = db.prepare(`SELECT * FROM access_stats ORDER BY month DESC LIMIT 1`).get();

  res.json({
    park,
    summary: {
      enterpriseCount: enterprises.count,
      majorHazardCount: Math.max(hazards.count || 0, park.major_hazard_count || 0),
      monthlyScore: latestScore,
      access,
    },
  });
});

app.get("/api/warnings", (_req, res) => {
  const rows = db.prepare(`SELECT * FROM warning_events ORDER BY occurred_at DESC`).all();
  res.json({ items: rows });
});

app.get("/api/enterprises", (req, res) => {
  res.json({ items: listEnterprises(db, req.query.q || "") });
});

app.get("/api/enterprises/:id", (req, res) => {
  const detail = getEnterpriseDetail(db, req.params.id);
  if (!detail) {
    res.status(404).json({ error: "enterprise not found" });
    return;
  }
  res.json(detail);
});

app.get("/api/enterprises/:id/risk-profile", (req, res) => {
  const profile = getEnterpriseRiskProfile(db, req.params.id);
  if (!profile) {
    res.status(404).json({ error: "enterprise not found" });
    return;
  }
  res.json(profile);
});

app.get("/api/knowledge/docs", (_req, res) => {
  res.json({ items: listKnowledgeDocs(db) });
});

app.get("/api/knowledge/docs/:id", (req, res) => {
  const doc = getKnowledgeDoc(db, Number(req.params.id));
  if (!doc) {
    res.status(404).json({ error: "doc not found" });
    return;
  }
  res.json(doc);
});

app.post("/api/warnings/evaluate", (req, res) => {
  const input = req.body || {};
  res.json({
    level: evaluateWarningLevel(input),
    metrics: {
      disturbanceRate: Number(input.average_disturbance_rate || 0),
      repeatAlarmCount: Number(input.repeat_alarm_count || 0),
      avgCloseMinutes: Number(input.avg_close_minutes || 0),
      timelyRate: Number(input.timely_rate || 0),
    },
  });
});

app.get("/api/scores/monthly", (_req, res) => {
  res.json({
    items: db.prepare(`SELECT * FROM monthly_scores ORDER BY month DESC`).all(),
  });
});

app.post("/api/scores/monthly/compute", (req, res) => {
  const input = req.body || {};
  const finalScore = computeMonthlyScore({
    previousScore: input.previousScore,
    accessCount: input.accessCount,
    bonusScore: input.bonusScore,
    penaltyScore: input.penaltyScore,
  });

  res.json({
    finalScore,
    grade: gradeForScore(finalScore),
  });
});

app.post("/api/scores/prevention", (req, res) => {
  res.json(computePreventionScore(req.body || {}));
});

app.post("/api/qa/ask", async (req, res) => {
  const question = String(req.body?.question || "").trim();
  const history = Array.isArray(req.body?.history) ? req.body.history : [];
  if (!question) {
    res.status(400).json({ error: "question is required" });
    return;
  }

  try {
    const result = await askQuestion(db, question, history);
    res.json(result);
  } catch (error) {
    console.error(`[ERROR] /api/qa/ask failed:`, error);
    const status = error.status || 500;
    let message = error.message || "问答服务异常";
    if (status === 401) message = "AI 服务密钥无效，请联系管理员检查配置";
    else if (status === 402) message = "AI 服务余额不足，请联系管理员充值";
    else if (status === 429) message = "AI 服务请求过于频繁，请稍后再试";
    else if (status >= 500 && status < 600) message = "AI 服务商暂时不可用，请稍后再试";
    res.status(status >= 400 && status < 600 ? status : 500).json({ error: message });
  }
});

app.post("/api/qa/chat", async (req, res) => {
  const question = String(req.body?.question || "").trim();
  const history = Array.isArray(req.body?.history) ? req.body.history : [];
  if (!question) {
    res.status(400).json({ error: "question is required" });
    return;
  }

  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");
  res.setHeader("X-Accel-Buffering", "no");
  if (res.socket) {
    res.socket.setNoDelay(true);
    res.socket.setKeepAlive(true);
  }

  const send = (data) => {
    res.write(`data: ${JSON.stringify(data)}\n\n`);
    if (typeof res.flush === "function") {
      res.flush();
    }
  };

  try {
    await streamQuestion(db, question, history, {
      onDelta: (content) => send({ type: "delta", content }),
      onSources: (sources) => send({ type: "sources", sources }),
      onDone: () => {
        send({ type: "done" });
        res.end();
      },
      onError: (message) => {
        send({ type: "error", message });
        res.end();
      },
    });
  } catch (error) {
    console.error(`[ERROR] /api/qa/chat failed:`, error);
    send({ type: "error", message: error.message || "问答服务异常" });
    res.end();
  }
});

app.get("/api/inspection/routes", (_req, res) => {
  res.json({ items: listInspectionRoutes(db) });
});

app.get("/api/inspection/records", (_req, res) => {
  res.json({ items: listInspectionRecords(db) });
});

app.post("/api/inspection/records", (req, res) => {
  res.status(201).json(createInspectionRecord(db, req.body || {}));
});

app.get("/api/issues", (_req, res) => {
  res.json({ items: listIssues(db) });
});

app.post("/api/issues", (req, res) => {
  res.status(201).json(createIssue(db, req.body || {}));
});

app.get("/api/work-records", (_req, res) => {
  res.json({ items: listWorkRecords(db) });
});

app.post("/api/work-records", (req, res) => {
  res.status(201).json(createWorkRecord(db, req.body || {}));
});

app.get("/api/reports/weekly", (_req, res) => {
  res.json({ content: generateWeeklyReport(db) });
});

app.get("/api/reports/monthly", (req, res) => {
  res.json({ content: generateMonthlyReport(db, req.query.month || "") });
});

// Global error handler
app.use((err, req, res, _next) => {
  console.error(`[ERROR] Unhandled error on ${req.method} ${req.url}:`, err);
  res.status(500).json({ error: err.message || "Internal Server Error", stack: err.stack });
});

app.listen(port, () => {
  console.log(`chemical-ai-server listening on ${port}`);
});
