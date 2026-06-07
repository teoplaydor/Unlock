package com.unlock

import android.app.Application
import com.unlock.core.ServiceLocator
import com.unlock.data.ActionLog
import com.unlock.shizuku.ShizukuManager

class UnlockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ActionLog.init(this)
        ShizukuManager.init()
    }
}
