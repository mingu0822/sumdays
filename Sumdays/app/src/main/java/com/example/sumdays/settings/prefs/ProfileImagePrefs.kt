package com.example.sumdays.settings.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object ProfileImagePrefs {
    private const val PREF_NAME = "profile_image_prefs"

    private const val KEY_FACE_ID = "face_id"
    private const val KEY_EYES_ID = "eyes_id"
    private const val KEY_MOUTH_ID = "mouth_id"
    private const val KEY_ACC_ID = "acc_id"
    private const val KEY_MODE = "profile_mode"
    private const val KEY_PHOTO_URI = "photo_uri"

    private const val DEFAULT_ID = -1

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setProfileIds(context: Context, face: Int, eyes: Int, mouth: Int, acc: Int) {
        getPrefs(context).edit {
            putInt(KEY_FACE_ID, face)
            putInt(KEY_EYES_ID, eyes)
            putInt(KEY_MOUTH_ID, mouth)
            putInt(KEY_ACC_ID, acc)
        }
    }

    fun getFaceId(context: Context): Int = getPrefs(context).getInt(KEY_FACE_ID, DEFAULT_ID)
    fun getEyesId(context: Context): Int = getPrefs(context).getInt(KEY_EYES_ID, DEFAULT_ID)
    fun getMouthId(context: Context): Int = getPrefs(context).getInt(KEY_MOUTH_ID, DEFAULT_ID)
    fun getAccId(context: Context): Int = getPrefs(context).getInt(KEY_ACC_ID, DEFAULT_ID)

    fun setMode(context: Context, mode: String) {
        getPrefs(context).edit { putString(KEY_MODE, mode) }
    }
    fun getMode(context: Context): String {
        return getPrefs(context).getString(KEY_MODE, "AVATAR") ?: "AVATAR"
    }

    fun setPhotoUri(context: Context, uri: String) {
        getPrefs(context).edit { putString(KEY_PHOTO_URI, uri) }
    }
    fun getPhotoUri(context: Context): String? {
        return getPrefs(context).getString(KEY_PHOTO_URI, null)
    }
}