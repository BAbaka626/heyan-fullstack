package work.xy0712.xz.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Chat
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Chat : Screen("chat", "智控助手", Icons.Default.Chat)
    data object Enterprise : Screen("enterprise", "企业检索", Icons.AutoMirrored.Filled.List)

    data object EnterpriseDetail : Screen("enterprise_detail/{enterpriseId}", "企业详情")
    data object IssueForm : Screen("issue_form/{enterpriseName}?enterpriseId={enterpriseId}", "问题登记")
    data object InspectionRoutes : Screen("inspection_routes", "巡检路线")
    data object InspectionRecords : Screen("inspection_records", "巡检记录")
    data object WorkRecords : Screen("work_records", "工作记录")
    data object ReportDetail : Screen("report_detail/{reportType}?title={title}", "报告详情")

    companion object {
        val bottomNavItems = listOf(Chat, Enterprise)

        fun enterpriseDetailRoute(enterpriseId: String) = "enterprise_detail/$enterpriseId"
        fun issueFormRoute(enterpriseName: String, enterpriseId: String = ""): String {
            return if (enterpriseId.isNotEmpty()) {
                "issue_form/$enterpriseName?enterpriseId=$enterpriseId"
            } else {
                "issue_form/$enterpriseName"
            }
        }
        fun reportDetailRoute(reportType: String, title: String): String {
            return "report_detail/$reportType?title=$title"
        }
    }
}
