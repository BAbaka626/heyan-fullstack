const fs = require("fs");
const path = require("path");
const dayjs = require("dayjs");
const { execFileSync } = require("child_process");
const mammoth = require("mammoth");
const XLSX = require("xlsx");
const { clamp, splitIntoChunks, normalizeText } = require("../utils");

function safeReadText(filePath) {
  const ext = path.extname(filePath).toLowerCase();

  if (ext === ".docx") {
    try {
      return mammoth.extractRawText({ path: filePath }).value;
    } catch (_error) {
      return "";
    }
  }

  if (ext === ".doc") {
    try {
      return execFileSync("antiword", [filePath], {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "ignore"],
      });
    } catch (_error) {
      return "";
    }
  }

  try {
    return execFileSync("textutil", ["-convert", "txt", "-stdout", filePath], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    });
  } catch (_error) {
    return "";
  }
}

function createKnowledgeDoc(db, title, filePath, docType, sourceTag, content) {
  const normalized = normalizeText(content) || title;
  return db
    .prepare(`
      INSERT INTO knowledge_docs (title, file_path, doc_type, source_tag, content)
      VALUES (?, ?, ?, ?, ?)
    `)
    .run(title, filePath, docType, sourceTag, normalized).lastInsertRowid;
}

function insertKnowledgeDoc(db, title, filePath, docType, sourceTag, content) {
  const docId = createKnowledgeDoc(db, title, filePath, docType, sourceTag, content);
  const chunkInsert = db.prepare(`
    INSERT INTO knowledge_chunks (doc_id, chunk_index, content)
    VALUES (?, ?, ?)
  `);

  splitIntoChunks(content || title).forEach((chunk, index) => {
    chunkInsert.run(docId, index, chunk);
  });

  return docId;
}

function buildMergeLookup(sheet) {
  const lookup = new Map();
  (sheet["!merges"] || []).forEach((merge) => {
    const topLeft = XLSX.utils.encode_cell(merge.s);
    for (let r = merge.s.r; r <= merge.e.r; r += 1) {
      for (let c = merge.s.c; c <= merge.e.c; c += 1) {
        lookup.set(`${r}:${c}`, topLeft);
      }
    }
  });
  return lookup;
}

function getDisplayedCellValue(sheet, mergeLookup, rowIndex, colIndex) {
  const directRef = XLSX.utils.encode_cell({ r: rowIndex, c: colIndex });
  const direct = normalizeText(sheet[directRef]?.v);
  if (direct) {
    return direct;
  }

  const mergedRef = mergeLookup.get(`${rowIndex}:${colIndex}`);
  if (!mergedRef) {
    return "";
  }

  return normalizeText(sheet[mergedRef]?.v);
}

function isMergedShadowCell(sheet, mergeLookup, rowIndex, colIndex) {
  const mergedRef = mergeLookup.get(`${rowIndex}:${colIndex}`);
  if (!mergedRef) {
    return false;
  }

  const directRef = XLSX.utils.encode_cell({ r: rowIndex, c: colIndex });
  return mergedRef !== directRef;
}

function extractSheetMatrix(sheet) {
  const range = XLSX.utils.decode_range(sheet["!ref"] || "A1:A1");
  const mergeLookup = buildMergeLookup(sheet);
  const matrix = [];

  for (let rowIndex = range.s.r; rowIndex <= range.e.r; rowIndex += 1) {
    const row = [];
    for (let colIndex = range.s.c; colIndex <= range.e.c; colIndex += 1) {
      row.push(getDisplayedCellValue(sheet, mergeLookup, rowIndex, colIndex));
    }
    matrix.push(row);
  }

  return { matrix, range, mergeLookup };
}

function looksLikeSectionTitle(value) {
  return /^[一二三四五六七八九十]+[、.．]/.test(value)
    || /^[（(]?[一二三四五六七八九十]+[）)]/.test(value)
    || (/表$/.test(value) && value.length >= 5);
}

function findSectionTitle(matrix, rowIndex) {
  for (let current = rowIndex; current >= Math.max(0, rowIndex - 15); current -= 1) {
    const values = Array.from(new Set(matrix[current].filter(Boolean)));
    if (!values.length) {
      continue;
    }

    const joined = values.join(" ").trim();
    if (values.length === 1 && looksLikeSectionTitle(joined)) {
      return joined;
    }
  }

  return "";
}

