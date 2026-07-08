package com.gongpx.androidacpclient.data.store

import android.content.Context

enum class AppLanguageMode {
    System,
    English,
    Chinese,
}

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun loadLanguageMode(): AppLanguageMode {
        return runCatching {
            AppLanguageMode.valueOf(preferences.getString(KEY_LANGUAGE_MODE, AppLanguageMode.System.name) ?: AppLanguageMode.System.name)
        }.getOrDefault(AppLanguageMode.System)
    }

    fun saveLanguageMode(mode: AppLanguageMode) {
        preferences.edit().putString(KEY_LANGUAGE_MODE, mode.name).apply()
    }

    fun loadSessionLoadMessageLimit(): Int {
        return preferences.getInt(KEY_SESSION_LOAD_MESSAGE_LIMIT, DEFAULT_SESSION_LOAD_MESSAGE_LIMIT).coerceAtLeast(1)
    }

    fun saveSessionLoadMessageLimit(limit: Int) {
        preferences.edit().putInt(KEY_SESSION_LOAD_MESSAGE_LIMIT, limit.coerceAtLeast(1)).apply()
    }

    private companion object {
        const val KEY_LANGUAGE_MODE = "language_mode"
        const val KEY_SESSION_LOAD_MESSAGE_LIMIT = "session_load_message_limit"
        const val DEFAULT_SESSION_LOAD_MESSAGE_LIMIT = 5
    }
}
