const {
  monthlyScoreBase,
  parkProfile,
  accessStats,
  warningEvents,
  inspectionRoutes,
  inspectionRecords,
  issues,
  workRecords,
} = require("./data/seed");
const { computeMonthlyScore, gradeForScore, evaluateWarningLevel } = require("./services/rules");

function initSchema(db) {
  db.exec(`
    DROP TABLE IF EXISTS work_records;
    DROP TABLE IF EXISTS issues;
    DROP TABLE IF EXISTS inspection_records;
    DROP TABLE IF EXISTS inspection_routes;
    DROP TABLE IF EXISTS enterprise_daily_metrics;
    DROP TABLE IF EXISTS knowledge_cells;
    DROP TABLE IF EXISTS knowledge_chunks;
    DROP TABLE IF EXISTS knowledge_docs;
    DROP TABLE IF EXISTS monthly_scores;
    DROP TABLE IF EXISTS access_stats;
    DROP TABLE IF EXISTS warning_events;
    DROP TABLE IF EXISTS risk_measures;
    DROP TABLE IF EXISTS risk_units;
    DROP TABLE IF EXISTS major_hazards;
    DROP TABLE IF EXISTS chemicals;
    DROP TABLE IF EXISTS personnel;
    DROP TABLE IF EXISTS enterprises;
    DROP TABLE IF EXISTS parks;

    CREATE TABLE parks (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      city TEXT,
      management_org TEXT,
      safety_org TEXT,
      output_2024 REAL,
      output_2025 REAL,
      approved_area REAL,
      built_area REAL,
      enterprise_count INTEGER,
      industrial_enterprise_count INTEGER,
      hazardous_production_enterprises INTEGER,
      hazardous_usage_enterprises INTEGER,
      key_supervision_processes INTEGER,
      key_supervision_chemicals INTEGER,
      major_hazard_count INTEGER,
      level_1_hazards INTEGER,
      level_2_hazards INTEGER,
      level_3_hazards INTEGER,
      level_4_hazards INTEGER
    );

    CREATE TABLE enterprises (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      code TEXT,
      social_credit_code TEXT,
      park_name TEXT,
      address TEXT,
      legal_person TEXT,
      safety_leader TEXT,
      business_scope TEXT,
      employee_count INTEGER,
      hazard_level TEXT,
      evaluation_level TEXT,
      is_chemical_enterprise INTEGER,
      is_run_prevention INTEGER,
      is_run_mechanism INTEGER,
      source_sheet TEXT
    );

    CREATE TABLE personnel (
      id TEXT PRIMARY KEY,
      enterprise_code TEXT,
      person_name TEXT,
      position_name TEXT,
      mobile_number TEXT,
      affiliated_department TEXT,
      is_security_supervise INTEGER
    );

    CREATE TABLE chemicals (
      id TEXT PRIMARY KEY,
      enterprise_code TEXT,
      name TEXT,
      alias TEXT,
      cas TEXT,
      annual_output TEXT,
      is_hazardous INTEGER
    );

    CREATE TABLE major_hazards (
      id TEXT PRIMARY KEY,
      enterprise_code TEXT,
      hazard_code TEXT,
      hazard_name TEXT,
      level TEXT,
      park_name TEXT,
      responsible TEXT,
      responsible_phone TEXT,
      technical TEXT,
      technical_phone TEXT,
      operation TEXT,
      operation_phone TEXT,
      status TEXT
    );

    CREATE TABLE risk_units (
      id TEXT PRIMARY KEY,
      enterprise_code TEXT,
      hazard_code TEXT,
      risk_class TEXT,
      risk_unit_name TEXT,
      liable_person TEXT
    );

    CREATE TABLE risk_measures (
      id TEXT PRIMARY KEY,
      enterprise_code TEXT,
      risk_event_id TEXT,
      measure_desc TEXT,
      troubleshoot_content TEXT,
      classify1 TEXT,
      classify2 TEXT,
      classify3 TEXT
    );

    CREATE TABLE warning_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      park_name TEXT,
      enterprise_name TEXT,
      category TEXT,
      parameter_type TEXT,
      point_count INTEGER,
      alarm_count INTEGER,
      repeat_alarm_count INTEGER,
      avg_close_minutes REAL,
      timely_rate REAL,
      average_disturbance_rate REAL,
      level TEXT,
      status TEXT,
      occurred_at TEXT,
      source TEXT
    );

    CREATE TABLE access_stats (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      park_name TEXT,
      month TEXT,
      access_count INTEGER,
      report_count INTEGER,
      active_enterprises INTEGER
    );

    CREATE TABLE monthly_scores (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      park_name TEXT,
      month TEXT,
      previous_score REAL,
      access_count INTEGER,
      bonus_score REAL,
      penalty_score REAL,
      final_score REAL,
      grade TEXT,
      reason TEXT
    );

    CREATE TABLE knowledge_docs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      title TEXT,
      file_path TEXT,
      doc_type TEXT,
      source_tag TEXT,
      content TEXT
    );

    CREATE TABLE knowledge_chunks (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      doc_id INTEGER,
      chunk_index INTEGER,
      content TEXT,
      FOREIGN KEY (doc_id) REFERENCES knowledge_docs(id)
    );

    CREATE TABLE knowledge_cells (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      doc_id INTEGER,
      title TEXT,
      file_path TEXT,
      source_tag TEXT,
      sheet_name TEXT,
      section_title TEXT,
      row_index INTEGER,
      col_index INTEGER,
      cell_ref TEXT,
      row_header TEXT,
      column_header TEXT,
      value TEXT,
      is_blank INTEGER,
      search_text TEXT,
      FOREIGN KEY (doc_id) REFERENCES knowledge_docs(id)
    );

    CREATE TABLE inspection_routes (
      id TEXT PRIMARY KEY,
      route_name TEXT NOT NULL,
      route_type TEXT,
      organization_name TEXT,
      frequency TEXT,
      point_count INTEGER,
      description TEXT
    );

    CREATE TABLE inspection_records (
      id TEXT PRIMARY KEY,
      route_id TEXT,
      route_name TEXT,
      inspector_name TEXT,
      organization_name TEXT,
      checkin_address TEXT,
      latitude TEXT,
      longitude TEXT,
      voice_text TEXT,
      status TEXT,
      issue_count INTEGER,
      inspected_at TEXT,
      FOREIGN KEY (route_id) REFERENCES inspection_routes(id)
    );

    CREATE TABLE issues (
      id TEXT PRIMARY KEY,
      source_type TEXT,
      related_record_id TEXT,
      enterprise_name TEXT,
      issue_title TEXT,
      issue_level TEXT,
      description TEXT,
      rectify_requirement TEXT,
      rectify_deadline TEXT,
      status TEXT,
      created_at TEXT
    );

    CREATE TABLE work_records (
      id TEXT PRIMARY KEY,
      record_type TEXT,
      title TEXT,
      organization_name TEXT,
      description TEXT,
      created_by TEXT,
      created_at TEXT
    );

    CREATE TABLE enterprise_daily_metrics (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      enterprise_id TEXT,
      enterprise_code TEXT,
      enterprise_name TEXT,
      stat_date TEXT,
      risk_score REAL,
      risk_level TEXT,
      online_personnel INTEGER,
      active_alarm_count INTEGER,
      open_issue_count INTEGER,
      inspection_count INTEGER,
      activity_index REAL,
      source TEXT
    );
  `);

  db.prepare(`
    INSERT INTO parks (
      id, name, city, management_org, safety_org, output_2024, output_2025, approved_area,
      built_area, enterprise_count, industrial_enterprise_count, hazardous_production_enterprises,
      hazardous_usage_enterprises, key_supervision_processes, key_supervision_chemicals,
      major_hazard_count, level_1_hazards, level_2_hazards, level_3_hazards, level_4_hazards
    ) VALUES (
      @id, @name, @city, @management_org, @safety_org, @output_2024, @output_2025, @approved_area,
      @built_area, @enterprise_count, @industrial_enterprise_count, @hazardous_production_enterprises,
      @hazardous_usage_enterprises, @key_supervision_processes, @key_supervision_chemicals,
      @major_hazard_count, @level_1_hazards, @level_2_hazards, @level_3_hazards, @level_4_hazards
    )
  `).run(parkProfile);

  const warningInsert = db.prepare(`
    INSERT INTO warning_events (
      park_name, enterprise_name, category, parameter_type, point_count, alarm_count,
      repeat_alarm_count, avg_close_minutes, timely_rate, average_disturbance_rate,
      level, status, occurred_at, source
    ) VALUES (
      @park_name, @enterprise_name, @category, @parameter_type, @point_count, @alarm_count,
      @repeat_alarm_count, @avg_close_minutes, @timely_rate, @average_disturbance_rate,
      @level, @status, @occurred_at, @source
    )
  `);
  warningEvents.forEach((item) => warningInsert.run({ ...item, level: evaluateWarningLevel(item) }));

  const accessInsert = db.prepare(`
    INSERT INTO access_stats (park_name, month, access_count, report_count, active_enterprises)
    VALUES (@park_name, @month, @access_count, @report_count, @active_enterprises)
  `);
  accessStats.forEach((item) => accessInsert.run(item));

  const currentAccess = accessStats.find((item) => item.month === monthlyScoreBase.month);
  const finalScore = computeMonthlyScore({
    previousScore: monthlyScoreBase.previous_score,
    accessCount: currentAccess ? currentAccess.access_count : 0,
    bonusScore: monthlyScoreBase.bonus_score,
    penaltyScore: monthlyScoreBase.penalty_score,
  });

  db.prepare(`
    INSERT INTO monthly_scores (
      park_name, month, previous_score, access_count, bonus_score, penalty_score, final_score, grade, reason
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    monthlyScoreBase.park_name,
    monthlyScoreBase.month,
    monthlyScoreBase.previous_score,
    currentAccess ? currentAccess.access_count : 0,
    monthlyScoreBase.bonus_score,
    monthlyScoreBase.penalty_score,
    finalScore,
    gradeForScore(finalScore),
    "按上月基础分叠加访问量加分、专项加分与扣分后得出本月评分"
  );

  const routeInsert = db.prepare(`
    INSERT INTO inspection_routes (
      id, route_name, route_type, organization_name, frequency, point_count, description
    ) VALUES (
      @id, @route_name, @route_type, @organization_name, @frequency, @point_count, @description
    )
  `);
  inspectionRoutes.forEach((item) => routeInsert.run(item));

  const inspectionInsert = db.prepare(`
    INSERT INTO inspection_records (
      id, route_id, route_name, inspector_name, organization_name, checkin_address, latitude, longitude,
      voice_text, status, issue_count, inspected_at
    ) VALUES (
      @id, @route_id, @route_name, @inspector_name, @organization_name, @checkin_address, @latitude, @longitude,
      @voice_text, @status, @issue_count, @inspected_at
    )
  `);
  inspectionRecords.forEach((item) => inspectionInsert.run(item));

  const issueInsert = db.prepare(`
    INSERT INTO issues (
      id, source_type, related_record_id, enterprise_name, issue_title, issue_level, description,
      rectify_requirement, rectify_deadline, status, created_at
    ) VALUES (
      @id, @source_type, @related_record_id, @enterprise_name, @issue_title, @issue_level, @description,
      @rectify_requirement, @rectify_deadline, @status, @created_at
    )
  `);
  issues.forEach((item) => issueInsert.run(item));

  const workInsert = db.prepare(`
    INSERT INTO work_records (
      id, record_type, title, organization_name, description, created_by, created_at
    ) VALUES (
      @id, @record_type, @title, @organization_name, @description, @created_by, @created_at
    )
  `);
  workRecords.forEach((item) => workInsert.run(item));
}

module.exports = { initSchema };