function isMeaningfulHeader(value) {
  return Boolean(value)
    && !/^(序号|备注|示例)$/.test(value)
    && !/^\d+$/.test(value);
}

function findRowHeader(row, colIndex) {
  for (let current = colIndex - 1; current >= 0; current -= 1) {
    const value = normalizeText(row[current]);
    if (isMeaningfulHeader(value)) {
      return value;
    }
  }

  for (let current = colIndex - 1; current >= 0; current -= 1) {
    const value = normalizeText(row[current]);
    if (value) {
      return value;
    }
  }

  return "";
}

function findColumnHeader(matrix, rowIndex, colIndex) {
  for (let current = rowIndex - 1; current >= Math.max(0, rowIndex - 12); current -= 1) {
    const value = normalizeText(matrix[current][colIndex]);
    if (isMeaningfulHeader(value)) {
      return value;
    }
  }

  for (let current = rowIndex - 1; current >= Math.max(0, rowIndex - 12); current -= 1) {
    const value = normalizeText(matrix[current][colIndex]);
    if (value) {
      return value;
    }
  }

  return "";
}

function buildWorkbookSummary(entries) {
  return entries
    .slice(0, 400)
    .map((entry) => entry.searchText)
    .join("\n");
}

function importWorkbookCellKnowledge(db, filePath, title, sourceTag) {
  if (!fs.existsSync(filePath)) {
    return;
  }

  const workbook = XLSX.readFile(filePath);
  const cellInsert = db.prepare(`
    INSERT INTO knowledge_cells (
      doc_id, title, file_path, source_tag, sheet_name, section_title, row_index, col_index,
      cell_ref, row_header, column_header, value, is_blank, search_text
    ) VALUES (
      @doc_id, @title, @file_path, @source_tag, @sheet_name, @section_title, @row_index, @col_index,
      @cell_ref, @row_header, @column_header, @value, @is_blank, @search_text
    )
  `);

  const entries = [];

  workbook.SheetNames.forEach((sheetName) => {
    const sheet = workbook.Sheets[sheetName];
    const { matrix, range, mergeLookup } = extractSheetMatrix(sheet);

    for (let rowIndex = range.s.r; rowIndex <= range.e.r; rowIndex += 1) {
      const row = matrix[rowIndex - range.s.r];
      for (let colIndex = range.s.c; colIndex <= range.e.c; colIndex += 1) {
        if (isMergedShadowCell(sheet, mergeLookup, rowIndex, colIndex)) {
          continue;
        }

        const value = normalizeText(row[colIndex - range.s.c]);
        const sectionTitle = findSectionTitle(matrix, rowIndex - range.s.r);
        const rowHeader = findRowHeader(row, colIndex - range.s.c);
        const columnHeader = findColumnHeader(matrix, rowIndex - range.s.r, colIndex - range.s.c);
        const cellRef = XLSX.utils.encode_cell({ r: rowIndex, c: colIndex });
        const searchText = normalizeText([
          `文档 ${title}`,
          `工作表 ${sheetName}`,
          sectionTitle ? `分区 ${sectionTitle}` : "",
          rowHeader ? `行标题 ${rowHeader}` : "",
          columnHeader ? `列标题 ${columnHeader}` : "",
          `单元格 ${cellRef}`,
          value ? `内容 ${value}` : "内容 空白待填",
        ].filter(Boolean).join("；"));

        if (!value && !rowHeader && !columnHeader && !sectionTitle) {
          continue;
        }

        entries.push({
          doc_id: 0,
          title,
          file_path: filePath,
          source_tag: sourceTag,
          sheet_name: sheetName,
          section_title: sectionTitle,
          row_index: rowIndex + 1,
          col_index: colIndex + 1,
          cell_ref: cellRef,
          row_header: rowHeader,
          column_header: columnHeader,
          value,
          is_blank: value ? 0 : 1,
          search_text: searchText,
          searchText,
        });
      }
    }
  });

  const docId = createKnowledgeDoc(
    db,
    title,
    filePath,
    ".xlsx",
    sourceTag,
    buildWorkbookSummary(entries)
  );

  const chunkInsert = db.prepare(`
    INSERT INTO knowledge_chunks (doc_id, chunk_index, content)
    VALUES (?, ?, ?)
  `);
  splitIntoChunks(buildWorkbookSummary(entries), 320).forEach((chunk, index) => {
    chunkInsert.run(docId, index, chunk);
  });

  entries.forEach((entry) => {
    cellInsert.run({
      ...entry,
      doc_id: docId,
    });
  });
}

