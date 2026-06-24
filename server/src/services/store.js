const dayjs = require("dayjs");
const { enrichEnterprisesRisk, evaluateEnterpriseRisk } = require("./riskEvaluation");

function createId(prefix) {
  return `${prefix}-${dayjs().format("YYYYMMDDHHmmss")}-${Math.random().toString(36).slice(2, 8)}`;
}

function resolveEnterpriseCode(enterprise) {
  return enterprise.code || enterprise.social_credit_code || enterprise.id;
}

function listEnterprises(db, query = "") {
  const keyword = `%${String(query || "").trim()}%`;
  const rows = db.prepare(`
    SELECT *
    FROM enterprises
    WHERE (? = '%%')
       OR name LIKE ?
       OR park_name LIKE ?
       OR social_credit_code LIKE ?
       OR code LIKE ?
       OR legal_person LIKE ?
       OR safety_leader LIKE ?
    ORDER BY name
    LIMIT 100
  `).all(keyword, keyword, keyword, keyword, keyword, keyword, keyword);
  return enrichEnterprisesRisk(rows);
}

function getEnterpriseDetail(db, id) {
  const rawEnterprise = db.prepare(`SELECT * FROM enterprises WHERE id = ?`).get(id);
  const enterprise = rawEnterprise ? evaluateEnterpriseRisk(rawEnterprise) : null;
  if (!enterprise) {
    return null;
  }

  const enterpriseCode = resolveEnterpriseCode(enterprise);
  return {
    enterprise,
    personnel: db.prepare(`
      SELECT *
      FROM personnel
      WHERE enterprise_code IN (?, ?)
      ORDER BY person_name
      LIMIT 50
    `).all(enterpriseCode, enterprise.social_credit_code),
    chemicals: db.prepare(`
      SELECT *
      FROM chemicals
      WHERE enterprise_code IN (?, ?)
      ORDER BY name
      LIMIT 50
    `).all(enterpriseCode, enterprise.social_credit_code),
    majorHazards: db.prepare(`
      SELECT *
      FROM major_hazards
      WHERE enterprise_code IN (?, ?)
      ORDER BY hazard_name
      LIMIT 50
    `).all(enterpriseCode, enterprise.social_credit_code),
    riskUnits: db.prepare(`
      SELECT *
      FROM risk_units
      WHERE enterprise_code IN (?, ?)
      ORDER BY risk_unit_name
      LIMIT 50
    `).all(enterpriseCode, enterprise.social_credit_code),
    riskMeasures: db.prepare(`
      SELECT *
      FROM risk_measures
      WHERE enterprise_code IN (?, ?)
      ORDER BY classify1, classify2, classify3
      LIMIT 100
    `).all(enterpriseCode, enterprise.social_credit_code),
  };
}

function getEnterpriseRiskProfile(db, id) {
  const detail = getEnterpriseDetail(db, id);
  if (!detail) {
    return null;
  }

  const { enterprise, personnel, majorHazards } = detail;
  const enterpriseCode = resolveEnterpriseCode(enterprise);
  const latestMetric = db.prepare(`
    SELECT *
    FROM enterprise_daily_metrics
    WHERE enterprise_id = ?
    ORDER BY stat_date DESC
    LIMIT 1
  `).get(id);
  const trend = db.prepare(`
    SELECT stat_date, risk_score, risk_level, online_personnel, active_alarm_count, open_issue_count, inspection_count, activity_index
    FROM enterprise_daily_metrics
    WHERE enterprise_id = ?
    ORDER BY stat_date
  `).all(id);
  const openIssues = db.prepare(`
    SELECT COUNT(*) AS count
    FROM issues
    WHERE enterprise_name = ?
      AND status != '已完成'
  `).get(enterprise.name)?.count || 0;
  const inspectionCount = db.prepare(`
    SELECT COUNT(*) AS count
    FROM inspection_records
    WHERE organization_name LIKE ?
       OR route_name LIKE ?
  `).get(`%${enterprise.name}%`, `%${enterprise.name}%`)?.count || 0;

  return {
    enterprise,
    summary: {
      riskScore: latestMetric?.risk_score || 0,
      riskLevel: enterprise.computedRiskLabel || latestMetric?.risk_level || "待评估",
      onlinePersonnel: latestMetric?.online_personnel || personnel.length || 0,
      activeAlarmCount: latestMetric?.active_alarm_count || 0,
      openIssueCount: latestMetric?.open_issue_count || openIssues,
      inspectionCount: latestMetric?.inspection_count || inspectionCount,
      activityIndex: latestMetric?.activity_index || 0,
      majorHazardCount: majorHazards.length,
      employeeCount: enterprise.employee_count || 0,
      safetyLeader: enterprise.safety_leader || "",
      legalPerson: enterprise.legal_person || "",
      parkName: enterprise.park_name || "",
      hazardLevel: enterprise.hazard_level || "",
      evaluationLevel: enterprise.evaluation_level || "",
      socialCreditCode: enterprise.social_credit_code || "",
      code: enterpriseCode,
    },
    charts: {
      riskTrend: trend.map((item) => ({
        date: item.stat_date,
        score: item.risk_score,
        level: item.risk_level,
      })),
      activityTrend: trend.map((item) => ({
        date: item.stat_date,
        onlinePersonnel: item.online_personnel,
        inspections: item.inspection_count,
        activityIndex: item.activity_index,
      })),
      issueAlarmTrend: trend.map((item) => ({
        date: item.stat_date,
        alarms: item.active_alarm_count,
        issues: item.open_issue_count,
      })),
    },
    personnelHighlights: personnel.slice(0, 8),
    majorHazards: majorHazards.slice(0, 10),
  };
}

