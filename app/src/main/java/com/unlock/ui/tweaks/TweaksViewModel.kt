package com.unlock.ui.tweaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
import com.unlock.data.DeviceInfo
import com.unlock.data.Tweak
import com.unlock.data.TweakKind
import com.unlock.data.TweaksCatalog
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TweaksViewModel : ViewModel() {

    data class Row(val tweak: Tweak, val isOn: Boolean?)

    data class UiState(
        val loading: Boolean = true,
        val rows: List<Row> = emptyList(),
        val busy: Set<String> = emptySet(),
        val shizukuReady: Boolean = false,
        val deviceLabel: String = DeviceInfo.label,
        val message: String? = null,
    ) {
        val byCategory: List<Pair<String, List<Row>>>
            get() = rows.groupBy { it.tweak.category }.toList()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val repo get() = ServiceLocator.tweaksRepository

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
            val applicable = TweaksCatalog.all.filter { DeviceInfo.applies(it.oem) }
            val rows = applicable.map { t ->
                val on = if (t.kind == TweakKind.TOGGLE && ShizukuManager.isReady) repo.isOn(t) else null
                Row(t, on)
            }
            _state.update { it.copy(loading = false, rows = rows) }
        }
    }

    fun toggle(t: Tweak, on: Boolean) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Connect Shizuku to use this.") }
                return@launch
            }
            setBusy(t.id, true)
            val r = repo.setEnabled(t, on)
            val newOn = repo.isOn(t) ?: (if (r.success) on else !on)
            _state.update { st ->
                st.copy(
                    rows = st.rows.map { if (it.tweak.id == t.id) it.copy(isOn = newOn) else it },
                    busy = st.busy - t.id,
                    message = if (r.success) null else "Failed: ${r.text.take(120)}",
                )
            }
        }
    }

    fun action(t: Tweak) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Connect Shizuku to use this.") }
                return@launch
            }
            setBusy(t.id, true)
            val r = repo.apply(t)
            setBusy(t.id, false)
            _state.update { it.copy(message = if (r.success) "Done." else "Failed: ${r.text.take(120)}") }
        }
    }

    private fun setBusy(id: String, busy: Boolean) = _state.update {
        it.copy(busy = if (busy) it.busy + id else it.busy - id)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
