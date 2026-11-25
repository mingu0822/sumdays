package com.example.sumdays.data.userInfo.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.sumdays.data.sync.ProfilePayload
import com.example.sumdays.network.ApiClient

object ProfileImagePrefs {

    private const val PREF_NAME = "profile_image_prefs"

    private const val KEY_FACE_ID = "face_id"
    private const val KEY_EYES_ID = "eyes_id"
    private const val KEY_MOUTH_ID = "mouth_id"
    private const val KEY_ACC_ID = "acc_id"
    private const val KEY_MODE = "profile_mode"
    private const val KEY_IMAGE_URL = "image_url"

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

    fun getFaceId(context: Context): Int =
        getPrefs(context).getInt(KEY_FACE_ID, DEFAULT_ID)

    fun getEyesId(context: Context): Int =
        getPrefs(context).getInt(KEY_EYES_ID, DEFAULT_ID)

    fun getMouthId(context: Context): Int =
        getPrefs(context).getInt(KEY_MOUTH_ID, DEFAULT_ID)

    fun getAccId(context: Context): Int =
        getPrefs(context).getInt(KEY_ACC_ID, DEFAULT_ID)

    fun setMode(context: Context, mode: String) {
        getPrefs(context).edit {
            putString(KEY_MODE, mode)
        }
    }

    fun getMode(context: Context): String {
        return getPrefs(context).getString(KEY_MODE, "AVATAR") ?: "AVATAR"
    }

    fun setImageUrl(context: Context, url: String?) {
        getPrefs(context).edit {
            putString(KEY_IMAGE_URL, url)
        }
    }
    fun parseImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            ApiClient.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
        }
    }
    fun getImageUrl(context: Context): String? {
        val url = getPrefs(context).getString(KEY_IMAGE_URL, null)
        return parseImageUrl(url)
    }

    fun getMyProfilePayload(context: Context): ProfilePayload {
        return ProfilePayload(
            mode = getMode(context),
            faceId = getFaceId(context),
            eyesId = getEyesId(context),
            mouthId = getMouthId(context),
            accessoryId = getAccId(context),
            imageUrl = getImageUrl(context)
        )
    }

    fun init(context: Context, profile: ProfilePayload?) {
        clear(context)

        if (profile == null) return

        when (profile.mode) {
            "AVATAR" -> {
                setProfileIds(
                    context = context,
                    face = profile.faceId ?: -1,
                    eyes = profile.eyesId ?: -1,
                    mouth = profile.mouthId ?: -1,
                    acc = profile.accessoryId ?: -1
                )
                setMode(context, "AVATAR")
            }

            "PHOTO" -> {
                setImageUrl(context, profile.imageUrl)
                setMode(context, "PHOTO")
            }
        }
    }
    fun clear(context: Context) {
        getPrefs(context).edit {
            clear()
        }
    }
}