function importDocuments(db, rootDir) {
  const files = [
    "4月16日会议记录.docx",
    "化工园区AI智能问数实施方案.docx",
    "双重预防机制数字化系统运行效果评估模型.docx",
    "重大危险风险预警模型的算法说明.doc",
    "（3月10日第2稿）化工园区平台运行工作周报.docx",
  ];

  files.forEach((name) => {
    const filePath = path.resolve(rootDir, name);
    if (!fs.existsSync(filePath)) {
      return;
    }

    insertKnowledgeDoc(db, path.basename(name), filePath, path.extname(name), "业务文档", safeReadText(filePath));
  });
}

function importSurveyWorkbookAsKnowledge(db, rootDir) {
  [
    { file: "广东省化工园区安全风险与管理能力普查表.xlsx", title: "广东省化工园区安全风险与管理能力普查表", tag: "普查表模板" },
    { file: "广东省化工园区安全风险与管理能力普查表（2026版）.xlsx", title: "广东省化工园区安全风险与管理能力普查表（2026版）", tag: "普查表模板" },
  ].forEach(({ file, title, tag }) => {
    const filePath = path.resolve(rootDir, file);
    importWorkbookCellKnowledge(db, filePath, title, tag);
  });
}

function createRiskLevel(score) {
  if (score >= 85) return "高风险";
  if (score >= 70) return "较高风险";
  if (score >= 55) return "中风险";
  return "低风险";
}

function hashCode(value) {
  return String(value || "")
    .split("")
    .reduce((total, char) => total + char.charCodeAt(0), 0);
}

function seedEnterpriseDailyMetrics(db) {
  const enterprises = db.prepare(`
    SELECT id, name, code, social_credit_code, employee_count, hazard_level, evaluation_level
    FROM enterprises
    ORDER BY name
    LIMIT 200
  `).all();

  const insert = db.prepare(`
    INSERT INTO enterprise_daily_metrics (
      enterprise_id, enterprise_code, enterprise_name, stat_date, risk_score, risk_level,
      online_personnel, active_alarm_count, open_issue_count, inspection_count, activity_index, source
    ) VALUES (
      @enterprise_id, @enterprise_code, @enterprise_name, @stat_date, @risk_score, @risk_level,
      @online_personnel, @active_alarm_count, @open_issue_count, @inspection_count, @activity_index, @source
    )
  `);

  const hazardCountStmt = db.prepare(`
    SELECT COUNT(*) AS count
    FROM major_hazards
    WHERE enterprise_code IN (?, ?)
  `);
  const personnelCountStmt = db.prepare(`
    SELECT COUNT(*) AS count
    FROM personnel
    WHERE enterprise_code IN (?, ?)
  `);
  const issueCountStmt = db.prepare(`
    SELECT COUNT(*) AS count
    FROM issues
    WHERE enterprise_name = ?
      AND status != '已完成'
  `);
  const inspectionCountStmt = db.prepare(`
    SELECT COUNT(*) AS count
    FROM inspection_records
    WHERE organization_name LIKE ?
       OR route_name LIKE ?
  `);

  enterprises.forEach((enterprise, index) => {
    const enterpriseCode = enterprise.code || enterprise.social_credit_code || enterprise.id;
    const hazardCount = hazardCountStmt.get(enterpriseCode, enterprise.social_credit_code || enterpriseCode)?.count || 0;
    const personnelCount = personnelCountStmt.get(enterpriseCode, enterprise.social_credit_code || enterpriseCode)?.count || 0;
    const openIssueCount = issueCountStmt.get(enterprise.name)?.count || 0;
    const inspectionBase = inspectionCountStmt.get(`%${enterprise.name}%`, `%${enterprise.name}%`)?.count || 0;
    const baseSeed = hashCode(`${enterpriseCode}-${index}`);
    const employeeBase = Math.max(Number(enterprise.employee_count || 0), personnelCount, 6);

    for (let offset = 6; offset >= 0; offset -= 1) {
      const statDate = dayjs("2026-05-11").subtract(offset, "day").format("YYYY-MM-DD");
      const swing = ((baseSeed + offset * 17) % 9) - 4;
      const activeAlarmCount = Math.max(0, hazardCount + Math.floor((baseSeed + offset) % 3) - 1);
      const inspectionCount = Math.max(1, inspectionBase + ((baseSeed + offset * 3) % 2));
      const onlinePersonnel = clamp(
        Math.floor(employeeBase * (0.45 + ((baseSeed + offset * 11) % 35) / 100)),
        Math.min(employeeBase, 3),
        employeeBase
      );
      const activityIndex = clamp(52 + inspectionCount * 8 + onlinePersonnel * 0.6 - openIssueCount * 6 + swing * 2, 0, 100);
      const riskScore = clamp(
        38 + hazardCount * 8 + activeAlarmCount * 7 + openIssueCount * 10 - inspectionCount * 3 - swing,
        0,
        100
      );

      insert.run({
        enterprise_id: enterprise.id,
        enterprise_code: enterpriseCode,
        enterprise_name: enterprise.name,
        stat_date: statDate,
        risk_score: riskScore,
        risk_level: createRiskLevel(riskScore),
        online_personnel: onlinePersonnel,
        active_alarm_count: activeAlarmCount,
        open_issue_count: openIssueCount,
        inspection_count: inspectionCount,
        activity_index: activityIndex,
        source: "seeded-enterprise-daily-metrics",
      });
    }
  });
}

