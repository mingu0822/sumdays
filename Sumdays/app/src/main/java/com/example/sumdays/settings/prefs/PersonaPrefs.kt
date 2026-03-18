package com.example.sumdays.settings.prefs

import android.content.Context
import android.content.SharedPreferences

object PersonaPrefs {
    private const val PREF_NAME = "persona_prefs"
    private const val KEY_SELECTED_PERSONA_ID = "selected_persona_id"
    private const val DEFAULT_ID = 1

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setSelectedPersonaId(context: Context, id: Int) {
        getPrefs(context).edit().putInt(KEY_SELECTED_PERSONA_ID, id).apply()
    }

    fun getSelectedPersonaId(context: Context): Int {
        return getPrefs(context).getInt(KEY_SELECTED_PERSONA_ID, DEFAULT_ID)
    }
}