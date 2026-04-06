package com.example.sumdays.settings

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sumdays.R
import com.example.sumdays.databinding.ActivityProfileEditBinding
import com.example.sumdays.settings.prefs.ProfileImagePrefs
import com.example.sumdays.settings.profileimage.CategoryAdapter
import com.example.sumdays.settings.profileimage.ProfileImageCategory
import com.example.sumdays.settings.profileimage.ProfileImageItem
import com.example.sumdays.settings.profileimage.ProfileImageItemType

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding

    private var curFaceId = -1
    private var curEyesId = -1
    private var curMouthId = -1
    private var curAccId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.header.headerTitle.text = "프로필 수정"
        binding.header.headerBackIcon.setOnClickListener {
            finish()
        }

        // TODO: 더미 데이터 생성
        val faceItems = listOf(
            ProfileImageItem(1, ProfileImageItemType.FACE, R.drawable.nav_fox_button),
            ProfileImageItem(2, ProfileImageItemType.FACE, R.drawable.dailyread_fox_face_level_5),
            ProfileImageItem(3, ProfileImageItemType.FACE, 0)
        )

        val eyeItems = listOf(
            ProfileImageItem(4, ProfileImageItemType.EYES, R.drawable.loading_animation),
            ProfileImageItem(5, ProfileImageItemType.EYES, 0)
        )

        val categories = listOf(
            ProfileImageCategory("얼굴형", ProfileImageItemType.FACE, faceItems),
            ProfileImageCategory("눈", ProfileImageItemType.EYES, eyeItems),
            ProfileImageCategory("입", ProfileImageItemType.MOUTH, emptyList()), // 일단 비워둠
            ProfileImageCategory("악세서리", ProfileImageItemType.ACC, emptyList())
        )

        // 기존 프로필 로드
        loadCurrentProfile(categories)
        val currentSelectedIdsMap = mapOf(
            ProfileImageItemType.FACE to curFaceId,
            ProfileImageItemType.EYES to curEyesId,
            ProfileImageItemType.MOUTH to curMouthId,
            ProfileImageItemType.ACC to curAccId
        )

        // 리사이클러뷰 설정
        val categoryAdapter = CategoryAdapter(categories, currentSelectedIdsMap) { selectedItem ->
            // 아이템 클릭 시 처리 로직
            updatePreview(selectedItem)
            // 선택된 아이템 id 업데이트
            when (selectedItem.type) {
                ProfileImageItemType.FACE -> curFaceId = selectedItem.id
                ProfileImageItemType.EYES -> curEyesId = selectedItem.id
                ProfileImageItemType.MOUTH -> curMouthId = selectedItem.id
                ProfileImageItemType.ACC -> curAccId = selectedItem.id
            }
        }

        binding.rvParts.apply {
            layoutManager = LinearLayoutManager(this@EditProfileActivity)
            adapter = categoryAdapter
        }
        binding.btnSaveProfileImage.setOnClickListener {
            saveToPrefs()
        }
    }

    private fun updatePreview(item: ProfileImageItem) {
        when (item.type) {
            // TODO: 에셋으로 변경(setColorFilter는 유지할 수도?)
            ProfileImageItemType.FACE -> {
                binding.previewBase.setImageResource(item.resId)
                binding.previewBase.setColorFilter("#FFE0BD".toColorInt())
            }
            ProfileImageItemType.EYES -> {
                binding.previewEyes.setImageResource(item.resId)
                binding.previewEyes.setColorFilter(Color.BLACK)
            }
            ProfileImageItemType.MOUTH -> {
                binding.previewMouth.setImageResource(item.resId)
                binding.previewMouth.setColorFilter(Color.CYAN)
            }
            ProfileImageItemType.ACC -> {
                binding.previewAccessory.setImageResource(item.resId)
                binding.previewAccessory.setColorFilter(Color.YELLOW)
            }
        }
    }

    private fun saveToPrefs() {
        ProfileImagePrefs.setProfileIds(
            context = this,
            face = curFaceId,
            eyes = curEyesId,
            mouth = curMouthId,
            acc = curAccId
        )

        Toast.makeText(this, "프로필이 저장되었습니다.", Toast.LENGTH_SHORT).show()

        finish()
    }

    private fun loadCurrentProfile(categories: List<ProfileImageCategory>) {
        curFaceId = ProfileImagePrefs.getFaceId(this)
        curEyesId = ProfileImagePrefs.getEyesId(this)
        curMouthId = ProfileImagePrefs.getMouthId(this)
        curAccId = ProfileImagePrefs.getAccId(this)

        val allItems = categories.flatMap { it.parts }

        allItems.find { it.id == curFaceId }?.let { updatePreview(it) }
        allItems.find { it.id == curEyesId }?.let { updatePreview(it) }
        allItems.find { it.id == curMouthId }?.let { updatePreview(it) }
        allItems.find { it.id == curAccId }?.let { updatePreview(it) }
    }
}