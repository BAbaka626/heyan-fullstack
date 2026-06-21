package work.xy0712.xz.ui.enterprise.disposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import work.xy0712.xz.data.model.IssueCreateRequest
import work.xy0712.xz.viewmodel.EnterpriseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueFormScreen(
    enterpriseName: String,
    enterpriseId: String,
    onBackClick: () -> Unit,
    viewModel: EnterpriseViewModel = viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()

    var issueTitle by remember { mutableStateOf("") }
    var issueLevel by remember { mutableStateOf("一般") }
    var description by remember { mutableStateOf("") }
    var rectifyRequirement by remember { mutableStateOf("") }
    var rectifyDeadline by remember { mutableStateOf("") }

    val levels = listOf("一般", "较大", "重大")

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("问题登记") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = enterpriseName,
                onValueChange = { },
                label = { Text("所属企业") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true
            )

            OutlinedTextField(
                value = issueTitle,
                onValueChange = { issueTitle = it },
                label = { Text("问题标题 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("问题等级", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { level ->
                    androidx.compose.material3.FilterChip(
                        selected = issueLevel == level,
                        onClick = { issueLevel = level },
                        label = { Text(level) }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("问题描述") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            OutlinedTextField(
                value = rectifyRequirement,
                onValueChange = { rectifyRequirement = it },
                label = { Text("整改要求") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            OutlinedTextField(
                value = rectifyDeadline,
                onValueChange = { rectifyDeadline = it },
                label = { Text("整改期限 (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (issueTitle.isBlank()) return@Button
                    viewModel.submitIssue(
                        IssueCreateRequest(
                            enterpriseName = enterpriseName,
                            issueTitle = issueTitle,
                            issueLevel = issueLevel,
                            description = description,
                            rectifyRequirement = rectifyRequirement,
                            rectifyDeadline = rectifyDeadline
                        ),
                        onSuccess = onBackClick
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && issueTitle.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("提交问题单")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
