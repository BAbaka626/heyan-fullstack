const RISK_LEVELS = Object.freeze({
  UNKNOWN: { code: "UNKNOWN", label: "待评估", color: "#757575", weight: 0 },
  LOW: { code: "LOW", label: "低风险", color: "#1976D2", weight: 1 },
  MEDIUM: { code: "MEDIUM", label: "中风险", color: "#FBC02D", weight: 2 },
  HIGH: { code: "HIGH", label: "高风险", color: "#F57C00", weight: 3 },
  EXTREME: { code: "EXTREME", label: "极高风险", color: "#D32F2F", weight: 4 },
});

function normalizeRisk(value) {
  if (value === null || value === undefined || value === "") return RISK_LEVELS.UNKNOWN;
  const text = String(value).trim().toUpperCase();
  if (["1", "EXTREME", "极高风险", "红", "红色", "RED"].includes(text)) return RISK_LEVELS.EXTREME;
  if (["2", "HIGH", "高风险", "橙", "橙色", "ORANGE"].includes(text)) return RISK_LEVELS.HIGH;
  if (["3", "MEDIUM", "中风险", "黄", "黄色", "YELLOW"].includes(text)) return RISK_LEVELS.MEDIUM;
  if (["4", "LOW", "低风险", "蓝", "蓝色", "BLUE"].includes(text)) return RISK_LEVELS.LOW;
  return RISK_LEVELS.UNKNOWN;
}

function evaluateDoublePrevention(enterprise) {
  const a = Number(enterprise.doublePreventionA ?? enterprise.double_prevention_a);
  const b = Number(enterprise.doublePreventionB ?? enterprise.double_prevention_b);
  const hasScores = Number.isFinite(a) && Number.isFinite(b);
  if (!hasScores) {
    return { score: null, effectLevel: "待评估", risk: RISK_LEVELS.UNKNOWN };
  }

  const veto = Boolean(
    enterprise.doublePreventionVeto ?? enterprise.double_prevention_veto ?? false
  );
  const score = Math.max(0, Math.min(100, (a + b) * (veto ? 0 : 1)));
  if (score >= 90) return { score, effectLevel: "优", risk: RISK_LEVELS.LOW };
  if (score >= 80) return { score, effectLevel: "良", risk: RISK_LEVELS.MEDIUM };
  if (score >= 70) return { score, effectLevel: "中", risk: RISK_LEVELS.HIGH };
  return { score, effectLevel: "差", risk: RISK_LEVELS.EXTREME };
}

function evaluateWarningRisk(enterprise) {
  return normalizeRisk(
    enterprise.warningLevel ??
      enterprise.warning_level ??
      enterprise.majorHazardWarningLevel ??
      enterprise.major_hazard_warning_level
  );
}

function highestRisk(...levels) {
  return levels.reduce(
    (highest, current) => current.weight > highest.weight ? current : highest,
    RISK_LEVELS.UNKNOWN
  );
}

function evaluateEnterpriseRisk(enterprise = {}) {
  const baseRisk = normalizeRisk(
    enterprise.QYFXDJ ?? enterprise.qyfxdj ?? enterprise.evaluation_level
  );
  const prevention = evaluateDoublePrevention(enterprise);
  const warningRisk = evaluateWarningRisk(enterprise);
  const finalRisk = highestRisk(baseRisk, prevention.risk, warningRisk);
  const reasons = [];

  if (baseRisk !== RISK_LEVELS.UNKNOWN) reasons.push(`一企一档风险等级：${baseRisk.label}`);
  if (prevention.risk !== RISK_LEVELS.UNKNOWN) {
    reasons.push(`双重预防运行效果：${prevention.effectLevel}（${prevention.score}分）`);
  }
  if (warningRisk !== RISK_LEVELS.UNKNOWN) reasons.push(`重大危险源预警：${warningRisk.label}`);
  if (reasons.length === 0) reasons.push("缺少可识别的风险评估字段");

  return {
    ...enterprise,
    computedRiskLevel: finalRisk.code,
    computedRiskLabel: finalRisk.label,
    computedRiskColor: finalRisk.color,
    computedRiskWeight: finalRisk.weight,
    doublePreventionScore: prevention.score,
    doublePreventionEffectLevel: prevention.effectLevel,
    riskReason: reasons.join("；"),
    riskLevel: finalRisk.label,
    risk_level: finalRisk.label,
    riskColor: finalRisk.color,
  };
}

function enrichEnterprisesRisk(enterprises = []) {
  return enterprises.map(evaluateEnterpriseRisk);
}

function countEnterpriseRiskLevels(enterprises = []) {
  const counts = { EXTREME: 0, HIGH: 0, MEDIUM: 0, LOW: 0, UNKNOWN: 0 };
  enrichEnterprisesRisk(enterprises).forEach((enterprise) => {
    counts[enterprise.computedRiskLevel] += 1;
  });
  return counts;
}

module.exports = {
  RISK_LEVELS,
  countEnterpriseRiskLevels,
  enrichEnterprisesRisk,
  evaluateDoublePrevention,
  evaluateEnterpriseRisk,
  normalizeRisk,
};
