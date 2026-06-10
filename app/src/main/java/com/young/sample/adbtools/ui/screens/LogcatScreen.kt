package com.young.sample.adbtools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.sample.adbtools.ui.viewmodel.LogcatViewModel

/**
 * Logcat 实时日志浏览器测试页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(
    serial: String,
    viewModel: LogcatViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by remember { mutableStateOf(false) }
    var bufferExpanded by remember { mutableStateOf(false) }

    // 自动滚动到底部
    LaunchedEffect(state.filteredEntries.size, state.scrollLocked) {
        if (!state.scrollLocked && state.filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(state.filteredEntries.size - 1)
        }
    }

    // 错误提示
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Logcat", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${serial}  |  ${state.filteredEntries.size} 条",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 暂停/播放
                    IconButton(onClick = {
                        if (state.isRunning) viewModel.stop() else viewModel.start()
                    }) {
                        Icon(
                            if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isRunning) "停止" else "开始"
                        )
                    }
                    // 滚动锁定
                    IconButton(onClick = { viewModel.toggleScrollLock() }) {
                        Icon(
                            if (state.scrollLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "滚动锁定"
                        )
                    }
                    // 清除
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清除")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 过滤栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.filterText,
                    onValueChange = { viewModel.setFilter(it) },
                    label = { Text("过滤") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(4.dp))

                // 缓冲区选择
                ExposedDropdownMenuBox(
                    expanded = bufferExpanded,
                    onExpandedChange = { bufferExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.buffer.label,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(90.dp).menuAnchor(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    ExposedDropdownMenu(
                        expanded = bufferExpanded,
                        onDismissRequest = { bufferExpanded = false }
                    ) {
                        LogcatViewModel.LogBuffer.entries.forEach { buf ->
                            DropdownMenuItem(
                                text = { Text(buf.label) },
                                onClick = { viewModel.setBuffer(buf); bufferExpanded = false }
                            )
                        }
                    }
                }

                // 分享/复制
                IconButton(onClick = {
                    val text = viewModel.exportEntries()
                    clipboardManager.setText(AnnotatedString(text))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(20.dp))
                }
            }

            // 级别过滤 chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LogcatViewModel.LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.minLevel.priority <= level.priority,
                        onClick = { viewModel.setMinLevel(level) },
                        label = {
                            Text(
                                level.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (state.minLevel.priority <= level.priority) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // 日志列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                // 调试：始终可见的条目计数
                item(key = "__header__") {
                    Text(
                        text = if (state.isRunning) "📋 ${state.filteredEntries.size} 条日志 (收集中...)"
                               else if (state.filteredEntries.isNotEmpty()) "📋 ${state.filteredEntries.size} 条日志 (已停止)"
                               else "📋 点击 ▶ 开始查看日志",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                itemsIndexed(state.filteredEntries, key = { index, _ -> "log_$index" }) { _, entry ->
                    LogEntryItem(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogcatViewModel.LogEntry) {
    val levelColor = when (entry.level) {
        "V" -> MaterialTheme.colorScheme.outline
        "D" -> MaterialTheme.colorScheme.primary
        "I" -> MaterialTheme.colorScheme.tertiary
        "W" -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        "E" -> MaterialTheme.colorScheme.error
        "F" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 时间
        Text(
            text = entry.timestamp.take(18),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp)
        )
        // PID:TID
        Text(
            text = "${entry.pid.toString().padStart(5)}:${entry.tid.toString().padStart(5)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        // Level
        Text(
            text = entry.level,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = levelColor,
            modifier = Modifier.width(14.dp)
        )
        // Tag
        Text(
            text = entry.tag,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(100.dp).padding(end = 4.dp)
        )
        // Message
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
