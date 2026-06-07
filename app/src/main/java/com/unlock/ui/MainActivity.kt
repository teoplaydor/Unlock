package com.unlock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.unlock.shizuku.ShizukuManager
import com.unlock.ui.navigation.UnlockRoot
import com.unlock.ui.theme.UnlockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnlockTheme {
                UnlockRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up Shizuku being started / permission granted while we were away.
        ShizukuManager.refreshState()
    }
}
