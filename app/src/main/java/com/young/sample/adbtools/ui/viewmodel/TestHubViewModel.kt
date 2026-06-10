package com.young.sample.adbtools.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 测试工具集散页 ViewModel。
 * 维护当前选中的设备 serial，不直接依赖 adb-lib。
 */
class TestHubViewModel : ViewModel() {

    private val _selectedSerial = MutableStateFlow<String?>(null)
    val selectedSerial: StateFlow<String?> = _selectedSerial.asStateFlow()

    fun selectSerial(serial: String) {
        _selectedSerial.value = serial
    }
}
