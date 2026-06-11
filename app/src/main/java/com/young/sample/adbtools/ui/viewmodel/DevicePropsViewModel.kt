package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.young.lib.adb.model.AdbException
import com.young.lib.adb.service.ShellService
import com.young.lib.adb.transport.AdbSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设备属性查询 ViewModel。
 */
class DevicePropsViewModel(
    private val session: AdbSession
) : ViewModel() {

    private val shellService = ShellService(session)

    enum class PropCategory(val label: String, val prefix: String) {
        ALL("全部", ""),
        SYSTEM("系统", "ro."),
        NETWORK("网络", "net."),
        DISPLAY("显示", "ro.sf|debug."),
        STORAGE("存储", "sys."),
        SECURITY("安全", "ro.build|security")
    }

    data class UiState(
        val rawProps: Map<String, String> = emptyMap(),
        val filteredProps: Map<String, String> = emptyMap(),
        val searchQuery: String = "",
        val selectedCategory: PropCategory = PropCategory.ALL,
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            Log.d(TAG, "获取设备属性...")
            try {
                val result = shellService.exec("getprop")
                Log.i(TAG, "getprop 完成，解析到 ${result.stdout.lines().size} 行")
                val props = parseGetprop(result.stdout)
                _state.update {
                    it.copy(
                        rawProps = props,
                        isLoading = false
                    )
                }
                applyFilters()
            } catch (e: AdbException.ServiceError) {
                _state.update {
                    it.copy(error = "服务错误: ${e.message}", isLoading = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = "获取属性失败: ${e.localizedMessage ?: e.message}",
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

    fun selectCategory(category: PropCategory) {
        _state.update { it.copy(selectedCategory = category) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _state.value
        val query = state.searchQuery.lowercase().trim()
        val category = state.selectedCategory

        var result = state.rawProps

        // 分类过滤
        if (category != PropCategory.ALL) {
            result = result.filterKeys { key ->
                category.prefix.split("|").any { key.startsWith(it) }
            }
        }

        // 搜索过滤
        if (query.isNotEmpty()) {
            result = result.filterKeys { key ->
                key.lowercase().contains(query)
            }
        }

        _state.update { it.copy(filteredProps = result) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "ADB-PropsVM"

        /**
         * 解析 getprop 输出，格式: [key]: [value]
         */
        fun parseGetprop(output: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val regex = Regex("""\[(.+?)\]:\s*\[(.*?)\]""")
            output.lines().forEach { line ->
                val match = regex.find(line)
                if (match != null) {
                    result[match.groupValues[1]] = match.groupValues[2]
                }
            }
            return result
        }
    }
}
