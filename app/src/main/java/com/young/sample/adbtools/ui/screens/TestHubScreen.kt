package com.young.sample.adbtools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 测试工具项定义。
 */
@Immutable
private data class TestTool(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val requireDevice: Boolean = true,
    val routeFactory: (String) -> String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestTool) return false
        return label == other.label
    }
    override fun hashCode(): Int = label.hashCode()
}

/** 静态工具列表，避免每次 recomposition 重建 */
private val testTools = listOf(
    TestTool("Server 信息", "ADB Server 状态与版本", Icons.Filled.Info, requireDevice = false) { "test/server" },
    TestTool("文件浏览器", "浏览设备文件系统", Icons.Filled.Folder) { serial -> "test/files/$serial" },
    TestTool("端口转发", "管理端口转发规则", Icons.Filled.SwapHoriz) { serial -> "test/forward/$serial" },
    TestTool("设备属性", "查看系统属性 getprop", Icons.Filled.Settings) { serial -> "test/props/$serial" },
    TestTool("包管理", "查看与管理应用包", Icons.Filled.Inventory2) { serial -> "test/packages/$serial" },
    TestTool("截图", "设备屏幕截图", Icons.Filled.CameraAlt) { serial -> "test/screenshot/$serial" },
    TestTool("Logcat", "实时日志查看器", Icons.Filled.Terminal) { serial -> "test/logcat/$serial" },
    TestTool("输入模拟", "点击/滑动/按键/文本", Icons.Filled.AdsClick) { serial -> "test/input/$serial" },
    TestTool("UI 树", "dump 当前页面 UI 层级", Icons.Filled.AccountTree) { serial -> "test/uiautomator/$serial" },
    TestTool("无障碍权限", "开启/关闭无障碍服务", Icons.Filled.Accessibility) { serial -> "test/accessibility/$serial" }
)

/**
 * 测试工具集散页。
 * 显示可用设备和测试工具项网格，点击工具项导航到对应测试页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestHubScreen(
    availableSerials: List<String>,
    selectedSerial: String?,
    onSerialSelected: (String) -> Unit,
    onNavigateTo: (String) -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ADB 测试工具") },
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
                .padding(16.dp)
        ) {
            // 设备选择器
            DeviceSelector(
                serials = availableSerials,
                selectedSerial = selectedSerial,
                onSerialSelected = onSerialSelected,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 提示
            if (availableSerials.isEmpty()) {
                Text(
                    text = "没有已连接的设备。请先在「设备」页面连接设备。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 工具项网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(testTools, key = { it.label }) { tool ->
                    val enabled = !tool.requireDevice || selectedSerial != null
                    ToolCard(
                        tool = tool,
                        enabled = enabled,
                        onClick = {
                            val route = if (tool.requireDevice) {
                                tool.routeFactory(selectedSerial!!)
                            } else {
                                tool.routeFactory("")
                            }
                            onNavigateTo(route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: TestTool,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                modifier = Modifier.size(36.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tool.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}

/**
 * 设备选择器下拉组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelector(
    serials: List<String>,
    selectedSerial: String?,
    onSerialSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && serials.isNotEmpty(),
        onExpandedChange = { if (serials.isNotEmpty()) expanded = it }
    ) {
        OutlinedTextField(
            value = selectedSerial ?: "请选择设备",
            onValueChange = {},
            readOnly = true,
            label = { Text("目标设备") },
            trailingIcon = {
                if (serials.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = modifier.menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded && serials.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            serials.forEach { serial ->
                DropdownMenuItem(
                    text = { Text(serial) },
                    onClick = {
                        onSerialSelected(serial)
                        expanded = false
                    }
                )
            }
        }
    }
}
