package work.xy0712.xz.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import work.xy0712.xz.ui.chat.ChatScreen
import work.xy0712.xz.ui.enterprise.EnterpriseScreen
import work.xy0712.xz.ui.enterprise.ParkOverviewScreen
import work.xy0712.xz.ui.enterprise.detail.EnterpriseDetailScreen
import work.xy0712.xz.ui.enterprise.disposal.InspectionRecordsScreen
import work.xy0712.xz.ui.enterprise.disposal.InspectionRoutesScreen
import work.xy0712.xz.ui.enterprise.disposal.IssueFormScreen
import work.xy0712.xz.ui.enterprise.disposal.WorkRecordsScreen
import work.xy0712.xz.ui.enterprise.detail.ReportDetailScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier
    ) {
        composable(Screen.Chat.route) {
            ChatScreen()
        }
        composable(Screen.ParkOverview.route) {
            ParkOverviewScreen()
        }
        composable(Screen.Enterprise.route) {
            EnterpriseScreen(
                onEnterpriseClick = { enterpriseId ->
                    navController.navigate(Screen.enterpriseDetailRoute(enterpriseId))
                }
            )
        }
        composable(Screen.EnterpriseDetail.route) { backStackEntry ->
            val enterpriseId = backStackEntry.arguments?.getString("enterpriseId") ?: ""
            EnterpriseDetailScreen(
                enterpriseId = enterpriseId,
                onBackClick = { navController.popBackStack() },
                onNavigateToIssueForm = { name, id ->
                    navController.navigate(Screen.issueFormRoute(name, id))
                },
                onNavigateToInspectionRoutes = {
                    navController.navigate(Screen.InspectionRoutes.route)
                },
                onNavigateToInspectionRecords = {
                    navController.navigate(Screen.InspectionRecords.route)
                },
                onNavigateToWorkRecords = {
                    navController.navigate(Screen.WorkRecords.route)
                },
                onNavigateToReportDetail = { reportType, title ->
                    navController.navigate(Screen.reportDetailRoute(reportType, title))
                }
            )
        }
        composable(Screen.ReportDetail.route) { backStackEntry ->
            val reportType = backStackEntry.arguments?.getString("reportType") ?: "weekly"
            val title = backStackEntry.arguments?.getString("title") ?: "报告详情"
            ReportDetailScreen(
                reportType = reportType,
                title = title,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.IssueForm.route) { backStackEntry ->
            val enterpriseName = backStackEntry.arguments?.getString("enterpriseName") ?: ""
            val enterpriseId = backStackEntry.arguments?.getString("enterpriseId") ?: ""
            IssueFormScreen(
                enterpriseName = enterpriseName,
                enterpriseId = enterpriseId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.InspectionRoutes.route) {
            InspectionRoutesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.InspectionRecords.route) {
            InspectionRecordsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.WorkRecords.route) {
            WorkRecordsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
