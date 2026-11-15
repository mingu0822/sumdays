
package com.example.sumdays.image

import android.graphics.Color
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.example.sumdays.R
import com.example.sumdays.data.sync.ProfilePayload
import com.example.sumdays.network.ApiClient
import com.example.sumdays.settings.prefs.ProfileImagePrefs
import com.example.sumdays.settings.profileimage.ProfileImageItem
import com.example.sumdays.settings.profileimage.ProfileImageItemType
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

object ProfileRenderer {

    fun render(
        root: View,
        profile: ProfilePayload?,
        isMine : Boolean
    ) {
        val photo = root.findViewById<ImageView>(R.id.profilePhoto)
        val base = root.findViewById<ImageView>(R.id.profileBase)
        val mouth = root.findViewById<ImageView>(R.id.profileMouth)
        val eyes = root.findViewById<ImageView>(R.id.profileEyes)
        val accessory = root.findViewById<ImageView>(R.id.profileAccessory)

        val avatarViews = listOf(base, mouth, eyes, accessory)


        // 이전 렌더링 상태 초기화
        Glide.with(root).clear(photo)
        photo.setImageDrawable(null)
        photo.visibility = View.GONE

        avatarViews.forEach {
            it.setImageDrawable(null)
            it.clearColorFilter()
            it.visibility = View.GONE
        }

        // 기본 이미지 표시
        fun showDefault() {
            photo.visibility = View.GONE

            avatarViews.forEach {
                it.visibility = View.GONE
            }

            if (isMine) {
                base.setImageResource(R.drawable.nav_fox_button)
                base.visibility = View.VISIBLE
            } else {
                photo.setImageResource(R.drawable.ic_account_circle)
                photo.visibility = View.VISIBLE
            }
        }

        if (profile == null) {
            showDefault()
            return
        }

        when (profile.mode) {

            "PHOTO" -> {
                val imageUrl = ProfileImagePrefs.parseImageUrl(profile.imageUrl)

                if (imageUrl == null) {
                    showDefault()
                    return
                }

                photo.visibility = View.VISIBLE

                Glide.with(root)
                    .load(imageUrl)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            showDefault()
                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean = false
                    })
                    .into(photo)
            }

            "AVATAR" -> {
                val isAvatarEmpty =
                    profile.faceId == -1 &&
                            profile.eyesId == -1 &&
                            profile.mouthId == -1 &&
                            profile.accessoryId == -1

                if (isAvatarEmpty) {
                    showDefault()
                    return
                }

                // 기존 SocialActivity.kt의 임시 리소스 목록
                val items = listOf(
                    ProfileImageItem(1, ProfileImageItemType.FACE, R.drawable.nav_fox_button),
                    ProfileImageItem(2, ProfileImageItemType.FACE, R.drawable.dailyread_fox_face_level_5),
                    ProfileImageItem(3, ProfileImageItemType.FACE, 0),
                    ProfileImageItem(4, ProfileImageItemType.EYES, R.drawable.loading_animation),
                    ProfileImageItem(5, ProfileImageItemType.EYES, 0)
                )

                fun renderPart(
                    view: ImageView,
                    type: ProfileImageItemType,
                    id: Int?,
                    color: Int
                ) {
                    if (id == null || id == -1) return

                    val resId = items.find {
                        it.type == type && it.id == id
                    }?.resId ?: return

                    if (resId == 0) return

                    view.setImageResource(resId)
                    view.setColorFilter(color)
                    view.visibility = View.VISIBLE
                }

                // 얼굴: 설정되지 않았으면 기본 여우 얼굴 표시
                val faceResId = items.find {
                    it.type == ProfileImageItemType.FACE &&
                            it.id == profile.faceId
                }?.resId?.takeIf { it != 0 } ?: R.drawable.nav_fox_button

                base.setImageResource(faceResId)
                base.setColorFilter(Color.rgb(255, 224, 189))
                base.visibility = View.VISIBLE

                // 나머지 파츠
                renderPart(
                    mouth,
                    ProfileImageItemType.MOUTH,
                    profile.mouthId,
                    Color.CYAN
                )

                renderPart(
                    eyes,
                    ProfileImageItemType.EYES,
                    profile.eyesId,
                    Color.BLACK
                )

                renderPart(
                    accessory,
                    ProfileImageItemType.ACC,
                    profile.accessoryId,
                    Color.YELLOW
                )
            }

            else -> showDefault()
        }
    }
}
