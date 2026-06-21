const { clamp, toNumber } = require("../utils");

function gradeForScore(score) {
  if (score >= 90) return "优";
  if (score >= 80) return "良";
  if (score >= 70) return "中";
  return "差";
}

function computeMonthlyScore({ previousScore, accessCount, bonusScore = 0, penaltyScore = 0 }) {
  const accessBonus = clamp(Math.floor(toNumber(accessCount) / 200), 0, 10);
  return clamp(toNumber(previousScore) + accessBonus + toNumber(bonusScore) - toNumber(penaltyScore), 0, 100);
}

function evaluateWarningLevel(metrics) {
  const disturbance = toNumber(metrics.average_disturbance_rate);
  const repeats = toNumber(metrics.repeat_alarm_count);
  const closeMinutes = toNumber(metrics.avg_close_minutes);
  const timelyRate = toNumber(metrics.timely_rate);
  const avgAlarm = toNumber(metrics.alarm_count) / Math.max(toNumber(metrics.point_count), 1);

  if (
    disturbance >= 30 &&
    (repeats >= 2 || closeMinutes > 5 || timelyRate === 0 || avgAlarm >= 3)
  ) {
    return "红色预警";
  }

  if (
    disturbance >= 10 &&
    disturbance < 30 &&
    (repeats >= 1 || (closeMinutes >= 3 && closeMinutes <= 5) || timelyRate < 50 || (avgAlarm >= 2 && avgAlarm < 3))
  ) {
    return "橙色预警";
  }

  if (
    disturbance >= 0 &&
    disturbance < 10 &&
    (avgAlarm >= 2 || (closeMinutes >= 1 && closeMinutes <= 3) || (timelyRate >= 50 && timelyRate < 100))
  ) {
    return "黄色预警";
  }

  return "蓝色预警";
}

function computePreventionScore(input) {
  const planned = Math.max(toNumber(input.plannedTasks), 1);
  const finished = clamp(toNumber(input.finishedTasks), 0, planned);
  const responsiblePenalty = toNumber(input.mainLeaderPenalty) + toNumber(input.techLeaderPenalty) + toNumber(input.operationLeaderPenalty);
  const overdueHazards = toNumber(input.overdueHazards);
  const fixedHazards = toNumber(input.fixedHazards);
  const totalHazards = Math.max(overdueHazards + fixedHazards, 1);
  const hiddenPenalty = toNumber(input.hiddenDangerPenalty);
  const veto = Boolean(input.hasVetoItem);

  const taskScore = clamp((finished / planned) * 50 - responsiblePenalty, 0, 50);
  const hazardScore = clamp((fixedHazards / totalHazards) * 50 - hiddenPenalty, 0, 50);
  const total = veto ? 0 : clamp(taskScore + hazardScore, 0, 100);

  return {
    total,
    grade: gradeForScore(total),
    taskScore,
    hazardScore,
    veto,
  };
}

module.exports = {
  computeMonthlyScore,
  computePreventionScore,
  evaluateWarningLevel,
  gradeForScore,
};
