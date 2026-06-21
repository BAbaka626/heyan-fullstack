package work.xy0712.xz.ui.enterprise.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import work.xy0712.xz.data.mock.MockData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportType: String,
    title: String,
    onBackClick: () -> Unit
) {
    val content = rememberReportContent(reportType)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Markdown(
                content = content,
                modifier = Modifier.fillMaxSize(),
                loading = { modifier ->
                    Box(modifier, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = { modifier ->
                    Box(modifier, contentAlignment = Alignment.Center) {
                        Text(
                            "渲染失败",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun rememberReportContent(reportType: String): String {
    return MockData.getMockReportContent(reportType)
}
