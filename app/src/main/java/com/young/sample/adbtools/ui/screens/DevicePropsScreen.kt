package com.young.sample.adbtools.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.young.sample.adbtools.ui.viewmodel.DevicePropsViewModel
import com.young.sample.adbtools.ui.viewmodel.DevicePropsViewModel.PropCategory

/**
 * 设备属性查询测试页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePropsScreen(
    serial: String,
    viewModel: DevicePropsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("设备属性", style = MaterialTheme.typography.titleMedium)
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                label = { Text("搜索属性") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                singleLine = true
            )

            // 分类标签
            PropCategoryTabRow(
                selectedCategory = state.selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) }
            )

            // 属性数量
            Text(
                text = "${state.filteredProps.size} 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // 加载指示器
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }

            // 错误提示
            if (state.error != null) {
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
                        TextButton(onClick = { viewModel.clearError() }) { Text("关闭") }
                    }
                }
            }

            // 属性列表
            val props = state.filteredProps.entries.toList()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(props, key = { it.key }) { (key, value) ->
                    PropRow(key = key, value = value)
                }
                if (props.isEmpty() && !state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (state.searchQuery.isNotEmpty()) "没有匹配的属性" else "加载属性...",
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
private fun PropCategoryTabRow(
    selectedCategory: PropCategory,
    onCategorySelected: (PropCategory) -> Unit
) {
    TabRow(selectedTabIndex = PropCategory.entries.indexOf(selectedCategory)) {
        PropCategory.entries.forEach { category ->
            Tab(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                text = { Text(category.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun PropRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "=",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.55f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
