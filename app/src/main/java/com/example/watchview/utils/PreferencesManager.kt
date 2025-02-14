package com.example.watchview.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "WatchViewPrefs"
        private const val KEY_LAST_IP = "last_ip_address"
    }

    var lastIpAddress: String
        get() = prefs.getString(KEY_LAST_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_IP, value).apply()
} 