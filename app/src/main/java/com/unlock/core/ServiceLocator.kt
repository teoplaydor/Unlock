package com.unlock.core

import android.content.Context
import com.unlock.data.AppActions
import com.unlock.data.AppRepository
import com.unlock.data.BatteryForensicsRepository
import com.unlock.data.MemoryRepository
import com.unlock.data.RunningRepository
import com.unlock.data.SafetyCatalog
import com.unlock.data.SafetyTier
import com.unlock.data.Samsung
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
    val batteryForensics: BatteryForensicsRepository by lazy { BatteryForensicsRepository(appContext) }
    val memoryRepository: MemoryRepository by lazy { MemoryRepository() }

    fun samsungGosPackages(): List<String> = Samsung.allGamePackages(appContext)

    fun hasUsageAccess(): Boolean = Permissions.hasUsageAccess(appContext)

    fun samsungGosPackage(): String? = Samsung.activeGosPackage(appContext)

    /** Authoritative protection check, independent of any loaded list. */
    fun isProtected(pkg: String): Boolean =
        SafetyCatalog.classify(pkg, SafetyCatalog.dynamicProtected(appContext)) == SafetyTier.PROTECTED
}
