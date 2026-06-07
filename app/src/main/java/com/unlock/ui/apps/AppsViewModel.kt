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

    fun uninstall(pkg: String, keepData: Boolean = true) = run("Uninstalled", pkg, guard = true) { actions.uninstallUser0(pkg, keepData) }
    fun reinstall(pkg: String) = run("Restored", pkg) { actions.reinstallExisting(pkg) }
    fun disable(pkg: String) = run("Disabled", pkg, guard = true) { actions.disable(pkg) }
    fun enable(pkg: String) = run("Enabled", pkg) { actions.enable(pkg) }
    fun forceStop(pkg: String) = run("Force-stopped", pkg) { actions.forceStop(pkg) }
    fun sleep(pkg: String) = run("Put to sleep", pkg, guard = true) { actions.sleep(pkg) }
    fun clearData(pkg: String) = run("Data cleared", pkg, guard = true) { actions.clearData(pkg) }

    private fun isProtected(pkg: String): Boolean =
        _state.value.apps.firstOrNull { it.packageName == pkg }?.isProtected
            ?: ServiceLocator.isProtected(pkg)

    private fun run(okVerb: String, pkg: String, guard: Boolean = false, block: suspend () -> ShellResult) {
        viewModelScope.launch {
            if (guard && isProtected(pkg)) {
                _state.update { it.copy(message = "Blocked — $pkg is a protected core package (would risk a bootloop).") }
                return@launch
            }
            if (!ShizukuManager.isReady) {
                _state.update { it.copy(message = "Enable Shizuku in Settings to do that without root.") }
                return@launch
            }
            val r = block()
            _state.update { it.copy(message = if (r.success) "$okVerb." else "Failed: ${r.text.take(160)}") }
            load()
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
            var ok = 0
            for (pkg in targets) {
                val r = when (action) {
                    Batch.UNINSTALL -> actions.uninstallUser0(pkg)
                    Batch.DISABLE -> actions.disable(pkg)
                    Batch.FORCE_STOP -> actions.forceStop(pkg)
                    Batch.SLEEP -> actions.sleep(pkg)
                }
                if (r.success) ok++
            }
            val protectedNote = if (skipped > 0) " · $skipped protected skipped" else ""
            _state.update { it.copy(message = "$action: $ok/${targets.size} ok$protectedNote", selected = emptySet()) }
            load()
        }
    }

    enum class Batch { UNINSTALL, DISABLE, FORCE_STOP, SLEEP }
}
