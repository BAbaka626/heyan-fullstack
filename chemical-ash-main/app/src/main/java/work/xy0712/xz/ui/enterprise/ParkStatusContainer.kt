package work.xy0712.xz.ui.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import work.xy0712.xz.data.model.EnterpriseSummary
import work.xy0712.xz.data.model.ReportResponse
import work.xy0712.xz.data.model.RiskLevelStat
import work.xy0712.xz.ui.chat.HazardPieChart
import work.xy0712.xz.ui.chat.HazardRiskMap

@Composable
fun ParkStatusContainer(
    enterprises: List<EnterpriseSummary>,
    riskStats: List<RiskLevelStat>,
    report: ReportResponse,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("基本信息", "态势指标", "建议意见", "数据报表", "现场处置")

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> ParkBasicInfoTab(enterprises = enterprises, riskStats = riskStats)
                    1 -> ParkSituationMetricsTab(enterprises = enterprises, riskStats = riskStats)
                    2 -> ParkAdviceTab(riskStats = riskStats)
                    3 -> ParkDataReportTab(report = report)
                    4 -> ParkDisposalTab()
                }
            }
        }
    }
}

@Composable
fun ParkBasicInfoTab(
    enterprises: List<EnterpriseSummary>,
    riskStats: List<RiskLevelStat>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicInfoColumn(
            enterprises = enterprises,
            riskStats = riskStats,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        ChartPanel(
            title = "危险源占比",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HazardPieChart(
                chartTitle = "重大危险源风险级别占比",
                chartData = riskStats.map { stat ->
                    mapOf("value" to stat.count, "name" to stat.levelName)
                }
            )
        }
        ChartPanel(
            title = "园区风险分布",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            HazardRiskMap(
                chartTitle = "园区微观库位风险打点",
                chartData = null
            )
        }
    }
}

@Composable
fun ParkSituationMetricsTab(
    enterprises: List<EnterpriseSummary>,
    riskStats: List<RiskLevelStat>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("企业总数", "${enterprises.size}", Modifier.weight(1f))
                MetricTile("化工企业", "${enterprises.count { it.is_chemical_enterprise == 1 }}", Modifier.weight(1f))
                MetricTile("重大危险源企业", "${enterprises.count { it.hazard_level.isNotBlank() }}", Modifier.weight(1f))
            }
        }
        items(riskStats) { stat ->
            RiskStatRow(stat)
        }
    }
}

@Composable
fun ParkAdviceTab(
    riskStats: List<RiskLevelStat>,
    modifier: Modifier = Modifier
) {
    val redOrOrange = riskStats.filter { it.levelCode == "RED" || it.levelCode == "ORANGE" }.sumOf { it.count }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AdviceCard(
                title = "高风险企业核验",
                content = "对红、橙等级企业执行负责人、危险源、应急资源三项核验；当前高关注企业 ${redOrOrange} 家。"
            )
        }
        item {
            AdviceCard(
                title = "微观坐标补齐",
                content = "请补充园区底图原点、比例尺、库位坐标与企业关联表。风险图已预留 0-100 相对网格入口。"
            )
        }
        item {
            AdviceCard(
                title = "报表结构化",
                content = "日报和周报优先渲染安全承诺、封闭化车辆、特殊作业三类表格，避免退化为纯 Markdown 文本。"
            )
        }
    }
}

