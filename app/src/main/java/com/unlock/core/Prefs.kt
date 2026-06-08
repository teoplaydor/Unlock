package com.unlock.core

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small persisted UI state: stopped-autostart set, anti-throttle toggle, and UI language. */
object Prefs {

    private lateinit var sp: SharedPreferences

    private val _stoppedAutostart = MutableStateFlow<Set<String>>(emptySet())
    val stoppedAutostart: StateFlow<Set<String>> = _stoppedAutostart.asStateFlow()

    private val _antiThrottle = MutableStateFlow(false)
    val antiThrottle: StateFlow<Boolean> = _antiThrottle.asStateFlow()

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("unlock_prefs", Context.MODE_PRIVATE)
        _stoppedAutostart.value = sp.getStringSet(KEY_STOPPED, emptySet())?.toSet() ?: emptySet()
        _antiThrottle.value = sp.getBoolean(KEY_THROTTLE, false)
        // Default to Russian when the device is Russian; otherwise English. User can override.
        _language.value = sp.getString(KEY_LANG, null)
            ?: if (Locale.getDefault().language == "ru") "ru" else "en"
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        sp.edit().putString(KEY_LANG, lang).apply()
    }

    fun setStopped(pkg: String, stopped: Boolean) {
        val next = if (stopped) _stoppedAutostart.value + pkg else _stoppedAutostart.value - pkg
        _stoppedAutostart.value = next
        sp.edit().putStringSet(KEY_STOPPED, next).apply()
    }

    fun setAntiThrottle(on: Boolean) {
        _antiThrottle.value = on
        sp.edit().putBoolean(KEY_THROTTLE, on).apply()
    }

    private const val KEY_STOPPED = "stopped_autostart"
    private const val KEY_THROTTLE = "anti_throttle"
    private const val KEY_LANG = "language"
}