function importEnterpriseWorkbook(db, rootDir) {
  const filePath = path.resolve(rootDir, "一企一档表结构.xlsx");
  if (!fs.existsSync(filePath)) {
    return;
  }

  const workbook = XLSX.readFile(filePath);
  const getRows = (sheetName) => XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], { raw: false, defval: "" });

  const enterpriseInsert = db.prepare(`
    INSERT OR REPLACE INTO enterprises (
      id, name, code, social_credit_code, park_name, address, legal_person, safety_leader,
      business_scope, employee_count, hazard_level, evaluation_level, qyfxdj, is_chemical_enterprise,
      is_run_prevention, is_run_mechanism, source_sheet
    ) VALUES (
      @id, @name, @code, @social_credit_code, @park_name, @address, @legal_person, @safety_leader,
      @business_scope, @employee_count, @hazard_level, @evaluation_level, @qyfxdj, @is_chemical_enterprise,
      @is_run_prevention, @is_run_mechanism, @source_sheet
    )
  `);
  getRows("企业基本信息").forEach((row) => {
    enterpriseInsert.run({
      id: row.ID || row.id || `${row.QYBM || row.QYMC}-enterprise`,
      name: row.QYMC || "",
      code: row.QYBM || "",
      social_credit_code: row.TYSHXYDM || "",
      park_name: row.SSHGYQMC || "",
      address: row.SCJYDZ || row.SZS2 || "",
      legal_person: row.FDDBR || "",
      safety_leader: row.AQFZR || "",
      business_scope: row.JYFW || row.YYZZJJFW || "",
      employee_count: Number(row.CYRYSL || row.PEOPLE_EMPLOYEE || 0) || 0,
      hazard_level: row.HAZARD_LEVEL || "",
      evaluation_level: row.EVALUATION_LEVEL || "",
      qyfxdj: Number(row.QYFXDJ || row.qyfxdj || 0) || null,
      is_chemical_enterprise: Number(row.is_chemical_enterprise || 0) || 0,
      is_run_prevention: Number(row.is_run_prevention || 0) || 0,
      is_run_mechanism: Number(row.is_run_mechanism || 0) || 0,
      source_sheet: "企业基本信息",
    });
  });

  const personnelInsert = db.prepare(`
    INSERT OR REPLACE INTO personnel (
      id, enterprise_code, person_name, position_name, mobile_number, affiliated_department, is_security_supervise
    ) VALUES (
      @id, @enterprise_code, @person_name, @position_name, @mobile_number, @affiliated_department, @is_security_supervise
    )
  `);
  getRows("人员表").forEach((row) => {
    personnelInsert.run({
      id: row.id || `${row.person_name}-${row.job_code}`,
      enterprise_code: row._from || "",
      person_name: row.person_name || "",
      position_name: row.position_name || "",
      mobile_number: row.mobile_number || "",
      affiliated_department: row.affiliated_department || "",
      is_security_supervise: Number(row.is_security_supervise || 0) || 0,
    });
  });

  const chemicalsInsert = db.prepare(`
    INSERT OR REPLACE INTO chemicals (
      id, enterprise_code, name, alias, cas, annual_output, is_hazardous
    ) VALUES (
      @id, @enterprise_code, @name, @alias, @cas, @annual_output, @is_hazardous
    )
  `);
  getRows("化学品").forEach((row) => {
    chemicalsInsert.run({
      id: row.ID || "",
      enterprise_code: row._from || "",
      name: row.WLMC || row.XM || "",
      alias: row.BM || "",
      cas: row.CASH || "",
      annual_output: row.CPSCNL || "",
      is_hazardous: Number(row.SFWXHXP || 0) || 0,
    });
  });

  const hazardInsert = db.prepare(`
    INSERT OR REPLACE INTO major_hazards (
      id, enterprise_code, hazard_code, hazard_name, level, park_name, responsible,
      responsible_phone, technical, technical_phone, operation, operation_phone, status
    ) VALUES (
      @id, @enterprise_code, @hazard_code, @hazard_name, @level, @park_name, @responsible,
      @responsible_phone, @technical, @technical_phone, @operation, @operation_phone, @status
    )
  `);
  getRows("重大危险源").forEach((row) => {
    hazardInsert.run({
      id: row.ID || "",
      enterprise_code: row._from || "",
      hazard_code: row.ZDWXYBM || "",
      hazard_name: row.ZDWXYMC || row.WXYJC || "",
      level: row.WXYDJ || row.ZDWXYZGDJ || "",
      park_name: row.SSHGYQMC || "",
      responsible: row.RESPONSIBLE || "",
      responsible_phone: row.RESPONSIBLE_PHONE || "",
      technical: row.TECHNICAL || "",
      technical_phone: row.TECHNICAL_PHONE || "",
      operation: row.OPERATION || "",
      operation_phone: row.OPERATION_PHONE || "",
      status: row.status || "",
    });
  });

  const riskUnitInsert = db.prepare(`
    INSERT OR REPLACE INTO risk_units (
      id, enterprise_code, hazard_code, risk_class, risk_unit_name, liable_person
    ) VALUES (
      @id, @enterprise_code, @hazard_code, @risk_class, @risk_unit_name, @liable_person
    )
  `);
  getRows("风险分析单元").forEach((row) => {
    riskUnitInsert.run({
      id: row.ID || "",
      enterprise_code: row.COMPANY_CODE || row._from || "",
      hazard_code: row.HAZARD_CODE || "",
      risk_class: row.RISK_CLASS || "",
      risk_unit_name: row.RISK_UNIT_NAME || "",
      liable_person: row.HAZARD_LIABLE_PERSON || "",
    });
  });

  const riskMeasureInsert = db.prepare(`
    INSERT OR REPLACE INTO risk_measures (
      id, enterprise_code, risk_event_id, measure_desc, troubleshoot_content, classify1, classify2, classify3
    ) VALUES (
      @id, @enterprise_code, @risk_event_id, @measure_desc, @troubleshoot_content, @classify1, @classify2, @classify3
    )
  `);
  getRows("风险管控措施").forEach((row) => {
    riskMeasureInsert.run({
      id: row.ID || "",
      enterprise_code: row.COMPANY_CODE || row._from || "",
      risk_event_id: row.RISK_EVENT_ID || "",
      measure_desc: row.RISK_MEASURE_DESC || "",
      troubleshoot_content: row.TROUBLESHOOT_CONTENT || "",
      classify1: row.CLASSIFY1 || "",
      classify2: row.CLASSIFY2 || "",
      classify3: row.CLASSIFY3 || "",
    });
  });

  importWorkbookCellKnowledge(db, filePath, "一企一档表结构", "一企一档");
  seedEnterpriseDailyMetrics(db);
}

function importAcceptanceWorkbook(db, rootDir) {
  const filePath = path.resolve(rootDir, "重点化工产业聚集区重大安全风险防控项目技.xlsx");
  importWorkbookCellKnowledge(db, filePath, "重点化工产业聚集区重大安全风险防控项目技术验收评分表", "评分表");
}

module.exports = {
  importAcceptanceWorkbook,
  importDocuments,
  importEnterpriseWorkbook,
  importSurveyWorkbookAsKnowledge,
};
