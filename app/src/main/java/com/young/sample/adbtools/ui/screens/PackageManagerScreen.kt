package com.young.sample.adbtools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.young.sample.adbtools.ui.viewmodel.PackageManagerViewModel

/**
 * 包管理测试页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerScreen(
    serial: String,
    viewModel: PackageManagerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    // 成功消息
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // 包详情对话框
    if (showDetailDialog && state.selectedPackage != null) {
        val pkg = state.selectedPackage!!
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = { Text(pkg.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    DetailRow("类型", if (pkg.isSystem) "系统应用" else "三方应用")
                    DetailRow("路径", pkg.sourceDir)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDetailDialog = false; showUninstallDialog = true },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("卸载") }
            }
        )
    }

    // 卸载确认
    if (showUninstallDialog && state.selectedPackage != null) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("确认卸载?") },
            text = { Text("将卸载 ${state.selectedPackage!!.packageName}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uninstallPackage(state.selectedPackage!!.packageName)
                        showUninstallDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("卸载") }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("包管理", style = MaterialTheme.typography.titleMedium)
                        Text(serial, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
            // 搜索
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                label = { Text("搜索应用") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                singleLine = true
            )

            // 过滤 chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PackageManagerViewModel.PackageFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.selectFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${state.filteredPackages.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // 加载指示器
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }

            // 错误
            AnimatedVisibility(state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.error ?: "",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.clearMessages() }) { Text("关闭") }
                    }
                }
            }

            // 包列表
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.filteredPackages, key = { it.packageName }) { pkg ->
                    PackageRow(
                        pkg = pkg,
                        onClick = {
                            viewModel.selectPackage(pkg)
                            showDetailDialog = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageRow(
    pkg: PackageManagerViewModel.PackageInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (pkg.isSystem) Icons.Filled.Smartphone else Icons.Filled.Inventory2,
            contentDescription = null,
            tint = if (pkg.isSystem) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.packageName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.Info,
            contentDescription = "详情",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
