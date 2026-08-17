package com.loyu.ledger.data.prefs

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getGroqApiKey(): String = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""

    fun setGroqApiKey(key: String) {
        prefs.edit().putString(KEY_GROQ_API_KEY, key).apply()
    }

    companion object {
        private const val PREFS_NAME = "loyu_ledger_settings"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
    }
}
