package com.unlock.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.Format
import com.unlock.core.ServiceLocator
import kotlinx.coroutines.delay
import com.unlock.data.RunningProcess
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RunningViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val processes: List<RunningProcess> = emptyList(),
        val shizukuReady: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ShizukuManager.state.collect { s ->
                _state.update { it.copy(shizukuReady = s == ShizukuManager.State.READY) }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val procs = ServiceLocator.runningRepository.running()
            _state.update { it.copy(loading = false, processes = procs) }
        }
    }

    fun forceStop(pkg: String) = act {
        val before = ServiceLocator.availMemBytes()
        val r = ServiceLocator.appActions.forceStop(pkg)
        delay(600)
        val freed = (ServiceLocator.availMemBytes() - before).coerceAtLeast(0)
        if (r.success) String.format(ServiceLocator.currentStrings().runStoppedFmt, Format.bytes(freed))
        else "Failed: ${r.text.take(120)}"
    }

    fun sleep(pkg: String) = act {
        val before = ServiceLocator.availMemBytes()
        val r = ServiceLocator.appActions.sleep(pkg)
        delay(600)
        val freed = (ServiceLocator.availMemBytes() - before).coerceAtLeast(0)
        if (r.success) String.format(ServiceLocator.currentStrings().runSleptFmt, Format.bytes(freed))
        else "Failed: ${r.text.take(120)}"
    }

    fun stopAutostart(pkg: String) = act {
        val r = ServiceLocator.appActions.stopAutostart(pkg)
        if (r.success) ServiceLocator.currentStrings().autostartStopped else "Failed: ${r.text.take(120)}"
    }

    private fun act(block: suspend () -> String) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = ServiceLocator.currentStrings().tweaksNeedShizuku) }
                return@launch
            }
            val msg = block()
            _state.update { it.copy(message = msg) }
            refresh()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
