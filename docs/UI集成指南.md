# UI 集成指南

## 概述

`:adb-lib` 是纯 JVM 库，与 UI 框架无关。`:app` 模块作为 Android 层，负责：
- 提供 Compose UI
- 管理 ViewModel 生命周期
- 处理 Android 特有的权限和文件操作
- 将 Bitmap 等 Android 类型与库的数据类型转换

## ViewModel 使用示例

### 设备列表 ViewModel

```kotlin
class DeviceListViewModel : ViewModel() {
    
    private val adbClient = AdbClient(
        AdbConfig(
            host = "10.0.2.2",  // 模拟器 → 主机 localhost
            port = 5037
        )
    )
    
    private val _devices = MutableStateFlow<List<AdbDevice>>(emptyList())
    val devices: StateFlow<List<AdbDevice>> = _devices.asStateFlow()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    fun connect() {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                adbClient.connect()
                _connectionState.value = ConnectionState.CONNECTED
                
                // 持续追踪设备变化
                adbClient.trackDevices().collect { deviceList ->
                    _devices.value = deviceList
                }
            } catch (e: AdbException.ConnectionFailed) {
                _connectionState.value = ConnectionState.ERROR
                _error.value = "连接失败: ${e.message}"
            }
        }
    }
    
    override fun onCleared() {
        adbClient.close()
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}
```

### Shell 执行 ViewModel

```kotlin
class ShellViewModel : ViewModel() {
    
    private val adbClient: AdbClient = ... // 注入或共享
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()
    
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()
    
    fun executeCommand(deviceSerial: String, command: String) {
        viewModelScope.launch {
            _isExecuting.value = true
            try {
                val session = adbClient.openSession(deviceSerial)
                val shellService = ShellService(session)
                val result = shellService.exec(command)
                _output.value = result.stdout
                
                if (result.exitCode != 0) {
                    _output.value += "\n[Exit code: ${result.exitCode}]\n${result.stderr}"
                }
            } catch (e: AdbException.CommandFailed) {
                _output.value = "命令执行失败: ${e.message}"
            } finally {
                _isExecuting.value = false
            }
        }
    }
}
```

### 交互式 Shell ViewModel

```kotlin
class InteractiveShellViewModel : ViewModel() {
    
    private var shell: InteractiveShell? = null
    
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    
    private val _status = MutableStateFlow("未连接")
    val status: StateFlow<String> = _status.asStateFlow()
    
    fun connect(serial: String) {
        viewModelScope.launch {
            try {
                val session = adbClient.openSession(serial)
                val shellService = ShellService(session)
                shell = shellService.interactive()
                
                _status.value = "已连接"
                
                // 收集输出
                shell?.output?.collect { data ->
                    when (data) {
                        is ShellData.Stdout -> _lines.value += data.data.decodeToString()
                        is ShellData.Stderr -> _lines.value += "[ERR] ${data.data.decodeToString()}"
                        is ShellData.Exit -> _status.value = "已退出 (code=${data.code})"
                    }
                }
            } catch (e: Exception) {
                _status.value = "连接失败: ${e.message}"
            }
        }
    }
    
    fun sendInput(input: String) {
        viewModelScope.launch {
            shell?.writeStdin(input + "\n")
        }
    }
    
    override fun onCleared() {
        viewModelScope.launch { shell?.close() }
    }
}
```

### 文件传输 ViewModel

```kotlin
class FileTransferViewModel : ViewModel() {
    private val _progress = MutableStateFlow(0f)  // 0.0 ~ 1.0
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    
    fun pushFile(serial: String, localPath: Path, remotePath: String) {
        viewModelScope.launch {
            _status.value = "正在推送..."
            try {
                val session = adbClient.openSession(serial)
                val sync = SyncService(session)
                // SyncService 实现内部可提供进度回调
                sync.push(localPath, remotePath)
                _status.value = "推送完成"
            } catch (e: Exception) {
                _status.value = "推送失败: ${e.message}"
            }
        }
    }
}
```

## 状态管理

建议使用 `StateFlow` + `MutableStateFlow`：

```
ViewModel ──StateFlow──▶ Compose UI
    ▲                        │
    │                        ▼
    │               collectAsState()
    │                        │
    └────── 用户操作 ────────┘
```

```kotlin
@Composable
fun DeviceListScreen(viewModel: DeviceListViewModel) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    
    // ... UI 组件
}
```

## AdbConfig 配置

```kotlin
// 模拟器场景：连接主机的 ADB Server
val config = AdbConfig.EMULATOR  // host="10.0.2.2", port=5037

// 真机场景：WiFi 连接主机 IP
val config = AdbConfig.custom(host = "192.168.1.100", port = 5037)

// 直连设备 adbd：无线调试模式
val config = AdbConfig.custom(host = "192.168.1.101", port = 5555)
```

## Compose UI 组件建议

### 设备列表页面

- `LazyColumn` 显示设备列表
- 每项显示设备 serial、型号、状态（彩色指示灯）
- 点击设备进入设备详情/Shell 页面
- 下拉刷新按钮

### Shell 终端页面

- 深色背景的终端模拟器风格
- 只读输出区域（`SelectionContainer` 可选文字）
- 底部输入框 + 发送按钮
- 支持命令历史（本地）

### 文件管理页面

- 双栏：本地文件 + 远程文件
- 文件列表支持点击进入目录
- 推送/拉取按钮
- 进度条（LinearProgressIndicator）

## 错误处理 UI

```kotlin
// 全局错误提示
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "关闭")
            }
        }
    }
}
```

## 权限注意事项

- **INTERNET 权限**：已由 `activity-compose` 间接引入
- **前台服务**（可选）：长期追踪设备变化时可能需要
- **文件访问**：Android 11+ 使用 SAF 或 `MANAGE_EXTERNAL_STORAGE`
