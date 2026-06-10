package com.unlock.ui.tweaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
import com.unlock.data.DeviceInfo
import com.unlock.data.Profile
import com.unlock.data.SliderTweaks
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

    data class Row(val tweak: Tweak, val isOn: Boolean? = null, val value: Float? = null)

    data class UiState(
        val loading: Boolean = true,
        val rows: List<Row> = emptyList(),
        val busy: Set<String> = emptySet(),
        val query: String = "",
        val shizukuReady: Boolean = false,
        val deviceLabel: String = DeviceInfo.label,
        val message: String? = null,
    ) {
        val byCategory: List<Pair<String, List<Row>>>
            get() = rows.filter { r ->
                query.isBlank() ||
                    r.tweak.title.contains(query, true) || r.tweak.titleRu.contains(query, true) ||
                    r.tweak.category.contains(query, true) ||
                    r.tweak.desc.contains(query, true) || r.tweak.descRu.contains(query, true)
            }.groupBy { it.tweak.category }.toList()
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

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
            val applicable = (SliderTweaks.all + TweaksCatalog.all).filter { DeviceInfo.applies(it.oem) }
            // Show the whole list instantly; states hydrate in the background so a device with
            // 100+ tweaks doesn't block on sequential `settings get` reads.
            _state.update { st ->
                val prev = st.rows.associateBy { it.tweak.id }
                st.copy(loading = false, rows = applicable.map { Row(it, prev[it.id]?.isOn, prev[it.id]?.value) })
            }
            if (ShizukuManager.isReady) {
                applicable.forEach { t ->
                    when (t.kind) {
                        TweakKind.TOGGLE -> if (t.readCmd != null) {
                            val on = repo.isOn(t)
                            _state.update { st -> st.copy(rows = st.rows.map { if (it.tweak.id == t.id) it.copy(isOn = on) else it }) }
                        }
                        TweakKind.SLIDER -> {
                            val v = repo.readValue(t)?.let { it / t.displayDivide }
                            _state.update { st -> st.copy(rows = st.rows.map { if (it.tweak.id == t.id) it.copy(value = v) else it }) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun setSlider(t: Tweak, value: Float) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = ServiceLocator.currentStrings().tweaksNeedShizuku) }
                return@launch
            }
            setBusy(t.id, true)
            val r = repo.setValue(t, value)
            setBusy(t.id, false)
            _state.update { st ->
                st.copy(
                    rows = st.rows.map { if (it.tweak.id == t.id) it.copy(value = value) else it },
                    message = if (r.success) null else "Failed: ${r.text.take(120)}",
                )
            }
        }
    }

    fun applyProfile(p: Profile, apply: Boolean) {
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = ServiceLocator.currentStrings().tweaksNeedShizuku) }
                return@launch
            }
            val r = repo.run(if (apply) p.applyCmd else p.undoCmd)
            _state.update { it.copy(message = if (r.success) "OK" else "Failed: ${r.text.take(120)}") }
            refresh()
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
