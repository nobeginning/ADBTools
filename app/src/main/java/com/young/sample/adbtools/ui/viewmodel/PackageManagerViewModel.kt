package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.sample.adblib.model.AdbException
import com.young.sample.adblib.service.ShellService
import com.young.sample.adblib.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 包管理 ViewModel。
 */
class PackageManagerViewModel(
    private val session: AdbSession
) : ViewModel() {

    private val shellService = ShellService(session)

    data class PackageInfo(
        val packageName: String,
        val isSystem: Boolean = false,
        val sourceDir: String = ""
    )

    enum class PackageFilter(val label: String) { ALL("全部"), SYSTEM("系统"), THIRD_PARTY("三方") }

    data class UiState(
        val packages: List<PackageInfo> = emptyList(),
        val filteredPackages: List<PackageInfo> = emptyList(),
        val searchQuery: String = "",
        val filter: PackageFilter = PackageFilter.ALL,
        val selectedPackage: PackageInfo? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val successMessage: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            Log.d(TAG, "获取包列表...")
            try {
                val result = shellService.exec("pm list packages -f")
                Log.i(TAG, "包列表获取成功，解析到 ${result.stdout.lines().size} 行")
                val packages = parsePackageList(result.stdout)
                _state.update { it.copy(packages = packages, isLoading = false) }
                applyFilters()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "获取包列表失败: ${e.localizedMessage ?: e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun selectFilter(filter: PackageFilter) {
        _state.update { it.copy(filter = filter) }
        applyFilters()
    }

    fun selectPackage(pkg: PackageInfo?) {
        _state.update { it.copy(selectedPackage = pkg) }
    }

    fun uninstallPackage(packageName: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                shellService.exec("pm uninstall $packageName")
                _state.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "$packageName 已卸载"
                    )
                }
                refresh()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "卸载失败: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null) }
    }

    private fun applyFilters() {
        val state = _state.value
        var result = state.packages

        // 按过滤条件
        when (state.filter) {
            PackageFilter.SYSTEM -> result = result.filter { it.isSystem }
            PackageFilter.THIRD_PARTY -> result = result.filter { !it.isSystem }
            PackageFilter.ALL -> {}
        }

        // 搜索
        val query = state.searchQuery.lowercase().trim()
        if (query.isNotEmpty()) {
            result = result.filter { it.packageName.lowercase().contains(query) }
        }

        // 字母排序
        result = result.sortedBy { it.packageName.lowercase() }

        _state.update { it.copy(filteredPackages = result) }
    }

    companion object {
        private const val TAG = "ADB-PkgVM"

        /**
         * 解析 pm list packages -f 输出。
         * 格式: package:/system/app/Example.apk=com.example.app
         */
        fun parsePackageList(output: String): List<PackageInfo> {
            return output.lines()
                .filter { it.startsWith("package:") }
                .mapNotNull { line ->
                    val parts = line.removePrefix("package:").split("=", limit = 2)
                    if (parts.size >= 2) {
                        val path = parts[0]
                        val pkg = parts[1].trim()
                        PackageInfo(
                            packageName = pkg,
                            isSystem = path.startsWith("/system"),
                            sourceDir = path
                        )
                    } else null
                }
        }
    }
}
