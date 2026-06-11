package com.young.sample.adbtools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.young.sample.adbtools.ui.viewmodel.FileBrowserViewModel

/**
 * 文件浏览器测试页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    serial: String,
    viewModel: FileBrowserViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var selectedEntryForMenu by remember { mutableStateOf<com.young.lib.adb.model.SyncEntry?>(null) }

    // 文件操作菜单
    if (selectedEntryForMenu != null) {
        val entry = selectedEntryForMenu!!
        AlertDialog(
            onDismissRequest = { selectedEntryForMenu = null },
            title = { Text(entry.name) },
            text = {
                Column {
                    InfoRow("大小", formatSize(entry.size))
                    InfoRow("权限", formatMode(entry.mode))
                    InfoRow("修改时间", formatTime(entry.mtime))
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedEntryForMenu = null }) {
                    Text("关闭")
                }
            },
            dismissButton = {
                if (entry.isDirectory().not()) {
                    TextButton(onClick = {
                        // pull 文件 - 在完整实现中通过 SAF 选择目标路径
                        selectedEntryForMenu = null
                    }) {
                        Text("拉取")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("文件浏览", style = MaterialTheme.typography.titleMedium)
                        Text(serial, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDirectory(state.currentPath) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 路径面包屑
            PathBreadcrumb(
                path = state.currentPath,
                onPathClick = { path -> viewModel.loadDirectory(path) }
            )

            // 加载指示器
            AnimatedVisibility(state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 传输进度
            AnimatedVisibility(state.transferMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(
                        text = state.transferMessage ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 错误提示
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.error ?: "",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                }
            }

            // 文件列表
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // 返回上级目录
                if (state.pathHistory.size > 1) {
                    item(key = "..") {
                        FileRow(
                            name = "..",
                            isDirectory = true,
                            size = 0,
                            onClick = { viewModel.navigateUp() },
                            onLongClick = {}
                        )
                    }
                }

                items(state.entries, key = { entry -> "${state.currentPath}/${entry.name}" }) { entry ->
                    FileRow(
                        name = entry.name,
                        isDirectory = entry.isDirectory(),
                        size = entry.size,
                        onClick = {
                            if (entry.isDirectory()) {
                                viewModel.navigateToDir(entry.name)
                            } else {
                                selectedEntryForMenu = entry
                            }
                        },
                        onLongClick = { selectedEntryForMenu = entry }
                    )
                }

                // 空目录
                if (state.entries.isEmpty() && !state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "目录为空",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PathBreadcrumb(
    path: String,
    onPathClick: (String) -> Unit
) {
    val segments = path.split("/").filter { it.isNotEmpty() }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = { onPathClick("/") },
            label = { Text("/", style = MaterialTheme.typography.labelSmall) }
        )

        var accumulatedPath = ""
        segments.forEachIndexed { index, segment ->
            accumulatedPath += "/$segment"
            Text(
                text = " › ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AssistChip(
                onClick = { onPathClick(accumulatedPath) },
                label = {
                    Text(
                        segment,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    name: String,
    isDirectory: Boolean,
    size: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = if (isDirectory) "目录" else "文件",
            tint = if (isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
            if (!isDirectory) {
                Text(
                    text = formatSize(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isDirectory) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatSize(size: Int): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
    else -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
}

private fun formatMode(mode: Int): String {
    val sb = StringBuilder()
    sb.append(if ((mode and 0x8000) != 0) 'd' else '-')
    sb.append(if ((mode and 0x0100) != 0) 'r' else '-')
    sb.append(if ((mode and 0x0080) != 0) 'w' else '-')
    sb.append(if ((mode and 0x0040) != 0) 'x' else '-')
    sb.append(if ((mode and 0x0020) != 0) 'r' else '-')
    sb.append(if ((mode and 0x0010) != 0) 'w' else '-')
    sb.append(if ((mode and 0x0008) != 0) 'x' else '-')
    sb.append(if ((mode and 0x0004) != 0) 'r' else '-')
    sb.append(if ((mode and 0x0002) != 0) 'w' else '-')
    sb.append(if ((mode and 0x0001) != 0) 'x' else '-')
    return sb.toString()
}

private fun formatTime(mtime: Long): String {
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(mtime * 1000))
    } catch (_: Exception) {
        "$mtime"
    }
}

private fun com.young.lib.adb.model.SyncEntry.isDirectory(): Boolean =
    (mode shr 14) and 1 == 1
