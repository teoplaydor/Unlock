package com.unlock.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.Prefs
import com.unlock.core.ServiceLocator
import com.unlock.data.DrainEntry
import com.unlock.data.MemEntry
import com.unlock.diagnostics.BatterySnapshot
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
        val ramConsumers: List<MemEntry> = emptyList(),
        val gosPackage: String? = null,
        val antiThrottleOn: Boolean = false,
        val shizukuReady: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            Prefs.antiThrottle.collect { on -> _state.update { it.copy(antiThrottleOn = on) } }
        }
        refresh()
        // Live tick: refresh the cheap sensors automatically so the user never presses Refresh.
        // The heavy dumpsys lists (drain, RAM) stay from the last full refresh; SoH is preserved.
        viewModelScope.launch {
            while (true) {
                delay(3000)
                runCatching {
                    val light = ServiceLocator.diagnostics.run(ServiceLocator.currentStrings())
                    _state.update { st -> st.copy(report = mergeHealth(light, st.report?.battery)) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, shizukuReady = ShizukuManager.isReady, gosPackage = ServiceLocator.samsungGosPackage())
            }
            var report = ServiceLocator.diagnostics.run(ServiceLocator.currentStrings())
            var drainers = emptyList<DrainEntry>()
            var ram = emptyList<MemEntry>()
            if (ShizukuManager.isReady) {
                val health = ServiceLocator.batteryForensics.batteryHealth()
                report = applyHealth(report, health)
                drainers = ServiceLocator.batteryForensics.topDrain()
                ram = ServiceLocator.memoryRepository.topMemory()
            }
            _state.update { it.copy(loading = false, report = report, drainers = drainers, ramConsumers = ram) }
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

    /** Anti-throttle toggle: ON applies the no-root levers, OFF restores them. Persisted. */
    fun setAntiThrottle(on: Boolean) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Anti-throttle needs Shizuku.") }
                return@launch
            }
            val gos = ServiceLocator.samsungGosPackages()
            val results = if (on) ServiceLocator.appActions.boostPerformance(gos)
            else ServiceLocator.appActions.restorePerformance(gos)
            Prefs.setAntiThrottle(on)
            val ok = results.count { it.success }
            if (on && ok == 0 && results.isNotEmpty()) {
                _state.update { it.copy(message = "This device didn't accept the performance levers.") }
            }
            _state.update { it.copy(gosPackage = ServiceLocator.samsungGosPackage()) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun applyHealth(report: DiagnosticsReport, h: com.unlock.data.BatteryHealth?): DiagnosticsReport {
        val b = report.battery ?: return report
        if (h == null) return report
        return report.copy(
            battery = b.copy(
                sohPercentDirect = h.sohPercent,
                cycleCount = if (h.cycleCount >= 0) h.cycleCount else b.cycleCount,
                chargeFullUah = if (h.chargeFullUah > 0) h.chargeFullUah else b.chargeFullUah,
                chargeFullDesignUah = if (h.chargeFullDesignUah > 0) h.chargeFullDesignUah else b.chargeFullDesignUah,
            ),
        )
    }

    /** Carry the previously-resolved SoH/cycle fields into a freshly-sampled light report. */
    private fun mergeHealth(report: DiagnosticsReport, prev: BatterySnapshot?): DiagnosticsReport {
        val b = report.battery ?: return report
        if (prev == null) return report
        return report.copy(
            battery = b.copy(
                sohPercentDirect = prev.sohPercentDirect,
                cycleCount = if (prev.cycleCount >= 0) prev.cycleCount else b.cycleCount,
                chargeFullUah = if (prev.chargeFullUah > 0) prev.chargeFullUah else b.chargeFullUah,
                chargeFullDesignUah = if (prev.chargeFullDesignUah > 0) prev.chargeFullDesignUah else b.chargeFullDesignUah,
            ),
        )
    }
}