function listKnowledgeDocs(db) {
  return db.prepare(`
    SELECT id, title, file_path, doc_type, source_tag, LENGTH(content) AS content_length
    FROM knowledge_docs
    ORDER BY id DESC
  `).all();
}

function getKnowledgeDoc(db, id) {
  const doc = db.prepare(`
    SELECT id, title, file_path, doc_type, source_tag, content
    FROM knowledge_docs
    WHERE id = ?
  `).get(id);
  if (!doc) {
    return null;
  }

  const chunks = db.prepare(`
    SELECT chunk_index, content
    FROM knowledge_chunks
    WHERE doc_id = ?
    ORDER BY chunk_index
  `).all(id);

  return { ...doc, chunks };
}

function listInspectionRoutes(db) {
  return db.prepare(`SELECT * FROM inspection_routes ORDER BY route_type, route_name`).all();
}

function listInspectionRecords(db) {
  return db.prepare(`SELECT * FROM inspection_records ORDER BY inspected_at DESC`).all();
}

function createInspectionRecord(db, payload) {
  const record = {
    id: createId("inspect"),
    route_id: payload.routeId || null,
    route_name: payload.routeName || "",
    inspector_name: payload.inspectorName || "",
    organization_name: payload.organizationName || "",
    checkin_address: payload.checkinAddress || "",
    latitude: payload.latitude || "",
    longitude: payload.longitude || "",
    voice_text: payload.voiceText || "",
    status: payload.status || "已完成",
    issue_count: Number(payload.issueCount || 0),
    inspected_at: payload.inspectedAt || dayjs().format("YYYY-MM-DD HH:mm:ss"),
  };

  db.prepare(`
    INSERT INTO inspection_records (
      id, route_id, route_name, inspector_name, organization_name, checkin_address, latitude, longitude,
      voice_text, status, issue_count, inspected_at
    ) VALUES (
      @id, @route_id, @route_name, @inspector_name, @organization_name, @checkin_address, @latitude, @longitude,
      @voice_text, @status, @issue_count, @inspected_at
    )
  `).run(record);

  return record;
}

function listIssues(db) {
  return db.prepare(`SELECT * FROM issues ORDER BY created_at DESC`).all();
}

function createIssue(db, payload) {
  const issue = {
    id: createId("issue"),
    source_type: payload.sourceType || "巡检问题",
    related_record_id: payload.relatedRecordId || null,
    enterprise_name: payload.enterpriseName || "",
    issue_title: payload.issueTitle || "",
    issue_level: payload.issueLevel || "一般",
    description: payload.description || "",
    rectify_requirement: payload.rectifyRequirement || "",
    rectify_deadline: payload.rectifyDeadline || "",
    status: payload.status || "待整改",
    created_at: payload.createdAt || dayjs().format("YYYY-MM-DD HH:mm:ss"),
  };

  db.prepare(`
    INSERT INTO issues (
      id, source_type, related_record_id, enterprise_name, issue_title, issue_level, description,
      rectify_requirement, rectify_deadline, status, created_at
    ) VALUES (
      @id, @source_type, @related_record_id, @enterprise_name, @issue_title, @issue_level, @description,
      @rectify_requirement, @rectify_deadline, @status, @created_at
    )
  `).run(issue);

  return issue;
}

function listWorkRecords(db) {
  return db.prepare(`SELECT * FROM work_records ORDER BY created_at DESC`).all();
}

function createWorkRecord(db, payload) {
  const work = {
    id: createId("work"),
    record_type: payload.recordType || "工作记录",
    title: payload.title || "",
    organization_name: payload.organizationName || "",
    description: payload.description || "",
    created_by: payload.createdBy || "系统用户",
    created_at: payload.createdAt || dayjs().format("YYYY-MM-DD HH:mm:ss"),
  };

  db.prepare(`
    INSERT INTO work_records (
      id, record_type, title, organization_name, description, created_by, created_at
    ) VALUES (
      @id, @record_type, @title, @organization_name, @description, @created_by, @created_at
    )
  `).run(work);

  return work;
}

module.exports = {
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
};
