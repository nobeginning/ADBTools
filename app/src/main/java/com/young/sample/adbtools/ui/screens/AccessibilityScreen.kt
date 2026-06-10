package com.young.sample.adbtools.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.young.sample.adbtools.ui.viewmodel.AccessibilityViewModel

/**
 * 无障碍权限页面。
 *
 * 通过 ADB shell 命令启用/禁用本应用的无障碍服务。
 * 也可跳转系统设置手动操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityScreen(
    viewModel: AccessibilityViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误提示
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("无障碍权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- 状态卡片 ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.isEnabled) Icons.Filled.CheckCircle
                        else Icons.Filled.Accessibility,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = if (state.isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "无障碍服务状态",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (state.isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- 操作按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 检查状态
                OutlinedButton(
                    onClick = { viewModel.checkStatus() },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("检查状态")
                }

                // 开启
                Button(
                    onClick = { viewModel.enable() },
                    enabled = !state.isLoading && !state.isEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Accessibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("开启")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 关闭
                OutlinedButton(
                    onClick = { viewModel.disable() },
                    enabled = !state.isLoading && state.isEnabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("关闭服务")
                }

                // 系统设置
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("系统设置")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- 说明卡片 ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "说明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = """
                            • 「开启」按钮通过 ADB shell 命令启用本应用的无障碍服务
                            • 「关闭」按钮通过 ADB shell 命令关闭无障碍服务
                            • 「系统设置」跳转到系统无障碍设置页面，可手动操作
                            • 无障碍服务启用后可获取前台应用包名、UI 层级等信息
                            • 仅当 ADB 已连接设备时可用
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
