package com.young.sample.adbtools.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.young.sample.adbtools.ui.viewmodel.InputViewModel

/**
 * 输入模拟页面 — 支持点击、滑动、按键、文本输入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(
    viewModel: InputViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("输入模拟") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 点击区域
            TapSection(viewModel, state)

            // 滑动区域
            SwipeSection(viewModel, state)

            // 按键区域
            KeySection(viewModel, state)

            // 文本输入区域
            TextSection(viewModel, state)
        }
    }
}

@Composable
private fun TapSection(viewModel: InputViewModel, state: InputViewModel.UiState) {
    SectionCard("模拟点击") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.tapForm.x,
                onValueChange = { viewModel.updateTapForm(state.tapForm.copy(x = it)) },
                label = { Text("X 坐标") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = state.tapForm.y,
                onValueChange = { viewModel.updateTapForm(state.tapForm.copy(y = it)) },
                label = { Text("Y 坐标") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.performTap() },
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("点击") }
            OutlinedButton(
                onClick = { viewModel.performLongPress() },
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("长按") }
        }
    }
}

@Composable
private fun SwipeSection(viewModel: InputViewModel, state: InputViewModel.UiState) {
    SectionCard("模拟滑动") {
        Text("起始坐标", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.swipeForm.x1,
                onValueChange = { viewModel.updateSwipeForm(state.swipeForm.copy(x1 = it)) },
                label = { Text("X1") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = state.swipeForm.y1,
                onValueChange = { viewModel.updateSwipeForm(state.swipeForm.copy(y1 = it)) },
                label = { Text("Y1") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("结束坐标", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.swipeForm.x2,
                onValueChange = { viewModel.updateSwipeForm(state.swipeForm.copy(x2 = it)) },
                label = { Text("X2") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = state.swipeForm.y2,
                onValueChange = { viewModel.updateSwipeForm(state.swipeForm.copy(y2 = it)) },
                label = { Text("Y2") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        OutlinedTextField(
            value = state.swipeForm.durationMs,
            onValueChange = { viewModel.updateSwipeForm(state.swipeForm.copy(durationMs = it)) },
            label = { Text("持续时间 (毫秒)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { viewModel.performSwipe() },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("滑动") }
    }
}

@Composable
private fun KeySection(viewModel: InputViewModel, state: InputViewModel.UiState) {
    SectionCard("模拟按键") {
        // 常用按键
        val commonKeys = listOf(
            "HOME" to com.young.lib.adb.service.InputService.KEYCODE_HOME,
            "返回" to com.young.lib.adb.service.InputService.KEYCODE_BACK,
            "多任务" to com.young.lib.adb.service.InputService.KEYCODE_APP_SWITCH,
            "回车" to com.young.lib.adb.service.InputService.KEYCODE_ENTER,
            "音量+" to com.young.lib.adb.service.InputService.KEYCODE_VOLUME_UP,
            "音量-" to com.young.lib.adb.service.InputService.KEYCODE_VOLUME_DOWN,
            "电源" to com.young.lib.adb.service.InputService.KEYCODE_POWER,
            "菜单" to com.young.lib.adb.service.InputService.KEYCODE_MENU
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonKeys.chunked(4).forEach { row ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, code) ->
                        OutlinedButton(
                            onClick = { viewModel.performKey(code) },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("自定义按键码", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.keyForm.keyCode,
                onValueChange = { viewModel.updateKeyForm(state.keyForm.copy(keyCode = it)) },
                label = { Text("KeyCode") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { viewModel.performCustomKey() },
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f)
            ) { Text("发送") }
        }
    }
}

@Composable
private fun TextSection(viewModel: InputViewModel, state: InputViewModel.UiState) {
    SectionCard("模拟文本输入") {
        OutlinedTextField(
            value = state.textForm.text,
            onValueChange = { viewModel.updateTextForm(state.textForm.copy(text = it)) },
            label = { Text("文本内容（英文字符）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Button(
            onClick = { viewModel.performText() },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("输入文本") }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}
