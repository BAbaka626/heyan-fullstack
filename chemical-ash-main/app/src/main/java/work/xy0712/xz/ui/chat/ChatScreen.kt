package work.xy0712.xz.ui.chat

import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.layout.ContentScale
import com.mikepenz.markdown.m3.Markdown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import work.xy0712.xz.data.model.AskChart
import work.xy0712.xz.data.model.ChatSession
import work.xy0712.xz.data.model.ChatTurn
import work.xy0712.xz.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isAsking by viewModel.isAsking.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val drawerOpen by viewModel.drawerOpen.collectAsState()
    val showRenameDialog by viewModel.showRenameDialog.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentSession = sessions.find { it.id == currentSessionId }
    val turns = currentSession?.turns ?: emptyList()

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) drawerState.open() else drawerState.close()
    }

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && drawerOpen) viewModel.closeDrawer()
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HistoryDrawerContent(
                sessions = sessions,
                currentSessionId = currentSessionId,
                onSessionClick = { viewModel.switchSession(it) },
                onNewSessionClick = {
                    viewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { viewModel.deleteSession(it) }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable { viewModel.showRenameDialog() }
                        ) {
                            Text(
                                text = currentSession?.title ?: "智控助手",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "重命名",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleDrawer() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Subject,
                                contentDescription = "历史会话"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.createNewSession() }) {
                            Icon(Icons.Filled.Add, contentDescription = "新对话")
                        }
                    }
                )
            },
            bottomBar = {
                ChatInputBar(
                    value = inputText,
                    onValueChange = viewModel::onInputChange,
                    onSend = {
                        keyboardController?.hide()
                        viewModel.sendQuestion()
                    },
                    isLoading = isAsking
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (turns.isEmpty() && !isAsking) {
                    EmptyChatPlaceholder(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(turns, key = { it.timestamp }) { turn ->
                            ChatTurnItem(turn = turn)
                        }
                        if (isAsking) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("小智思考中...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
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
                            IconButton(onClick = viewModel::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(currentSession?.title ?: "") }
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("重命名对话") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentSession?.let { viewModel.renameSession(it.id, newTitle) }
                    viewModel.dismissRenameDialog()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameDialog) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HistoryDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String,
    onSessionClick: (String) -> Unit,
    onNewSessionClick: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    ) {
        TextButton(
            onClick = onNewSessionClick,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("新对话")
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sessions) { session ->
                val isSelected = session.id == currentSessionId
                Surface(
                    onClick = { onSessionClick(session.id) },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (sessions.size > 1) {
                            IconButton(
                                onClick = { showDeleteConfirm = session.id },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { sessionId ->
        val session = sessions.find { it.id == sessionId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除「${session?.title ?: "该对话"}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(sessionId)
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ChatTurnItem(turn: ChatTurn) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                modifier = Modifier.padding(start = 48.dp)
            ) {
                Text(
                    text = turn.question,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                modifier = Modifier.padding(end = 48.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val aiAnswer = turn.response.answer
                    val imageRequests = remember(aiAnswer) { extractMarkdownImages(aiAnswer) }
                    val cleanAnswer = remember(aiAnswer) { removeMarkdownImages(aiAnswer).trim() }
                    val diagramDecision = remember(turn.response, aiAnswer) { resolveDiagramDecision(turn) }

                    val isReportNeed = turn.response.mode == "report"
                            || aiAnswer.contains("承诺率")
                            || aiAnswer.contains("危险化学品品名")
                            || aiAnswer.contains("作业票")
                            || aiAnswer.contains("动火等级")
                            || aiAnswer.contains("封闭化")
                            || aiAnswer.contains("特殊作业")

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (diagramDecision) {
                            is DiagramDecision.Pie -> {
                                HazardPieChart(
                                    chartTitle = diagramDecision.title,
                                    chartData = diagramDecision.chartData ?: fallbackHazardPieData()
                                )
                            }
                            is DiagramDecision.Scatter -> {
                                HazardRiskMap(
                                    chartTitle = diagramDecision.title,
                                    chartData = diagramDecision.chartData ?: fallbackParkRiskPoints()
                                )
                            }
                            is DiagramDecision.StaticImage -> {
                                StaticDiagramImages(requests = diagramDecision.images)
                            }
                            DiagramDecision.None -> Unit
                        }

                        if (diagramDecision !is DiagramDecision.StaticImage && imageRequests.isNotEmpty()) {
                            StaticDiagramImages(requests = imageRequests)
                        }

                        if (isReportNeed) {
                            StructuredReportPreview(answer = aiAnswer)
                        }

                        if (cleanAnswer.isNotBlank()) {
                            SimpleMarkdownText(text = cleanAnswer)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface DiagramDecision {
    data class Pie(val title: String, val chartData: Any?) : DiagramDecision
    data class Scatter(val title: String, val chartData: Any?) : DiagramDecision
    data class StaticImage(val images: List<StaticDiagramImageRequest>) : DiagramDecision
    data object None : DiagramDecision
}

private data class StaticDiagramImageRequest(
    val url: String?,
    val title: String
)

private val markdownImageRegex = Regex("""!\[[^\]]*]\(([^)\s]+)(?:\s+"[^"]*")?\)""")

private fun resolveDiagramDecision(turn: ChatTurn): DiagramDecision {
    val answer = turn.response.answer
    val charts = turn.response.charts
    val explicitChart = charts.firstOrNull()
    val explicitTitle = explicitChart?.title.orEmpty()
    val rootChartType = turn.response.chartType.lowercase()
    val chartType = explicitChart?.chartType.orEmpty().ifBlank { rootChartType }
    val legacyType = explicitChart?.type.orEmpty()
    val title = explicitTitle.ifBlank { answer }

    val isDiagramMode = turn.response.mode == "diagram"
    val isPie = isDiagramMode && (
        chartType == "pie" ||
            legacyType == "pie_chart" ||
            title.contains("危险源占比")
        )
    val isScatter = isDiagramMode && (
        chartType == "scatter" ||
            legacyType == "risk_map" ||
            title.contains("园区风险分布")
        )

    return when {
        isPie && title.contains("危险源占比") -> DiagramDecision.Pie(
            title = explicitTitle.ifBlank { "危险源占比四色环形统计图" },
            chartData = explicitChart?.data
        )
        isScatter && title.contains("园区风险分布") -> DiagramDecision.Scatter(
            title = explicitTitle.ifBlank { "园区风险分布微观网格图" },
            chartData = explicitChart?.data
        )
        answer.contains("危险源占比") || answer.contains("园区风险分布") || answer.contains("园区风险画像") -> {
            val images = extractMarkdownImages(answer)
            if (images.isNotEmpty()) {
                DiagramDecision.StaticImage(images)
            } else {
                resolveKeywordFallback(answer)
            }
        }
        else -> DiagramDecision.None
    }
}

private fun resolveKeywordFallback(answer: String): DiagramDecision {
    return when {
        answer.contains("危险源占比") -> DiagramDecision.Pie(
            title = "危险源占比四色环形统计图",
            chartData = fallbackHazardPieData()
        )
        answer.contains("园区风险分布") -> DiagramDecision.Scatter(
            title = "园区风险分布微观网格图",
            chartData = fallbackParkRiskPoints()
        )
        answer.contains("园区风险画像") -> DiagramDecision.StaticImage(
            listOf(StaticDiagramImageRequest(url = null, title = "园区风险画像"))
        )
        else -> DiagramDecision.None
    }
}

private fun extractMarkdownImages(text: String): List<StaticDiagramImageRequest> {
    return markdownImageRegex.findAll(text).map { match ->
        val full = match.value
        val title = full.substringAfter("![").substringBefore("]").ifBlank { "静态分析图" }
        StaticDiagramImageRequest(url = match.groupValues[1], title = title)
    }.toList()
}

private fun removeMarkdownImages(text: String): String = markdownImageRegex.replace(text, "").trim()

private fun fallbackHazardPieData(): List<Map<String, Any>> = listOf(
    mapOf("value" to 14, "name" to "一级重大危险源"),
    mapOf("value" to 7, "name" to "二级重大危险源"),
    mapOf("value" to 4, "name" to "三级重大危险源"),
    mapOf("value" to 9, "name" to "四级重大危险源")
)

private fun fallbackParkRiskPoints(): List<Map<String, Any>> = listOf(
    mapOf("name" to "星澜化工园北罐组A-01库位 (一级-红)", "value" to listOf(28, 76), "itemStyle" to mapOf("color" to "#D32F2F")),
    mapOf("name" to "星澜化工园反应装置B-03单元 (二级-橙)", "value" to listOf(64, 58), "itemStyle" to mapOf("color" to "#F57C00")),
    mapOf("name" to "星澜化工园危化品装卸C-02泊位 (三级-黄)", "value" to listOf(42, 34), "itemStyle" to mapOf("color" to "#FBC02D")),
    mapOf("name" to "星澜化工园应急物资D-01仓 (四级-蓝)", "value" to listOf(78, 24), "itemStyle" to mapOf("color" to "#1976D2"))
)

@Composable
private fun StaticDiagramImages(requests: List<StaticDiagramImageRequest>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        requests.forEach { request ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (request.url.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = request.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    AsyncImage(
                        model = request.url,
                        contentDescription = request.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleMarkdownText(text: String) {
    Markdown(
        content = text,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StructuredReportPreview(answer: String) {
    val sections = buildList {
        if (answer.contains("承诺率") || answer.contains("安全承诺")) {
            add(
                ReportTableSpec(
                    title = "安全承诺公告",
                    headers = listOf("指标", "公告/承诺情况", "实际执行情况", "异常及措施"),
                    rows = listOf(
                        listOf("企业公告", "10:00 前完成安全承诺公告", "完成家数、未承诺家数、承诺率", "未承诺企业名单及原因"),
                        listOf("特殊作业", "申报特殊作业企业及名单", "实际开展企业、未按公告开展企业", "不一致企业及说明")
                    )
                )
            )
        }
        if (answer.contains("危险化学品品名") || answer.contains("封闭化")) {
            add(
                ReportTableSpec(
                    title = "表1 本周进出园区前5名危险化学品品名及数量情况",
                    headers = listOf("序号", "危险化学品品名", "数量 / 吨"),
                    rows = List(5) { index -> listOf("${index + 1}", "待解析", "待解析") }
                )
            )
        }
        if (answer.contains("作业票") || answer.contains("动火") || answer.contains("受限空间") || answer.contains("特殊作业")) {
            add(
                ReportTableSpec(
                    title = "表2 本周特级动火、一级动火、受限空间作业抽查情况",
                    headers = listOf("企业名称", "作业数量", "抽查明细", "发现问题数量"),
                    rows = listOf(
                        listOf("待解析", "待解析", "作业票号、动火等级、受限空间作业、线下抽查情况", "待解析")
                    )
                )
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.ifEmpty {
            listOf(
                ReportTableSpec(
                    title = "平台日报/周报结构化预览",
                    headers = listOf("模块", "关键字段", "渲染状态"),
                    rows = listOf(listOf("报表", "承诺率、危险化学品品名、作业票、动火等级", "待解析"))
                )
            )
        }.forEach { spec ->
            ReportTable(spec)
        }
    }
}

private data class ReportTableSpec(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

@Composable
private fun ReportTable(spec: ReportTableSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(spec.title, style = MaterialTheme.typography.titleSmall)
        ReportTableRow(spec.headers, isHeader = true)
        spec.rows.forEach { ReportTableRow(it, isHeader = false) }
    }
}

@Composable
private fun ReportTableRow(cells: List<String>, isHeader: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 1.dp, bottom = 1.dp),
                color = if (isHeader) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = cell,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = if (isHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                minLines = 3,
                maxLines = 8
            )
        }
        val sendEnabled = value.trim().isNotEmpty() && !isLoading
        Box(
            modifier = Modifier
                .clickable(enabled = sendEnabled, onClick = onSend)
                .background(
                    if (sendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                "发送",
                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "小智问数",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "输入问题，获取化工园区 data 分析",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun HazardPieChart(chartTitle: String, chartData: Any?) {
    val jsonData = remember(chartData) {
        runCatching { Gson().toJson(chartData ?: fallbackHazardPieData()) }
            .getOrDefault(Gson().toJson(fallbackHazardPieData()))
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()

                val htmlContent = """
                    <html>
                    <head>
                        <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
                    </head>
                    <body style="margin:0;padding:10px;background-color:transparent;">
                        <div id="pieContainer" style="width:100%;height:100%;"></div>
                        <script>
                            var myChart = echarts.init(document.getElementById('pieContainer'));
                            var backendRaw = $jsonData;
                            
                            var seriesData = (backendRaw && backendRaw.length > 0) ? backendRaw : [
                                { value: 14, name: '一级重大危险源 (14个)' },
                                { value: 7, name: '二级重大危险源 (7个)' },
                                { value: 4, name: '三级重大危险源 (4个)' },
                                { value: 9, name: '四级重大危险源 (9个)' }
                            ];

                            var option = {
                                title: { text: '$chartTitle', left: 'center', top: '0px', textStyle: { fontSize: 14, color: '#333' } },
                                tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                                legend: { bottom: '0', left: 'center', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 10 } },
                                series: [{
                                    type: 'pie',
                                    radius: ['40%', '70%'],
                                    avoidLabelOverlap: true,
                                    itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
                                    label: { show: false },
                                    data: seriesData
                                }]
                            };
                            myChart.setOption(option);
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            }
        }
    )
}

@Composable
fun HazardRiskMap(chartTitle: String, chartData: Any?) {
    var selectedRiskPoint by remember { mutableStateOf<RiskPointDialogData?>(null) }
    val jsonData = remember(chartData) {
        runCatching { Gson().toJson(chartData ?: fallbackParkRiskPoints()) }
            .getOrDefault(Gson().toJson(fallbackParkRiskPoints()))
    }

    Column {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            factory = { context ->
                WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    RiskPointBridge { payload ->
                        post { selectedRiskPoint = parseRiskPoint(payload) }
                    },
                    "AndroidRiskBridge"
                )

                val htmlContent = """
                    <html>
                    <head>
                        <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
                    </head>
                    <body style="margin:0;padding:10px;background-color:transparent;">
                        <div id="mapContainer" style="width:100%;height:100%;"></div>
                        <script>
                            var myChart = echarts.init(document.getElementById('mapContainer'));
                            var backendRaw = $jsonData;
                            
                            var localParkPoints = (backendRaw && backendRaw.length > 0) ? backendRaw : [
                                { name: '星澜化工园北罐组A-01库位 (一级-红)', value: [28, 76], itemStyle: {color: '#D32F2F'} },
                                { name: '星澜化工园反应装置B-03单元 (二级-橙)', value: [64, 58], itemStyle: {color: '#F57C00'} },
                                { name: '星澜化工园危化品装卸C-02泊位 (三级-黄)', value: [42, 34], itemStyle: {color: '#FBC02D'} },
                                { name: '星澜化工园应急物资D-01仓 (四级-蓝)', value: [78, 24], itemStyle: {color: '#1976D2'} }
                            ];

                            var option = {
                                title: { text: '$chartTitle', left: 'center', textStyle: { fontSize: 14, color: '#333' } },
                                tooltip: { 
                                    trigger: 'item',
                                    formatter: function(params) {
                                        return '<b>' + params.name + '</b><br/>园区网格横坐标X: ' + params.value[0] + ' 米<br/>园区网格纵坐标Y: ' + params.value[1] + ' 米';
                                    }
                                },
                                xAxis: { min: 0, max: 100, show: false },
                                yAxis: { min: 0, max: 100, show: false },
                                grid: { top: '16%', bottom: '8%', left: '6%', right: '6%', containLabel: false },
                                graphic: [
                                    {
                                        type: 'rect',
                                        left: '6%',
                                        top: '16%',
                                        shape: { width: 285, height: 195, r: 10 },
                                        style: { fill: '#F4F7FA', stroke: '#AAB7C4', lineWidth: 1 }
                                    },
                                    {
                                        type: 'polygon',
                                        left: '10%',
                                        top: '23%',
                                        shape: { points: [[8,118],[76,26],[188,42],[246,120],[146,170],[38,158]] },
                                        style: { fill: '#E7EEF5', stroke: '#7D8DA0', lineWidth: 1 }
                                    },
                                    {
                                        type: 'rect',
                                        left: '16%',
                                        top: '31%',
                                        shape: { width: 66, height: 42, r: 4 },
                                        style: { fill: '#FBE9E7', stroke: '#D32F2F', lineWidth: 1 }
                                    },
                                    {
                                        type: 'rect',
                                        left: '50%',
                                        top: '38%',
                                        shape: { width: 78, height: 46, r: 4 },
                                        style: { fill: '#FFF3E0', stroke: '#F57C00', lineWidth: 1 }
                                    },
                                    {
                                        type: 'rect',
                                        left: '31%',
                                        top: '58%',
                                        shape: { width: 72, height: 34, r: 4 },
                                        style: { fill: '#FFFDE7', stroke: '#FBC02D', lineWidth: 1 }
                                    },
                                    {
                                        type: 'line',
                                        left: '14%',
                                        top: '49%',
                                        shape: { x1: 0, y1: 0, x2: 218, y2: 56 },
                                        style: { stroke: '#8EA1B2', lineWidth: 3, lineDash: [6, 5] }
                                    },
                                    {
                                        type: 'text',
                                        left: '11%',
                                        top: '19%',
                                        style: { text: '星澜化工园示意底图', fill: '#4E5D6C', font: '12px sans-serif' }
                                    },
                                    {
                                        type: 'text',
                                        left: '17%',
                                        top: '28%',
                                        style: { text: '北罐组', fill: '#8A2E26', font: '10px sans-serif' }
                                    },
                                    {
                                        type: 'text',
                                        left: '51%',
                                        top: '35%',
                                        style: { text: '反应装置区', fill: '#9A5B00', font: '10px sans-serif' }
                                    },
                                    {
                                        type: 'text',
                                        left: '32%',
                                        top: '55%',
                                        style: { text: '装卸泊位', fill: '#8A7500', font: '10px sans-serif' }
                                    }
                                ],
                                series: [{
                                    type: 'scatter',
                                    coordinateSystem: 'cartesian2d',
                                    symbolSize: 15,
                                    data: localParkPoints,
                                    label: { 
                                        show: true, 
                                        formatter: function(p) {
                                            return p.name.substring(0, 6) + '...';
                                        }, 
                                        position: 'top', 
                                        fontSize: 9, 
                                        color: '#555' 
                                    }
                                }]
                            };
                            myChart.setOption(option);
                            myChart.on('click', function(params) {
                                if (params.seriesType === 'scatter' && window.AndroidRiskBridge) {
                                    window.AndroidRiskBridge.showRiskPoint(JSON.stringify(params.data || {}));
                                }
                            });
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            }
        })
    }

    selectedRiskPoint?.let { point ->
        AlertDialog(
            onDismissRequest = { selectedRiskPoint = null },
            title = { Text(point.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiskPointInfoLine("风险等级", point.riskLevel)
                    RiskPointInfoLine("区域位置", point.area)
                    RiskPointInfoLine("风险描述", point.riskDescription)
                    RiskPointInfoLine("管控建议", point.controlSuggestion)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRiskPoint = null }) { Text("关闭") }
            }
        )
    }
}

private data class RiskPointDialogData(
    val name: String,
    val riskLevel: String,
    val area: String,
    val riskDescription: String,
    val controlSuggestion: String
)

private class RiskPointBridge(private val onRiskPointClick: (String) -> Unit) {
    @JavascriptInterface
    fun showRiskPoint(payload: String) {
        onRiskPointClick(payload)
    }
}

private fun parseRiskPoint(payload: String): RiskPointDialogData {
    return runCatching {
        val data = JsonParser.parseString(payload).asJsonObject
        val name = data.get("name")?.asString.orEmpty().ifBlank { "未命名风险点" }
        val coordinates = data.getAsJsonArray("value")
        val area = data.get("area")?.asString
            ?: coordinates?.let {
                val x = if (it.size() > 0) it[0].asString else "-"
                val y = if (it.size() > 1) it[1].asString else "-"
                "园区网格 X=${x}m，Y=${y}m"
            }
            ?: "位置待补充"
        val level = data.get("riskLevel")?.asString ?: when {
            name.contains("一级") || name.contains("红") -> "极高风险"
            name.contains("二级") || name.contains("橙") -> "高风险"
            name.contains("三级") || name.contains("黄") -> "中风险"
            name.contains("四级") || name.contains("蓝") -> "低风险"
            else -> "待评估"
        }
        RiskPointDialogData(
            name = name,
            riskLevel = level,
            area = area,
            riskDescription = data.get("riskDescription")?.asString ?: "该点位为园区危险源空间定位点，请结合现场底账核验风险状态。",
            controlSuggestion = data.get("controlSuggestion")?.asString ?: "核对责任人、监测报警和应急物资，按风险等级落实巡检与闭环处置。"
        )
    }.getOrElse {
        RiskPointDialogData("风险点", "待评估", "位置解析失败", "点位数据不完整", "请联系值班人员核验现场底账")
    }
}

@Composable
private fun RiskPointInfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
