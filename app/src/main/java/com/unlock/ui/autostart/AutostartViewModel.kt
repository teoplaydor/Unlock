package com.unlock.ui.autostart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
import com.unlock.data.BootEntry
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutostartViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val entries: List<BootEntry> = emptyList(),
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
            val entries = withContext(Dispatchers.Default) { ServiceLocator.appRepository.bootEntries() }
            _state.update { it.copy(loading = false, entries = entries) }
        }
    }

    fun toggleComponent(entry: BootEntry) {
        viewModelScope.launch {
            // Disabling a protected core package's boot receiver can brick the device or kill
            // emergency alerts. Re-enabling (turning back on) is always safe.
            if (entry.isEnabledComponent && entry.isProtected) {
                _state.update { it.copy(message = "Blocked — ${entry.packageName} is a protected core package.") }
                return@launch
            }
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Disabling autostart needs Shizuku.") }
                return@launch
            }
            val actions = ServiceLocator.appActions
            val r = if (entry.isEnabledComponent) actions.disableComponent(entry.packageName, entry.receiverClass)
            else actions.enableComponent(entry.packageName, entry.receiverClass)
            _state.update { it.copy(message = if (r.success) "Updated ${entry.label}" else "Failed: ${r.text.take(120)}") }
            refresh()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
