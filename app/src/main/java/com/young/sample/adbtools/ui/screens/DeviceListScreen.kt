package com.young.sample.adbtools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.young.sample.adblib.model.AdbDevice
import com.young.sample.adblib.model.DeviceState
import com.young.sample.adbtools.ui.viewmodel.DeviceViewModel

/**
 * 设备列表页。
 *
 * 显示已连接的 Android 设备列表，并提供连接/刷新操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    viewModel: DeviceViewModel,
    onDeviceClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val error by viewModel.error.collectAsState()
    val serverVersion by viewModel.serverVersion.collectAsState()
    val connectedSerials by viewModel.connectedSerials.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ADB Tools", fontWeight = FontWeight.Bold)
                        if (serverVersion != null) {
                            Text(
                                text = "Server $serverVersion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isScanning
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier
                                .then(
                                    if (isScanning) Modifier else Modifier
                                )
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 错误提示
            AnimatedVisibility(visible = error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 扫描中指示
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 设备统计
            Text(
                text = "发现 ${devices.size} 个设备",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (devices.isEmpty() && !isScanning) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "没有发现设备",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "确保 ADB Server 正在运行并连接了设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("重新扫描")
                        }
                    }
                }
            } else {
                // 设备列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices, key = { it.serial }) { device ->
                        DeviceCard(
                            device = device,
                            isConnected = device.serial in connectedSerials,
                            onClick = {
                                if (device.state == DeviceState.DEVICE) {
                                    onDeviceClick(device.serial)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 设备卡片。
 */
@Composable
private fun DeviceCard(
    device: AdbDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = device.state == DeviceState.DEVICE) { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备图标
            Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = when (device.state) {
                    DeviceState.DEVICE -> MaterialTheme.colorScheme.primary
                    DeviceState.UNAUTHORIZED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(Modifier.width(16.dp))

            // 设备信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.serial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态指示灯
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (device.state) {
                                    DeviceState.DEVICE -> Color(0xFF4CAF50) // 绿色
                                    DeviceState.OFFLINE -> Color(0xFFFFC107) // 黄色
                                    DeviceState.UNAUTHORIZED -> Color(0xFFF44336) // 红色
                                    else -> Color(0xFF9E9E9E) // 灰色
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.state.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 操作按钮
            if (device.state == DeviceState.DEVICE) {
                FilledTonalIconButton(
                    onClick = onClick,
                    enabled = !isConnected
                ) {
                    Icon(
                        Icons.Filled.Terminal,
                        contentDescription = if (isConnected) "已连接" else "打开 Shell",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
