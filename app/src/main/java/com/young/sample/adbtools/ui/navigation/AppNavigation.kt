package com.young.sample.adbtools.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.young.lib.adb.transport.AdbSession
import com.young.sample.adbtools.ui.screens.AccessibilityScreen
import com.young.sample.adbtools.ui.screens.DeviceListScreen
import com.young.sample.adbtools.ui.screens.DevicePropsScreen
import com.young.sample.adbtools.ui.screens.FileBrowserScreen
import com.young.sample.adbtools.ui.screens.InputScreen
import com.young.sample.adbtools.ui.screens.LogcatScreen
import com.young.sample.adbtools.ui.screens.PackageManagerScreen
import com.young.sample.adbtools.ui.screens.ScreenshotScreen
import com.young.sample.adbtools.ui.screens.ShellScreen
import com.young.sample.adbtools.ui.screens.TestHubScreen
import com.young.sample.adbtools.ui.screens.UiAutomatorScreen
import com.young.sample.adbtools.ui.viewmodel.AccessibilityViewModel
import com.young.sample.adbtools.ui.viewmodel.DevicePropsViewModel
import com.young.sample.adbtools.ui.viewmodel.DeviceViewModel
import com.young.sample.adbtools.ui.viewmodel.FileBrowserViewModel
import com.young.sample.adbtools.ui.viewmodel.InputViewModel
import com.young.sample.adbtools.ui.viewmodel.LogcatViewModel
import com.young.sample.adbtools.ui.viewmodel.PackageManagerViewModel
import com.young.sample.adbtools.ui.viewmodel.ScreenshotViewModel
import com.young.sample.adbtools.ui.viewmodel.TestHubViewModel
import com.young.sample.adbtools.ui.viewmodel.UiAutomatorViewModel

/**
 * 应用主导航图。
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    deviceViewModel: DeviceViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.DEVICE_LIST,
        modifier = modifier
    ) {
        // ---- 设备列表 ----
        composable(NavRoutes.DEVICE_LIST) {
            DeviceListScreen(
                viewModel = deviceViewModel,
                onConnected = {
                    navController.navigate(NavRoutes.shellRoute("direct"))
                }
            )
        }

        // ---- Shell 终端 ----
        composable(
            route = NavRoutes.SHELL,
            arguments = listOf(navArgument("serial") { type = NavType.StringType })
        ) { backStackEntry ->
            val serial = backStackEntry.arguments?.getString("serial") ?: return@composable
            val session = deviceViewModel.getSession(serial)
            if (session != null) {
                ShellScreen(serial = serial, session = session, onBack = { navController.popBackStack() })
            } else {
                navController.popBackStack()
            }
        }

        // ---- 测试工具主页 ----
        composable(NavRoutes.TEST_HUB) {
            val testHubViewModel: TestHubViewModel = viewModel()
            val serials by deviceViewModel.connectedSerials.collectAsState()
            val selectedSerial by testHubViewModel.selectedSerial.collectAsState()

            TestHubScreen(
                availableSerials = serials.toList(),
                selectedSerial = selectedSerial,
                onSerialSelected = { testHubViewModel.selectSerial(it) },
                onNavigateTo = { route -> navController.navigate(route) }
            )
        }

        // ---- 文件浏览器 ----
        testPageRoute(NavRoutes.TEST_FILES, deviceViewModel, navController, "文件浏览器") { serial, session ->
            val vm: FileBrowserViewModel = viewModel(
                key = "files_$serial",
                factory = viewModelFactory { FileBrowserViewModel(session) }
            )
            FileBrowserScreen(serial = serial, viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- 设备属性 ----
        testPageRoute(NavRoutes.TEST_PROPS, deviceViewModel, navController, "设备属性") { serial, session ->
            val vm: DevicePropsViewModel = viewModel(
                key = "props_$serial",
                factory = viewModelFactory { DevicePropsViewModel(session) }
            )
            DevicePropsScreen(serial = serial, viewModel = vm, onBack = { navController.popBackStack() })
        }
        // ---- 包管理 ----
        testPageRoute(NavRoutes.TEST_PACKAGES, deviceViewModel, navController, "包管理") { serial, session ->
            val vm: PackageManagerViewModel = viewModel(
                key = "packages_$serial",
                factory = viewModelFactory { PackageManagerViewModel(session) }
            )
            PackageManagerScreen(serial = serial, viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- 截图 ----
        testPageRoute(NavRoutes.TEST_SCREENSHOT, deviceViewModel, navController, "截图") { serial, session ->
            val vm: ScreenshotViewModel = viewModel(
                key = "screenshot_$serial",
                factory = viewModelFactory { ScreenshotViewModel(session) }
            )
            ScreenshotScreen(serial = serial, viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- Logcat ----
        testPageRoute(NavRoutes.TEST_LOGCAT, deviceViewModel, navController, "Logcat") { serial, session ->
            val vm: LogcatViewModel = viewModel(
                key = "logcat_$serial",
                factory = viewModelFactory { LogcatViewModel(session) }
            )
            LogcatScreen(serial = serial, viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- 输入模拟 ----
        testPageRoute(NavRoutes.TEST_INPUT, deviceViewModel, navController, "输入模拟") { serial, session ->
            val vm: InputViewModel = viewModel(
                key = "input_$serial",
                factory = viewModelFactory { InputViewModel(session) }
            )
            InputScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- UI 树 ----
        testPageRoute(NavRoutes.TEST_UI_AUTOMATOR, deviceViewModel, navController, "UI 树") { serial, session ->
            val vm: UiAutomatorViewModel = viewModel(
                key = "uiauto_$serial",
                factory = viewModelFactory { UiAutomatorViewModel(session) }
            )
            UiAutomatorScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- 无障碍权限 ----
        testPageRoute(NavRoutes.TEST_ACCESSIBILITY, deviceViewModel, navController, "无障碍权限") { serial, session ->
            val vm: AccessibilityViewModel = viewModel(
                key = "accessibility_$serial",
                factory = viewModelFactory { AccessibilityViewModel(session) }
            )
            AccessibilityScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}

// ---- 辅助 ----

/**
 * 为带 serial 参数的测试页面注册路由。
 * 从 DeviceViewModel 获取 session，校验后传给 content。
 */
private fun NavGraphBuilder.testPageRoute(
    route: String,
    deviceViewModel: DeviceViewModel,
    navController: NavHostController,
    title: String,
    content: @Composable (serial: String, session: AdbSession) -> Unit
) {
    composable(
        route = route,
        arguments = listOf(navArgument("serial") { type = NavType.StringType })
    ) { backStackEntry ->
        val serial = backStackEntry.arguments?.getString("serial") ?: return@composable
        val session = deviceViewModel.getSession(serial)
        if (session != null) {
            content(serial, session)
        } else {
            PlaceholderPage(title, "设备 $serial 未连接，请先连接", { navController.popBackStack() })
        }
    }
}

/**
 * ViewModel 工厂辅助函数，用于创建带构造参数的 ViewModel。
 */
@Composable
inline fun <reified T : ViewModel> viewModelFactory(
    crossinline creator: () -> T
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}

/**
 * 占位页面，在对应 Screen 实现前临时使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
