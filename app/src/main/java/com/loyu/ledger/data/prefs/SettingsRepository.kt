package com.loyu.ledger.data.prefs

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getGroqApiKey(): String = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""

    fun setGroqApiKey(key: String) {
        prefs.edit().putString(KEY_GROQ_API_KEY, key).apply()
    }

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "loyu_ledger_settings"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
