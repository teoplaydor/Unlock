package com.unlock.core

import android.content.Context
import com.unlock.data.AppActions
import com.unlock.data.AppRepository
import com.unlock.data.RunningRepository
import com.unlock.data.UsageRepository
import com.unlock.diagnostics.DiagnosticsEngine

/** Tiny manual DI container so ViewModels can stay no-arg and use viewModel(). */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val usageRepository: UsageRepository by lazy { UsageRepository(appContext) }
    val runningRepository: RunningRepository by lazy { RunningRepository(usageRepository) }
    val appActions: AppActions by lazy { AppActions(appContext) }
    val diagnostics: DiagnosticsEngine by lazy { DiagnosticsEngine(appContext) }

    fun hasUsageAccess(): Boolean = Permissions.hasUsageAccess(appContext)
}
