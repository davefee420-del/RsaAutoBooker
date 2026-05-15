package com.rsa.autobooker

import android.content.Context
import android.content.SharedPreferences

enum class ScanMode { STEALTH, NORMAL, AGGRESSIVE }

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoClick: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLICK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CLICK, value).apply()

    var autoBook: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BOOK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BOOK, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    var scanMode: ScanMode
        get() = ScanMode.valueOf(prefs.getString(KEY_SCAN_MODE, ScanMode.STEALTH.name)!!)
        set(value) = prefs.edit().putString(KEY_SCAN_MODE, value.name).apply()

    companion object {
        private const val PREFS_NAME = "rsa_auto_booker_settings"
        private const val KEY_AUTO_CLICK = "auto_click"
        private const val KEY_AUTO_BOOK = "auto_book"
        private const val KEY_SOUND = "sound"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_SCAN_MODE = "scan_mode"
    }
}
