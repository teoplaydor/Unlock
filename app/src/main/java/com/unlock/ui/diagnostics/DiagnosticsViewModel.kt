package com.unlock.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
import com.unlock.data.DrainEntry
import com.unlock.diagnostics.DiagnosticsReport
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiagnosticsViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val report: DiagnosticsReport? = null,
        val drainers: List<DrainEntry> = emptyList(),
        val gosPackage: String? = null,
        val shizukuReady: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        // Live tick: refresh the cheap sensors (battery/thermal/cpu/mem) automatically so the
        // user never has to press Refresh. The heavy dumpsys drain list stays from the last full refresh.
        viewModelScope.launch {
            while (true) {
                delay(3000)
                runCatching {
                    val light = ServiceLocator.diagnostics.run()
                    _state.update { it.copy(report = light) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    shizukuReady = ShizukuManager.isReady,
                    gosPackage = ServiceLocator.samsungGosPackage(),
                )
            }
            val report = ServiceLocator.diagnostics.run()
            val drainers = if (ShizukuManager.isReady) ServiceLocator.batteryForensics.topDrain() else emptyList()
            _state.update { it.copy(loading = false, report = report, drainers = drainers) }
        }
    }

    fun disableGos() {
        viewModelScope.launch {
            val pkg = _state.value.gosPackage ?: return@launch
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Disabling GOS needs Shizuku.") }
                return@launch
            }
            val r = ServiceLocator.appActions.disable(pkg)
            _state.update {
                it.copy(
                    message = if (r.success) "Samsung GOS throttling disabled." else "Failed: ${r.text.take(120)}",
                    gosPackage = ServiceLocator.samsungGosPackage(),
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
