package work.xy0712.xz.ui.enterprise.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import work.xy0712.xz.data.mock.MockData
import work.xy0712.xz.data.model.ChemicalRecord
import work.xy0712.xz.data.model.EnterpriseDetailResponse
import work.xy0712.xz.data.model.EnterpriseRiskPoint
import work.xy0712.xz.data.model.EnterpriseRiskProfile
import work.xy0712.xz.data.model.MajorHazardRecord
import work.xy0712.xz.data.model.PersonRecord
import work.xy0712.xz.data.model.RiskMeasureRecord
import work.xy0712.xz.viewmodel.EnterpriseViewModel

private val tabs = listOf("基本信息", "风险画像", "建议意见", "数据报表", "现场处置")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterpriseDetailScreen(
    enterpriseId: String,
    onBackClick: () -> Unit,
    onNavigateToIssueForm: (String, String) -> Unit,
    onNavigateToInspectionRoutes: () -> Unit,
    onNavigateToInspectionRecords: () -> Unit,
    onNavigateToWorkRecords: () -> Unit,
    onNavigateToReportDetail: (String, String) -> Unit,
    viewModel: EnterpriseViewModel = viewModel()
) {
    val enterprise by viewModel.selectedEnterprise.collectAsState()
    val riskProfile by viewModel.selectedRiskProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(enterpriseId) {
        viewModel.selectEnterprise(enterpriseId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = enterprise?.enterprise?.name ?: "企业详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (enterprise != null && riskProfile != null) {
                EnterpriseDetailContent(
                    enterprise = enterprise!!,
                    riskProfile = riskProfile!!,
                    onNavigateToIssueForm = onNavigateToIssueForm,
                    onNavigateToInspectionRoutes = onNavigateToInspectionRoutes,
                    onNavigateToInspectionRecords = onNavigateToInspectionRecords,
                    onNavigateToWorkRecords = onNavigateToWorkRecords,
                    onNavigateToReportDetail = onNavigateToReportDetail
                )
            }

            errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearError) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterpriseDetailContent(
    enterprise: EnterpriseDetailResponse,
    riskProfile: EnterpriseRiskProfile,
    onNavigateToIssueForm: (String, String) -> Unit,
    onNavigateToInspectionRoutes: () -> Unit,
    onNavigateToInspectionRecords: () -> Unit,
    onNavigateToWorkRecords: () -> Unit,
    onNavigateToReportDetail: (String, String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> BasicInfoTab(enterprise = enterprise)
                1 -> RiskProfileTab(riskProfile = riskProfile)
                2 -> SuggestionsTab(enterpriseName = enterprise.enterprise.name)
                3 -> ReportsTab(onNavigateToReportDetail = onNavigateToReportDetail)
                4 -> DisposalTab(
                    enterpriseName = enterprise.enterprise.name,
                    enterpriseId = enterprise.enterprise.id,
                    onNavigateToIssueForm = onNavigateToIssueForm,
                    onNavigateToInspectionRoutes = onNavigateToInspectionRoutes,
                    onNavigateToInspectionRecords = onNavigateToInspectionRecords,
                    onNavigateToWorkRecords = onNavigateToWorkRecords
                )
            }
        }
    }
}

