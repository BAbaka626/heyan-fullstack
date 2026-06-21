function generateWeeklyReport(db) {
  const park = db.prepare(`SELECT * FROM parks LIMIT 1`).get();
  const activeWarnings = db.prepare(`
    SELECT COUNT(*) AS count
    FROM warning_events
    WHERE status = 'active'
  `).get();
  const totalWarnings = db.prepare(`SELECT COUNT(*) AS count FROM warning_events`).get();
  const latestScore = db.prepare(`SELECT * FROM monthly_scores ORDER BY month DESC LIMIT 1`).get();
  const enterpriseStats = db.prepare(`
    SELECT
      SUM(CASE WHEN evaluation_level = '优' THEN 1 ELSE 0 END) AS excellent_count,
      SUM(CASE WHEN evaluation_level = '良' THEN 1 ELSE 0 END) AS good_count,
      SUM(CASE WHEN evaluation_level = '中' THEN 1 ELSE 0 END) AS medium_count,
      SUM(CASE WHEN evaluation_level = '差' THEN 1 ELSE 0 END) AS poor_count
    FROM enterprises
  `).get();

  return `化工园区平台运行工作周报

一、事件中心
本周累计预警工单 ${totalWarnings.count} 条，其中在途处置 ${activeWarnings.count} 条，未发现事故升级情况。

二、安全应急管理
双重预防机制运行情况：优 ${enterpriseStats.excellent_count || 0} 家、良 ${enterpriseStats.good_count || 0} 家、中 ${enterpriseStats.medium_count || 0} 家、差 ${enterpriseStats.poor_count || 0} 家。
平台月度评分：${latestScore.month} 得分 ${latestScore.final_score}，等级 ${latestScore.grade}。

三、园区画像
园区名称：${park.name}
2025年产值：${park.output_2025} 亿元
企业总数：${park.enterprise_count} 家
重大危险源：${park.major_hazard_count} 个

四、建议
1. 针对红色预警点位，优先核查液位、温压类监测点重复报警与销警超时原因。
2. 按企业活跃度和访问量继续优化平台月度评分。
3. 对知识库采用增量导入，避免园区之间数据串查。`;
}

function generateMonthlyReport(db, month) {
  const park = db.prepare(`SELECT * FROM parks LIMIT 1`).get();
  const score = db.prepare(`
    SELECT *
    FROM monthly_scores
    WHERE month = COALESCE(?, month)
    ORDER BY month DESC
    LIMIT 1
  `).get(month || null);
  const access = db.prepare(`
    SELECT *
    FROM access_stats
    WHERE month = COALESCE(?, month)
    ORDER BY month DESC
    LIMIT 1
  `).get(month || null);
  const warningStats = db.prepare(`
    SELECT level, COUNT(*) AS count
    FROM warning_events
    GROUP BY level
    ORDER BY count DESC
  `).all();
  const issueStats = db.prepare(`
    SELECT status, COUNT(*) AS count
    FROM issues
    GROUP BY status
    ORDER BY count DESC
  `).all();
  const workCount = db.prepare(`SELECT COUNT(*) AS count FROM work_records`).get();

  return `化工园区平台运行工作月报

统计月份：${score?.month || month || "未指定"}

一、园区总体情况
园区名称：${park.name}
2025年产值：${park.output_2025} 亿元
企业总数：${park.enterprise_count} 家
重大危险源：${park.major_hazard_count} 个

二、平台运行评分
最终得分：${score?.final_score || 0}
评级：${score?.grade || "未生成"}
访问量：${access?.access_count || 0}
活跃企业：${access?.active_enterprises || 0}

三、风险预警统计
${warningStats.map((item) => `${item.level} ${item.count} 条`).join("；") || "暂无"}

四、巡检与问题整改
问题状态：${issueStats.map((item) => `${item.status} ${item.count} 条`).join("；") || "暂无"}
工作记录总数：${workCount.count}

五、建议
1. 保持重点装置和重大危险源参数持续监测。
2. 按月复核访问量、活跃企业和工单闭环情况。
3. 对逾期问题单设置预警升级。`;
}

module.exports = { generateMonthlyReport, generateWeeklyReport };
