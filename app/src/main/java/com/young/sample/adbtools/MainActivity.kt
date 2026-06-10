package com.young.sample.adbtools

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.young.sample.adbtools.ui.navigation.AppNavigation
import com.young.sample.adbtools.ui.navigation.NavRoutes
import com.young.sample.adbtools.ui.theme.ADBToolsTheme
import com.young.sample.adbtools.ui.viewmodel.DeviceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 沉浸式状态栏：透明系统栏，内容绘制在状态栏和导航栏后方
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 setDecorFitsSystemWindows
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        setContent {
            ADBToolsTheme(dynamicColor = true) {
                ADBToolsApp()
            }
        }
    }
}

@Composable
fun ADBToolsApp() {
    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 仅在顶层页面显示底部导航栏
    val topLevelRoutes = listOf(NavRoutes.DEVICE_LIST, NavRoutes.TEST_HUB)
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // 不消耗系统栏 insets — 各页面内层 Scaffold 自行处理状态栏和导航栏
        // 外层 Scaffold 仅负责 BottomNavigationBar，innerPadding 仅含底栏高度
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == NavRoutes.DEVICE_LIST,
                        onClick = {
                            navController.navigate(NavRoutes.DEVICE_LIST) {
                                popUpTo(NavRoutes.DEVICE_LIST) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                        },
                        label = { Text("设备") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == NavRoutes.TEST_HUB,
                        onClick = {
                            navController.navigate(NavRoutes.TEST_HUB) {
                                popUpTo(NavRoutes.DEVICE_LIST) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(Icons.Filled.Build, contentDescription = null)
                        },
                        label = { Text("工具") }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            deviceViewModel = deviceViewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
