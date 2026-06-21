package work.xy0712.xz.ui.enterprise

enum class EnterpriseRiskLevel(
    val label: String,
    val shortLabel: String
) {
    EXTREME("极高风险", "极高"),
    HIGH("高风险", "高"),
    MEDIUM("中风险", "中"),
    LOW("低风险", "低")
}

data class EnterpriseRiskCount(
    val extreme: Int = 0,
    val high: Int = 0,
    val medium: Int = 0,
    val low: Int = 0
)

fun normalizeRiskLevel(raw: String?): EnterpriseRiskLevel {
    val text = raw
        ?.trim()
        ?.lowercase()
        ?: ""

    return when {
        text.contains("极高") ||
        text.contains("红") ||
        text.contains("red") ||
        text.contains("重大风险") ||
        text.contains("一级") -> EnterpriseRiskLevel.EXTREME

        text.contains("高风险") ||
        text.contains("橙") ||
        text.contains("orange") ||
        text.contains("较大风险") ||
        text.contains("二级") -> EnterpriseRiskLevel.HIGH

        text.contains("中风险") ||
        text.contains("黄") ||
        text.contains("yellow") ||
        text.contains("一般风险") ||
        text.contains("三级") -> EnterpriseRiskLevel.MEDIUM

        text.contains("低风险") ||
        text.contains("蓝") ||
        text.contains("blue") ||
        text.contains("低") ||
        text.contains("四级") -> EnterpriseRiskLevel.LOW

        else -> EnterpriseRiskLevel.LOW
    }
}