@Composable
private fun BasicInfoTab(enterprise: EnterpriseDetailResponse) {
    val ent = enterprise.enterprise
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            InfoCard(title = "企业基本信息") {
                InfoRow("企业名称", ent.name)
                InfoRow("统一社会信用代码", ent.social_credit_code)
                InfoRow("所属园区", ent.park_name)
                InfoRow("注册地址", ent.address)
                InfoRow("经营范围", ent.business_scope)
                InfoRow("法人", ent.legal_person)
                InfoRow("安全负责人", ent.safety_leader)
                InfoRow("员工人数", "${ent.employee_count}人")
                InfoRow("危险源等级", ent.hazard_level)
                InfoRow("评估等级", ent.evaluation_level)
            }
        }

        if (enterprise.personnel.isNotEmpty()) {
            item {
                InfoCard(title = "关键人员 (${enterprise.personnel.size}人)") {
                    enterprise.personnel.forEach { p ->
                        PersonRow(person = p)
                    }
                }
            }
        }

        if (enterprise.chemicals.isNotEmpty()) {
            item {
                InfoCard(title = "涉及化学品 (${enterprise.chemicals.size}种)") {
                    enterprise.chemicals.forEach { c ->
                        ChemicalRow(chemical = c)
                    }
                }
            }
        }

        if (enterprise.majorHazards.isNotEmpty()) {
            item {
                InfoCard(title = "重大危险源 (${enterprise.majorHazards.size}个)") {
                    enterprise.majorHazards.forEach { h ->
                        HazardRow(hazard = h)
                    }
                }
            }
        }

        if (enterprise.riskMeasures.isNotEmpty()) {
            item {
                InfoCard(title = "风险管控措施 (${enterprise.riskMeasures.size}条)") {
                    enterprise.riskMeasures.forEach { m ->
                        MeasureRow(measure = m)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskProfileTab(riskProfile: EnterpriseRiskProfile) {
    val summary = riskProfile.summary
    val charts = riskProfile.charts

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Risk score header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (summary.riskLevel) {
                        "极高风险" -> Color(0xFFFFEBEE)
                        "高风险" -> Color(0xFFFFF3E0)
                        "中风险" -> Color(0xFFFFFDE7)
                        else -> Color(0xFFE3F2FD)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前风险等级",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = summary.riskLevel,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (summary.riskLevel) {
                                "极高风险" -> Color(0xFFD32F2F)
                                "高风险" -> Color(0xFFF57C00)
                                "中风险" -> Color(0xFFFBC02D)
                                else -> Color(0xFF1976D2)
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "评分 ${summary.riskScore}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            InfoCard(title = "安全态势指标") {
                InfoRow("在线人数", "${summary.onlinePersonnel}人")
                InfoRow("活跃报警", "${summary.activeAlarmCount}起")
                InfoRow("未整改问题", "${summary.openIssueCount}项")
                InfoRow("本月巡检", "${summary.inspectionCount}次")
                InfoRow("活跃指数", String.format("%.2f", summary.activityIndex))
                InfoRow("重大危险源", "${summary.majorHazardCount}个")
            }
        }

        if (charts.riskTrend.isNotEmpty()) {
            item {
                InfoCard(title = "风险趋势 (近7日)") {
                    RiskTrendBars(points = charts.riskTrend)
                }
            }
        }

        if (riskProfile.majorHazards.isNotEmpty()) {
            item {
                InfoCard(title = "重点危险源") {
                    riskProfile.majorHazards.forEach { h ->
                        HazardRow(hazard = h)
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsTab(enterpriseName: String) {
    val suggestions = MockData.getMockSuggestions(enterpriseName)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "以下建议基于当前风险画像自动生成，仅供参考。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        items(suggestions.size) { index ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestions[index],
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsTab(
    onNavigateToReportDetail: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "演示数据：当前后端报表接口为园区级，企业级报表接口待开发。",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        ReportCard(
            title = "本周安全周报",
            date = "2026年4月第3周",
            icon = Icons.Filled.Report,
            onClick = { onNavigateToReportDetail("weekly", "本周安全周报") }
        )

        ReportCard(
            title = "本月管理月报",
            date = "2026年4月",
            icon = Icons.Filled.Book,
            onClick = { onNavigateToReportDetail("monthly", "本月管理月报") }
        )
    }
}

@Composable
private fun DisposalTab(
    enterpriseName: String,
    enterpriseId: String,
    onNavigateToIssueForm: (String, String) -> Unit,
    onNavigateToInspectionRoutes: () -> Unit,
    onNavigateToInspectionRecords: () -> Unit,
    onNavigateToWorkRecords: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "现场处置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        DisposalActionCard(
            title = "问题登记",
            description = "记录现场发现的安全隐患和问题",
            color = Color(0xFFFFEBEE),
            onClick = { onNavigateToIssueForm(enterpriseName, enterpriseId) }
        )

        DisposalActionCard(
            title = "巡检路线",
            description = "查看并执行配置的巡检路线",
            color = Color(0xFFE8F5E9),
            onClick = onNavigateToInspectionRoutes
        )

        DisposalActionCard(
            title = "巡检记录",
            description = "查看历史巡检台账",
            color = Color(0xFFE3F2FD),
            onClick = onNavigateToInspectionRecords
        )

        DisposalActionCard(
            title = "工作记录",
            description = "查看日常工作台账",
            color = Color(0xFFFFF3E0),
            onClick = onNavigateToWorkRecords
        )
    }
}

// ─── Reusable Components ───

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun PersonRow(person: PersonRecord) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "${person.person_name} · ${person.position_name}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${person.affiliated_department} · ${person.mobile_number}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChemicalRow(chemical: ChemicalRecord) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = chemical.name + if (chemical.alias.isNotBlank()) " (${chemical.alias})" else "",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "CAS: ${chemical.cas} · 年产量: ${chemical.annual_output}" +
                    if (chemical.is_hazardous == 1) " · 危险化学品" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HazardRow(hazard: MajorHazardRecord) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "${hazard.hazard_name} · ${hazard.level}级",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "负责人: ${hazard.responsible} · 技术负责人: ${hazard.technical}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MeasureRow(measure: RiskMeasureRecord) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = measure.measure_desc,
            style = MaterialTheme.typography.bodyMedium
        )
        if (measure.troubleshoot_content.isNotBlank()) {
            Text(
                text = "排查: ${measure.troubleshoot_content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RiskTrendBars(points: List<EnterpriseRiskPoint>) {
    if (points.isEmpty()) return
    val maxScore = points.mapNotNull { it.score }.maxOrNull()?.coerceAtLeast(100.0) ?: 100.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            val score = point.score ?: 0.0
            val ratio = (score / maxScore).toFloat().coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = point.date.substring(5),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(48.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (point.level) {
                                    "极高风险" -> Color(0xFFD32F2F)
                                    "高风险" -> Color(0xFFF57C00)
                                    "中风险" -> Color(0xFFFBC02D)
                                    else -> Color(0xFF1976D2)
                                }
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%.0f", score),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    date: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisposalActionCard(title: String, description: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
