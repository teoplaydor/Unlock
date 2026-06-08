package com.unlock.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
import com.unlock.data.AppInfo
import com.unlock.data.SafetyTier
import com.unlock.shizuku.ShellResult
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppsViewModel : ViewModel() {

    enum class Filter { ALL, USER, SYSTEM, DISABLED, AUTOSTART, SAFE_DEBLOAT }
    enum class Sort { NAME, SIZE, LAST_USED, INSTALL_DATE }

    data class UiState(
        val loading: Boolean = true,
        val apps: List<AppInfo> = emptyList(),
        val query: String = "",
        val filter: Filter = Filter.ALL,
        val sort: Sort = Sort.NAME,
        val selected: Set<String> = emptySet(),
        val busy: Set<String> = emptySet(),
        val shizukuReady: Boolean = false,
        val hasUsageAccess: Boolean = false,
        val message: String? = null,
    ) {
        val visible: List<AppInfo>
            get() = apps
                .filter { app ->
                    when (filter) {
                        Filter.ALL -> true
                        Filter.USER -> !app.isSystem
                        Filter.SYSTEM -> app.isSystem
                        Filter.DISABLED -> !app.isEnabled
                        Filter.AUTOSTART -> app.hasBootReceiver
                        Filter.SAFE_DEBLOAT -> app.safetyTier == SafetyTier.DEBLOAT_SAFE
                    }
                }
                .filter { app ->
                    query.isBlank() ||
                        app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                }
                .let { list ->
                    when (sort) {
                        Sort.NAME -> list.sortedBy { it.label.lowercase() }
                        Sort.SIZE -> list.sortedByDescending { it.totalBytes }
                        Sort.LAST_USED -> list.sortedByDescending { it.lastUsed }
                        Sort.INSTALL_DATE -> list.sortedByDescending { it.firstInstallTime }
                    }
                }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val repo get() = ServiceLocator.appRepository
    private val actions get() = ServiceLocator.appActions

    init {
        viewModelScope.launch {
            ShizukuManager.state.collect { s ->
                _state.update { it.copy(shizukuReady = s == ShizukuManager.State.READY) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val hasUsage = ServiceLocator.hasUsageAccess()
            _state.update { it.copy(loading = true, hasUsageAccess = hasUsage) }
            val base = repo.loadApps()
            val usage = if (hasUsage) ServiceLocator.usageRepository.usageSince(30) else emptyMap()
            val enriched = base.map { app ->
                val u = usage[app.packageName]
                val sizes = if (hasUsage) repo.sizesOf(app) else Triple(-1L, -1L, -1L)
                app.copy(
                    lastUsed = u?.lastUsed ?: 0,
                    totalForegroundMs = u?.totalForegroundMs ?: 0,
                    launchCount = u?.launchCount ?: 0,
                    appBytes = sizes.first,
                    dataBytes = sizes.second,
                    cacheBytes = sizes.third,
                )
            }
            _state.update { it.copy(loading = false, apps = enriched) }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }
    fun setSort(s: Sort) = _state.update { it.copy(sort = s) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun toggleSelect(pkg: String) = _state.update {
        it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectAllVisible() = _state.update { it.copy(selected = it.visible.map { a -> a.packageName }.toSet()) }

    // ---- privileged single-package actions (route through Shizuku) ----

    fun uninstall(pkg: String, keepData: Boolean = true) =
        run("Uninstalled", pkg, guard = true, transform = { null }) { actions.uninstallUser0(pkg, keepData) }
    fun reinstall(pkg: String) =
        run("Restored", pkg, transform = { it.copy(isEnabled = true) }) { actions.reinstallExisting(pkg) }
    fun disable(pkg: String) =
        run("Disabled", pkg, guard = true, transform = { it.copy(isEnabled = false) }) { actions.disable(pkg) }
    fun enable(pkg: String) =
        run("Enabled", pkg, transform = { it.copy(isEnabled = true) }) { actions.enable(pkg) }
    fun forceStop(pkg: String) = run("Force-stopped", pkg) { actions.forceStop(pkg) }
    fun sleep(pkg: String) = run("Put to sleep", pkg, guard = true) { actions.sleep(pkg) }
    fun clearData(pkg: String) = run("Data cleared", pkg, guard = true) { actions.clearData(pkg) }

    /** Row Switch: flip enabled state. State reflects instantly (optimistic) on success. */
    fun toggleEnabled(pkg: String, currentlyEnabled: Boolean) =
        if (currentlyEnabled) disable(pkg) else enable(pkg)

    private fun isProtected(pkg: String): Boolean =
        _state.value.apps.firstOrNull { it.packageName == pkg }?.isProtected
            ?: ServiceLocator.isProtected(pkg)

    private fun setBusy(pkg: String, busy: Boolean) = _state.update {
        it.copy(busy = if (busy) it.busy + pkg else it.busy - pkg)
    }

    /**
     * Runs a privileged action and updates ONLY the affected row in place (via [transform])
     * instead of reloading the whole list — the list never blanks and scroll is preserved.
     * transform returns the updated AppInfo, or null to remove the row (uninstall).
     */
    private fun run(
        okVerb: String,
        pkg: String,
        guard: Boolean = false,
        transform: ((AppInfo) -> AppInfo?)? = null,
        block: suspend () -> ShellResult,
    ) {
        viewModelScope.launch {
            if (guard && isProtected(pkg)) {
                _state.update { it.copy(message = "Blocked — $pkg is a protected core package (would risk a bootloop).") }
                return@launch
            }
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Enable Shizuku in Settings to do that without root.") }
                return@launch
            }
            setBusy(pkg, true)
            val r = block()
            val tf = transform
            if (r.success && tf != null) {
                _state.update { st ->
                    st.copy(apps = st.apps.mapNotNull { if (it.packageName == pkg) tf(it) else it })
                }
            }
            setBusy(pkg, false)
            // No notification on success — the row reflects the new state itself. Errors only.
            if (!r.success) _state.update { it.copy(message = "$okVerb failed: ${r.text.take(140)}") }
        }
    }

    // ---- batch ----

    fun batch(action: Batch) {
        val selected = _state.value.selected.toList()
        if (selected.isEmpty()) return
        val targets = selected.filter { !isProtected(it) }
        val skipped = selected.size - targets.size
        viewModelScope.launch {
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Batch actions need Shizuku (no root).") }
                return@launch
            }
            _state.update { it.copy(busy = it.busy + targets) }
            var ok = 0
            for (pkg in targets) {
                val r = when (action) {
                    Batch.UNINSTALL -> actions.uninstallUser0(pkg)
                    Batch.DISABLE -> actions.disable(pkg)
                    Batch.FORCE_STOP -> actions.forceStop(pkg)
                    Batch.SLEEP -> actions.sleep(pkg)
                }
                if (r.success) {
                    ok++
                    _state.update { st ->
                        st.copy(apps = st.apps.mapNotNull { app ->
                            if (app.packageName != pkg) app
                            else when (action) {
                                Batch.UNINSTALL -> null
                                Batch.DISABLE -> app.copy(isEnabled = false)
                                else -> app
                            }
                        })
                    }
                }
            }
            val note = if (skipped > 0) " · $skipped protected skipped" else ""
            _state.update {
                it.copy(busy = it.busy - targets.toSet(), selected = emptySet(), message = "$action: $ok/${targets.size} ok$note")
            }
        }
    }

    enum class Batch { UNINSTALL, DISABLE, FORCE_STOP, SLEEP }
}
