package work.xy0712.xz.ui.enterprise

import work.xy0712.xz.data.model.EnterpriseSummary

enum class EnterpriseRiskLevel(
    val label: String,
    val shortLabel: String,
    val colorHex: String,
    val weight: Int
) {
    EXTREME("极高风险", "极高", "#D32F2F", 4),
    HIGH("高风险", "高", "#F57C00", 3),
    MEDIUM("中风险", "中", "#FBC02D", 2),
    LOW("低风险", "低", "#1976D2", 1),
    UNKNOWN("待评估", "待评估", "#757575", 0)
}

data class EnterpriseRiskCount(
    val extreme: Int = 0,
    val high: Int = 0,
    val medium: Int = 0,
    val low: Int = 0,
    val unknown: Int = 0
)

fun normalizeRiskLevel(raw: String?): EnterpriseRiskLevel {
    val text = raw
        ?.trim()
        ?.lowercase()
        ?: ""

    return when (text) {
        "1", "extreme", "极高风险", "红", "红色", "red" -> EnterpriseRiskLevel.EXTREME
        "2", "high", "高风险", "橙", "橙色", "orange" -> EnterpriseRiskLevel.HIGH
        "3", "medium", "中风险", "黄", "黄色", "yellow" -> EnterpriseRiskLevel.MEDIUM
        "4", "low", "低风险", "蓝", "蓝色", "blue" -> EnterpriseRiskLevel.LOW
        else -> EnterpriseRiskLevel.UNKNOWN
    }
}

fun getEnterpriseRiskLevel(enterprise: EnterpriseSummary): EnterpriseRiskLevel {
    val compatibleFields = listOf(
        enterprise.computedRiskLevel,
        enterprise.computedRiskLabel,
        enterprise.riskLevel,
        enterprise.risk_level,
        enterprise.qyfxdj?.toString()
    )
    return compatibleFields
        .asSequence()
        .map(::normalizeRiskLevel)
        .firstOrNull { it != EnterpriseRiskLevel.UNKNOWN }
        ?: EnterpriseRiskLevel.UNKNOWN
}

fun countEnterpriseRiskLevels(enterprises: List<EnterpriseSummary>): EnterpriseRiskCount {
    return enterprises.fold(EnterpriseRiskCount()) { count, enterprise ->
        when (getEnterpriseRiskLevel(enterprise)) {
            EnterpriseRiskLevel.EXTREME -> count.copy(extreme = count.extreme + 1)
            EnterpriseRiskLevel.HIGH -> count.copy(high = count.high + 1)
            EnterpriseRiskLevel.MEDIUM -> count.copy(medium = count.medium + 1)
            EnterpriseRiskLevel.LOW -> count.copy(low = count.low + 1)
            EnterpriseRiskLevel.UNKNOWN -> count.copy(unknown = count.unknown + 1)
        }
    }
}
