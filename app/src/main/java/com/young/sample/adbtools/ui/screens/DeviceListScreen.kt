package com.young.sample.adbtools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.sample.adbtools.ui.viewmodel.ConnectionState
import com.young.sample.adbtools.ui.viewmodel.DeviceViewModel

/**
 * 设备连接页。
 *
 * 显示 adbd 直连状态，提供 host/port 配置和连接控制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    viewModel: DeviceViewModel,
    onConnected: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentHost by viewModel.host.collectAsState()
    val currentPort by viewModel.port.collectAsState()

    var hostInput by remember { mutableStateOf(currentHost) }
    var portInput by remember { mutableStateOf(currentPort) }
    var portError by remember { mutableStateOf(false) }

    val isConnected = connectionState is ConnectionState.Connected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ADB Tools", fontWeight = FontWeight.Bold)
                        Text(
                            text = "$currentHost:$currentPort (直连)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.connect() },
                        enabled = !isScanning
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新连接")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 目标地址配置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "adbd 连接目标",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("主机") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = {
                                portInput = it
                                portError = it.toIntOrNull() == null || it.toInt() !in 1..65535
                            },
                            label = { Text("端口") },
                            isError = portError,
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                    Button(
                        onClick = {
                            val port = portInput.toIntOrNull()
                            if (port != null && port in 1..65535) {
                                viewModel.updateTarget(hostInput, port)
                            }
                        },
                        enabled = hostInput.isNotBlank() && !portError && !isScanning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isConnected) "重新连接" else "连接")
                    }
                }
            }

            // 连接中指示
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 错误提示
            AnimatedVisibility(visible = error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp
                    )
                }
            }

            // 连接状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isConnected) onConnected()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (isConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "已连接"
                                is ConnectionState.Connecting -> "连接中..."
                                is ConnectionState.Error -> "连接失败"
                                is ConnectionState.Disconnected -> "未连接"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (connectionState) {
                                is ConnectionState.Connected -> "$currentHost:$currentPort"
                                is ConnectionState.Connecting -> "正在建立连接..."
                                is ConnectionState.Error -> "点击上方「连接」重试"
                                is ConnectionState.Disconnected -> "点击上方「连接」开始"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isConnected) {
                        TextButton(onClick = onConnected) {
                            Text("进入 Shell →")
                        }
                    }
                }
            }

            // 底部提示
            if (!isConnected && connectionState !is ConnectionState.Connecting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "使用前请确保已开启 adbd TCP 监听：",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "1. USB 连接手机到 PC\n2. 在 PC 终端执行: adb tcpip 5555\n3. 断开 USB，回到本 app 点击「连接」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
