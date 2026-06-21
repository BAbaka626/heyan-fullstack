package work.xy0712.xz.ui.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import work.xy0712.xz.data.model.EnterpriseSummary
import work.xy0712.xz.data.model.RiskLevelStat
import work.xy0712.xz.viewmodel.EnterpriseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterpriseScreen(
    onEnterpriseClick: (String) -> Unit,
    viewModel: EnterpriseViewModel = viewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val enterprises by viewModel.enterprises.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedRiskLevel by viewModel.selectedRiskLevel.collectAsState()
    val riskStats by viewModel.riskLevelStats.collectAsState()
    val dailyReport by viewModel.dailyReport.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("企业列表与园区看板") }
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EnterpriseListTabContent(
                searchQuery = searchQuery,
                enterprises = enterprises,
                isLoading = isLoading,
                errorMessage = errorMessage,
                selectedRiskLevel = selectedRiskLevel,
                riskStats = riskStats,
                viewModel = viewModel,
                onEnterpriseClick = onEnterpriseClick,
                modifier = Modifier.weight(0.42f)
            )
            ParkStatusContainer(
                enterprises = enterprises,
                riskStats = riskStats,
                report = dailyReport,
                modifier = Modifier.weight(0.58f)
            )
        }
    }
}

/**
 * 优化重构：原有的企业检索明细列表组件（保持原有状态和筛选逻辑原样不动）
 */
@Composable
private fun EnterpriseListTabContent(
    searchQuery: String,
    enterprises: List<EnterpriseSummary>,
    isLoading: Boolean,
    errorMessage: String?,
    selectedRiskLevel: String?,
    riskStats: List<RiskLevelStat>,
    viewModel: EnterpriseViewModel,
    onEnterpriseClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            placeholder = { Text("搜索企业名称、园区、危险源...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Risk level filter chips
        Text(
            text = "按风险等级筛选",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            riskStats.forEach { stat ->
                RiskLevelFilterChip(
                    stat = stat,
                    selected = selectedRiskLevel == stat.levelCode,
                    onClick = {
                        viewModel.onRiskLevelSelected(
                            if (selectedRiskLevel == stat.levelCode) null else stat.levelCode
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Text(
                        text = "共 ${enterprises.size} 家企业",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(enterprises, key = { it.id }) { enterprise ->
                    EnterpriseListItem(
                        enterprise = enterprise,
                        onClick = { onEnterpriseClick(enterprise.id) }
                    )
                }
            }
        }

        errorMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            Icons.Filled.Clear,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskLevelFilterChip(
    stat: RiskLevelStat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = Color(android.graphics.Color.parseColor(stat.colorHex))
    val containerColor = if (selected) baseColor else baseColor.copy(alpha = 0.1f)
    val contentColor = if (selected) Color.White else baseColor

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stat.levelName,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${stat.count}家",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun EnterpriseListItem(
    enterprise: EnterpriseSummary,
    onClick: () -> Unit
) {
    val riskColor = when (enterprise.evaluation_level) {
        "极高风险" -> Color(0xFFD32F2F)
        "高风险" -> Color(0xFFF57C00)
        "中风险" -> Color(0xFFFBC02D)
        "低风险" -> Color(0xFF1976D2)
        else -> Color.Gray
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(riskColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = enterprise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${enterprise.park_name} · ${enterprise.legal_person} · ${enterprise.employee_count}人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = riskColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = enterprise.evaluation_level,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor
                )
            }
        }
    }
}
