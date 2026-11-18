package com.example.sumdays.data.sync

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class PreferencesPayload(
    val profile : ProfilePayload? = null
) : Parcelable

@Parcelize
data class ProfilePayload(
    val mode: String, // "AVATAR" or "PHOTO"
    val faceId: Int? = null,
    val eyesId: Int? = null,
    val mouthId: Int? = null,
    val accessoryId: Int? = null,
    val imageUrl: String? = null
): Parcelable
