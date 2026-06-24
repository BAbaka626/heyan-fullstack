package work.xy0712.xz.ui.enterprise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import work.xy0712.xz.ui.chat.HazardPieChart
import work.xy0712.xz.ui.chat.HazardRiskMap
import work.xy0712.xz.viewmodel.EnterpriseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkOverviewScreen(viewModel: EnterpriseViewModel = viewModel()) {
    val enterprises by viewModel.enterprises.collectAsState()
    val riskStats by viewModel.riskLevelStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("园区总览") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard("园区概览") {
                ParkOverviewSummary(enterprises = enterprises, riskStats = riskStats)
            }
            OverviewCard("危险源占比") {
                HazardPieChart(
                    chartTitle = "重大危险源风险级别占比",
                    chartData = riskStats.map { mapOf("value" to it.count, "name" to it.levelName) }
                )
            }
            OverviewCard("园区风险分布") {
                HazardRiskMap(
                    chartTitle = "园区微观库位风险打点",
                    chartData = null
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
