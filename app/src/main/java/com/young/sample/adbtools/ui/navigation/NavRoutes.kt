package com.young.sample.adbtools.ui.navigation

/**
 * 导航路由定义。
 */
object NavRoutes {
    const val DEVICE_LIST = "device_list"
    const val SHELL = "shell/{serial}"

    fun shellRoute(serial: String): String = "shell/$serial"
}
