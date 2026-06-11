package com.young.sample.adbtools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.lib.adb.transport.AdbSession
import com.young.sample.adbtools.ui.viewmodel.ShellLine
import com.young.sample.adbtools.ui.viewmodel.ShellViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Shell 终端页。
 *
 * 提供命令行交互界面，连接到目标设备执行 shell 命令。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    serial: String,
    session: AdbSession,
    onBack: () -> Unit,
    shellViewModel: ShellViewModel = viewModel(
        key = "shell-$serial",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ShellViewModel(serial, session) as T
            }
        }
    )
) {
    val outputLines by shellViewModel.outputLines.collectAsState()
    val isRunning by shellViewModel.isRunning.collectAsState()
    val error by shellViewModel.error.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // 自动滚动到底部
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shell", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = serial,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { shellViewModel.clearOutput() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "清除输出")
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
            // 终端输出区域
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E) // 终端黑色背景
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(outputLines) { line ->
                        TerminalLine(line)
                    }

                    // 底部留白
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            // 错误提示
            if (error != null) {
                Text(
                    text = error ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 输入区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 命令行输入框
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(12.dp)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp &&
                                        event.key == Key.Enter
                                    ) {
                                        executeCommand(
                                            inputText,
                                            shellViewModel
                                        ) {
                                            inputText = ""
                                            focusManager.clearFocus()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    executeCommand(
                                        inputText,
                                        shellViewModel
                                    ) {
                                        inputText = ""
                                        focusManager.clearFocus()
                                    }
                                }
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text(
                                        "输入命令 (如: ls -la)",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // 发送按钮
                    FilledIconButton(
                        onClick = {
                            executeCommand(inputText, shellViewModel) {
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        },
                        enabled = inputText.isNotBlank() && !isRunning
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 执行命令。
 */
private fun executeCommand(
    command: String,
    shellViewModel: ShellViewModel,
    onDone: () -> Unit
) {
    if (command.isBlank()) return
    shellViewModel.executeCommand(command.trim())
    onDone()
}

/**
 * 终端输出行。
 */
@Composable
private fun TerminalLine(line: ShellLine) {
    val color = when {
        line.isError -> Color(0xFFFF6B6B) // 红色
        line.isSystem -> Color(0xFF69DB8B) // 绿色
        line.command -> Color(0xFF69DB8B) // 绿色（命令）
        else -> Color(0xFFE0E0E0) // 浅灰（普通输出）
    }

    Text(
        text = line.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 1.dp)
    )
}
