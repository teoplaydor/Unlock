package com.unlock.ui.autostart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.Prefs
import com.unlock.core.ServiceLocator
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One autostarting app (its boot receivers grouped under the parent package). */
data class AutostartApp(
    val packageName: String,
    val label: String,
    val isProtected: Boolean,
    val receivers: List<String>,
)

class AutostartViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val apps: List<AutostartApp> = emptyList(),
        val stopped: Set<String> = emptySet(),
        val busy: Set<String> = emptySet(),
        val shizukuReady: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ShizukuManager.state.collect { s -> _state.update { it.copy(shizukuReady = s == ShizukuManager.State.READY) } }
        }
        viewModelScope.launch {
            Prefs.stoppedAutostart.collect { s -> _state.update { it.copy(stopped = s) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val apps = withContext(Dispatchers.Default) {
                ServiceLocator.appRepository.bootEntries()
                    .groupBy { it.packageName }
                    .map { (pkg, list) ->
                        AutostartApp(
                            packageName = pkg,
                            label = list.first().label,
                            isProtected = list.any { it.isProtected },
                            receivers = list.map { it.receiverClass.substringAfterLast('.') }.distinct(),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _state.update { it.copy(loading = false, apps = apps) }
        }
    }

    /** Toggle: stopped=true blocks autostart (appops+bucket+stopped-state); false restores it. */
    fun setStopped(app: AutostartApp, stopped: Boolean) {
        viewModelScope.launch {
            if (app.isProtected) {
                _state.update { it.copy(message = "Blocked — ${app.packageName} is a protected core package.") }
                return@launch
            }
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "This needs Shizuku.") }
                return@launch
            }
            _state.update { it.copy(busy = it.busy + app.packageName) }
            val actions = ServiceLocator.appActions
            val r = if (stopped) actions.stopAutostart(app.packageName) else actions.restoreAutostart(app.packageName)
            if (r.success) {
                Prefs.setStopped(app.packageName, stopped)
            } else {
                _state.update { it.copy(message = "Failed: ${r.text.take(140)}") }
            }
            _state.update { it.copy(busy = it.busy - app.packageName) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
