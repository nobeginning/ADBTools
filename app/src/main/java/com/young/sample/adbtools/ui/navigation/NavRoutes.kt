package com.young.sample.adbtools.ui.navigation

/**
 * 导航路由定义。
 */
object NavRoutes {
    // 主页面
    const val DEVICE_LIST = "device_list"
    const val SHELL = "shell/{serial}"

    // 测试工具
    const val TEST_HUB = "test_hub"
    const val TEST_SERVER = "test/server"
    const val TEST_FILES = "test/files/{serial}"
    const val TEST_FORWARD = "test/forward/{serial}"
    const val TEST_PROPS = "test/props/{serial}"
    const val TEST_PACKAGES = "test/packages/{serial}"
    const val TEST_SCREENSHOT = "test/screenshot/{serial}"
    const val TEST_LOGCAT = "test/logcat/{serial}"
    const val TEST_INPUT = "test/input/{serial}"
    const val TEST_UI_AUTOMATOR = "test/uiautomator/{serial}"
    const val TEST_ACCESSIBILITY = "test/accessibility/{serial}"

    fun shellRoute(serial: String): String = "shell/$serial"
    fun testFilesRoute(serial: String): String = "test/files/$serial"
    fun testForwardRoute(serial: String): String = "test/forward/$serial"
    fun testPropsRoute(serial: String): String = "test/props/$serial"
    fun testPackagesRoute(serial: String): String = "test/packages/$serial"
    fun testScreenshotRoute(serial: String): String = "test/screenshot/$serial"
    fun testLogcatRoute(serial: String): String = "test/logcat/$serial"
    fun testInputRoute(serial: String): String = "test/input/$serial"
    fun testUiAutomatorRoute(serial: String): String = "test/uiautomator/$serial"
    fun testAccessibilityRoute(serial: String): String = "test/accessibility/$serial"
}
