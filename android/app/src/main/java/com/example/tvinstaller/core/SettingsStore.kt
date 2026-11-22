package com.example.tvinstaller.core

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var tvIp: String
        get() = prefs.getString(KEY_TV_IP, DEFAULT_TV_IP) ?: DEFAULT_TV_IP
        set(value) = prefs.edit().putString(KEY_TV_IP, value).apply()

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, DEFAULT_HTTP_PORT)
        set(value) = prefs.edit().putInt(KEY_HTTP_PORT, value).apply()

    var timeoutSeconds: Int
        get() = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()

    companion object {
        const val DEFAULT_TV_IP = "192.168.3.212"
        const val DEFAULT_HTTP_PORT = 8765
        const val DEFAULT_TIMEOUT_SECONDS = 180

        private const val KEY_TV_IP = "tv_ip"
        private const val KEY_HTTP_PORT = "http_port"
        private const val KEY_TIMEOUT = "timeout_seconds"
    }
}
