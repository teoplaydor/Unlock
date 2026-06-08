package com.unlock.ui.autostart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        val expanded: Set<String> = emptySet(),
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

    fun toggleExpand(pkg: String) = _state.update {
        it.copy(expanded = if (pkg in it.expanded) it.expanded - pkg else it.expanded + pkg)
    }

    /** Reliable no-root autostart block (appops + restricted bucket + stopped-state). */
    fun stopAutostart(app: AutostartApp) = act(app, "Autostart stopped for ${app.label}") {
        ServiceLocator.appActions.stopAutostart(app.packageName)
    }

    /** Stronger: disable the whole app (reversible). */
    fun disableApp(app: AutostartApp) = act(app, "Disabled ${app.label}") {
        ServiceLocator.appActions.disable(app.packageName)
    }

    private fun act(app: AutostartApp, okMsg: String, block: suspend () -> com.unlock.shizuku.ShellResult) {
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
            val r = block()
            _state.update {
                it.copy(
                    busy = it.busy - app.packageName,
                    message = if (r.success) okMsg else "Failed: ${r.text.take(140)}",
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
