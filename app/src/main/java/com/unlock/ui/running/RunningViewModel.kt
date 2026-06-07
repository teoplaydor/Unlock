package com.unlock.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
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

    fun forceStop(pkg: String) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Force-stop needs Shizuku.") }
                return@launch
            }
            val r = ServiceLocator.appActions.forceStop(pkg)
            _state.update { it.copy(message = if (r.success) "Stopped $pkg" else "Failed: ${r.text.take(120)}") }
            refresh()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
