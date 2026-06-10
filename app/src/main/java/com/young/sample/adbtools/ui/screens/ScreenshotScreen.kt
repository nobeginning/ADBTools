package com.young.sample.adbtools.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.young.sample.adbtools.ui.viewmodel.ScreenshotViewModel

/**
 * 截图预览测试页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotScreen(
    serial: String,
    viewModel: ScreenshotViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("截图", style = MaterialTheme.typography.titleMedium)
                        Text(serial, style = MaterialTheme.typography.labelSmall)
                    }
                },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            // 截图区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (state.isCapturing) {
                    CircularProgressIndicator()
                } else if (state.bitmap != null) {
                    Image(
                        bitmap = state.bitmap!!.asImageBitmap(),
                        contentDescription = "设备截图",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                } else if (state.error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "点击「截取」获取设备屏幕",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 尺寸信息
            if (state.width > 0) {
                Text(
                    text = "${state.width} × ${state.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 控制栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.capture() },
                    enabled = !state.isCapturing
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("截取")
                }

                // 自动刷新选择器
                AutoRefreshSelector(
                    currentInterval = state.autoRefreshIntervalMs,
                    isRunning = state.autoRefreshIntervalMs > 0,
                    onSelect = { viewModel.startAutoRefresh(it) },
                    onStop = { viewModel.stopAutoRefresh() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoRefreshSelector(
    currentInterval: Long,
    isRunning: Boolean,
    onSelect: (Long) -> Unit,
    onStop: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val intervals = listOf(0L to "手动", 1000L to "1 秒", 3000L to "3 秒", 5000L to "5 秒")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = intervals.find { it.first == currentInterval }?.second ?: "手动",
            onValueChange = {},
            readOnly = true,
            leadingIcon = {
                Icon(
                    if (isRunning) Icons.Filled.Timer else Icons.Filled.TimerOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier.width(120.dp).menuAnchor(),
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            intervals.forEach { (ms, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        if (ms == 0L) onStop() else onSelect(ms)
                        expanded = false
                    }
                )
            }
        }
    }
}