@Composable
fun ParkDataReportTab(
    report: ReportResponse,
    modifier: Modifier = Modifier
) {
    val safety = report.safetyPromise
    val closed = report.closedManagement
    val operations = report.specialOperations

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("报告日期", report.reportDate.ifBlank { "待接入" }, Modifier.weight(1f))
                MetricTile("值班领导", report.leaderName.ifBlank { "待接入" }, Modifier.weight(1f))
                MetricTile("承诺率", "${safety.promiseRate}%", Modifier.weight(1f))
            }
        }
        item {
            ReportSection(
                title = "安全承诺公告",
                headers = listOf("指标", "公告/承诺情况", "实际执行情况", "异常及措施"),
                rows = listOf(
                    listOf(
                        "企业公告",
                        "应承诺 ${safety.totalEnterprises} 家",
                        "完成 ${safety.completedCount} 家，承诺率 ${safety.promiseRate}%",
                        safety.uncommittedCompanies.joinToString("、").ifBlank { "无" }
                    ),
                    listOf(
                        "特殊作业",
                        safety.specialWorkCompanies.joinToString("、").ifBlank { "无" },
                        "装置开停车：${safety.deviceStartStopCompanies.joinToString("、").ifBlank { "无" }}",
                        "检维修/承包商：${safety.maintenanceCompanies.joinToString("、").ifBlank { "无" }}"
                    )
                )
            )
        }
        item {
            ReportSection(
                title = "封闭化管理抽查",
                headers = listOf("总车辆", "危化品车辆", "总吨数", "抽查结论"),
                rows = listOf(
                    listOf(
                        "${closed.totalVehicles} 辆",
                        "${closed.hazmatVehicles} 辆",
                        "${closed.totalTonnage} 吨",
                        "运单一致：${yesNo(closed.randomCheck.consistent)}；超速 ${closed.randomCheck.speedingCount} 起；违停 ${closed.randomCheck.illegalParkingCount} 起；轨迹可查：${yesNo(closed.randomCheck.trackTraceable)}"
                    )
                )
            )
        }
        item {
            ReportSection(
                title = "表1 本周进出园区前5名危险化学品品名及数量情况",
                headers = listOf("序号", "危险化学品品名", "数量 / 吨"),
                rows = closed.topChemicals
                    .sortedBy { it.rank }
                    .map { listOf("${it.rank}", it.name, "${it.weight}") }
                    .ifEmpty { List(5) { index -> listOf("${index + 1}", "待接入", "待接入") } }
            )
        }
        item {
            ReportSection(
                title = "表2 本周特级动火、一级动火、受限空间作业抽查情况",
                headers = listOf("企业名称", "作业数量", "作业分类", "抽查明细"),
                rows = operations.operationList.map { item ->
                    val count = item.superHotCount + item.level1HotCount + item.confinedSpaceCount
                    listOf(
                        item.enterpriseName,
                        "${count} 起",
                        "特级动火 ${item.superHotCount}；一级动火 ${item.level1HotCount}；受限空间 ${item.confinedSpaceCount}",
                        item.details
                    )
                }.ifEmpty {
                    listOf(listOf("待接入", "0 起", "待接入", "作业票号、动火等级、受限空间作业、线下抽查情况"))
                }
            )
        }
        item {
            ReportSection(
                title = "特殊作业统计",
                headers = listOf("线上承诺", "实际开展", "特级动火", "一级动火", "受限空间"),
                rows = listOf(
                    listOf(
                        "${operations.onlinePromises} 起",
                        "${operations.actualOperations} 起",
                        "${operations.superHotWorkCount} 起",
                        "${operations.level1HotWorkCount} 起",
                        "${operations.confinedSpaceCount} 起"
                    )
                )
            )
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "是" else "否"

@Composable
fun ParkDisposalTab(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DisposalStep("1", "预警确认", "核对企业、库位、危险源等级与现场负责人。") }
        item { DisposalStep("2", "现场联动", "派发巡检、问题单或作业记录，保留处置时间线。") }
        item { DisposalStep("3", "闭环复核", "复核整改材料、承诺公告和特殊作业抽查结果。") }
    }
}

@Composable
private fun BasicInfoColumn(
    enterprises: List<EnterpriseSummary>,
    riskStats: List<RiskLevelStat>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("园区概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoLine("企业数量", "${enterprises.size} 家")
        InfoLine("化工企业", "${enterprises.count { it.is_chemical_enterprise == 1 }} 家")
        InfoLine("覆盖园区", enterprises.map { it.park_name }.filter { it.isNotBlank() }.distinct().joinToString("、").ifBlank { "待接入" })
        HorizontalDivider()
        riskStats.forEach { stat -> RiskStatRow(stat) }
    }
}

@Composable
private fun ChartPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RiskStatRow(stat: RiskLevelStat) {
    val color = Color(android.graphics.Color.parseColor(stat.colorHex))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stat.levelName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("${stat.count} 家", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdviceCard(title: String, content: String) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReportSection(
    title: String,
    headers: List<String>,
    rows: List<List<String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ReportRow(headers, isHeader = true)
        rows.forEach { ReportRow(it, isHeader = false) }
    }
}

@Composable
private fun ReportRow(cells: List<String>, isHeader: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 1.dp, bottom = 1.dp),
                color = if (isHeader) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = cell,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DisposalStep(index: String, title: String, content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(index, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
