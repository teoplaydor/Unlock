package com.unlock.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unlock.core.ServiceLocator
import com.unlock.diagnostics.DiagnosticsReport
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val report: DiagnosticsReport? = null,
        val userApps: Int = 0,
        val systemApps: Int = 0,
        val disabledApps: Int = 0,
        val autostartApps: Int = 0,
        val shizuku: ShizukuManager.State = ShizukuManager.State.NOT_RUNNING,
        val hasUsageAccess: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ShizukuManager.state.collect { s -> _state.update { it.copy(shizuku = s) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, hasUsageAccess = ServiceLocator.hasUsageAccess()) }
            val report = ServiceLocator.diagnostics.run(ServiceLocator.currentStrings())
            val apps = ServiceLocator.appRepository.loadApps()
            _state.update {
                it.copy(
                    loading = false,
                    report = report,
                    userApps = apps.count { a -> !a.isSystem },
                    systemApps = apps.count { a -> a.isSystem },
                    disabledApps = apps.count { a -> !a.isEnabled },
                    autostartApps = apps.count { a -> a.hasBootReceiver },
                )
            }
        }
    }
}
