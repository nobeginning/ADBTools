package com.young.sample.adbtools.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.young.sample.adbtools.ui.screens.DeviceListScreen
import com.young.sample.adbtools.ui.screens.ShellScreen
import com.young.sample.adbtools.ui.viewmodel.DeviceViewModel

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
        composable(NavRoutes.DEVICE_LIST) {
            DeviceListScreen(
                viewModel = deviceViewModel,
                onDeviceClick = { serial ->
                    // 如果未连接则先连接
                    if (!deviceViewModel.isConnected(serial)) {
                        deviceViewModel.connectToDevice(serial)
                    }
                    navController.navigate(NavRoutes.shellRoute(serial))
                },
                onRefresh = { deviceViewModel.refreshDevices() }
            )
        }

        composable(
            route = NavRoutes.SHELL,
            arguments = listOf(
                navArgument("serial") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val serial = backStackEntry.arguments?.getString("serial") ?: return@composable
            val session = deviceViewModel.getSession(serial)

            if (session != null) {
                ShellScreen(
                    serial = serial,
                    session = session,
                    onBack = { navController.popBackStack() }
                )
            } else {
                // Session 不存在，回到设备列表
                navController.popBackStack()
            }
        }
    }
}
