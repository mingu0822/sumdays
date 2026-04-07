package com.example.sumdays.settings

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.sumdays.settings.profileimage.ProfileMode
import com.yalantis.ucrop.UCrop
import java.io.File

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding

    private var currentMode = ProfileMode.AVATAR

    private var curFaceId = -1
    private var curEyesId = -1
    private var curMouthId = -1
    private var curAccId = -1
    private var pendingPhotoPath: String? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            launchUCrop(uri)
        }
    private val uCropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val resultUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
                pendingPhotoPath = resultUri.path
                binding.previewPhoto.setImageURI(resultUri)
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val error = UCrop.getError(result.data!!)
                Toast.makeText(this, "사진을 불러오지 못했어요: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.header.headerTitle.text = "프로필 수정"
        binding.header.headerBackIcon.setOnClickListener { finish() }

        setupTabs()
        setupAvatarRecyclerView()
        setupPhotoMode()

        val savedMode = ProfileImagePrefs.getMode(this)
        switchTab(if (savedMode == "PHOTO") ProfileMode.PHOTO else ProfileMode.AVATAR)

        binding.btnSaveProfileImage.setOnClickListener { save() }
    }

    // ── 탭 ───────────────────────────────────────────────

    private fun setupTabs() {
        binding.tabPhoto.setOnClickListener { switchTab(ProfileMode.PHOTO) }
        binding.tabAvatar.setOnClickListener { switchTab(ProfileMode.AVATAR) }
    }

    private fun switchTab(mode: ProfileMode) {
        currentMode = mode

        binding.tabPhoto.applyTabStyle(selected = mode == ProfileMode.PHOTO)
        binding.tabAvatar.applyTabStyle(selected = mode == ProfileMode.AVATAR)

        when (mode) {
            ProfileMode.PHOTO -> {
                binding.photoModeContainer.visibility = View.VISIBLE
                binding.rvParts.visibility = View.GONE
                setAvatarLayersVisible(false)
                binding.previewPhoto.visibility = View.VISIBLE

                // 이전에 선택/저장된 사진 복원
                val path = pendingPhotoPath ?: ProfileImagePrefs.getPhotoUri(this)
                if (path != null) {
                    binding.previewPhoto.setImageURI(Uri.fromFile(File(path)))
                }
            }
            ProfileMode.AVATAR -> {
                binding.photoModeContainer.visibility = View.GONE
                binding.rvParts.visibility = View.VISIBLE
                binding.previewPhoto.visibility = View.GONE
                setAvatarLayersVisible(true)
            }
        }
    }

    private fun TextView.applyTabStyle(selected: Boolean) {
        setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else android.R.color.transparent
        )
        setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun setAvatarLayersVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        binding.previewBase.visibility = v
        binding.previewMouth.visibility = v
        binding.previewEyes.visibility = v
        binding.previewAccessory.visibility = v
    }

    // ── 아바타 ───────────────────────────────────────────

    private fun setupAvatarRecyclerView() {
        // TODO: 더미 데이터 — 나중에 Repository로 교체
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
            ProfileImageCategory("입", ProfileImageItemType.MOUTH, emptyList()),
            ProfileImageCategory("악세서리", ProfileImageItemType.ACC, emptyList())
        )

        loadCurrentAvatarProfile(categories)

        val selectedIdsMap = mapOf(
            ProfileImageItemType.FACE  to curFaceId,
            ProfileImageItemType.EYES  to curEyesId,
            ProfileImageItemType.MOUTH to curMouthId,
            ProfileImageItemType.ACC   to curAccId
        )

        val categoryAdapter = CategoryAdapter(categories, selectedIdsMap) { selectedItem ->
            updateAvatarPreview(selectedItem)
            when (selectedItem.type) {
                ProfileImageItemType.FACE  -> curFaceId  = selectedItem.id
                ProfileImageItemType.EYES  -> curEyesId  = selectedItem.id
                ProfileImageItemType.MOUTH -> curMouthId = selectedItem.id
                ProfileImageItemType.ACC   -> curAccId   = selectedItem.id
            }
        }

        binding.rvParts.apply {
            layoutManager = LinearLayoutManager(this@EditProfileActivity)
            adapter = categoryAdapter
        }
    }

    private fun updateAvatarPreview(item: ProfileImageItem) {
        when (item.type) {
            ProfileImageItemType.FACE  -> {
                binding.previewBase.setImageResource(item.resId)
                binding.previewBase.setColorFilter("#FFE0BD".toColorInt())
            }
            ProfileImageItemType.EYES  -> {
                binding.previewEyes.setImageResource(item.resId)
                binding.previewEyes.setColorFilter(Color.BLACK)
            }
            ProfileImageItemType.MOUTH -> {
                binding.previewMouth.setImageResource(item.resId)
                binding.previewMouth.setColorFilter(Color.CYAN)
            }
            ProfileImageItemType.ACC   -> {
                binding.previewAccessory.setImageResource(item.resId)
                binding.previewAccessory.setColorFilter(Color.YELLOW)
            }
        }
    }

    private fun loadCurrentAvatarProfile(categories: List<ProfileImageCategory>) {
        curFaceId  = ProfileImagePrefs.getFaceId(this)
        curEyesId  = ProfileImagePrefs.getEyesId(this)
        curMouthId = ProfileImagePrefs.getMouthId(this)
        curAccId   = ProfileImagePrefs.getAccId(this)

        val allItems = categories.flatMap { it.parts }
        allItems.find { it.id == curFaceId  }?.let { updateAvatarPreview(it) }
        allItems.find { it.id == curEyesId  }?.let { updateAvatarPreview(it) }
        allItems.find { it.id == curMouthId }?.let { updateAvatarPreview(it) }
        allItems.find { it.id == curAccId   }?.let { updateAvatarPreview(it) }
    }

    // ── 사진 / uCrop ─────────────────────────────────────

    private fun setupPhotoMode() {
        binding.btnSelectPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun launchUCrop(sourceUri: Uri) {
        val destUri = Uri.fromFile(File(cacheDir, "profile_photo_crop.jpg"))

        val options = UCrop.Options().apply {
            setCircleDimmedLayer(false)
            setShowCropGrid(false)
            setShowCropFrame(true)
            setHideBottomControls(false)
            setCompressionQuality(90)
        }

        val intent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .getIntent(this)

        uCropLauncher.launch(intent)
    }

    // ── 저장 ─────────────────────────────────────────────

    private fun save() {
        when (currentMode) {
            ProfileMode.PHOTO -> {
                val path = pendingPhotoPath ?: ProfileImagePrefs.getPhotoUri(this)
                if (path == null) {
                    Toast.makeText(this, "사진을 먼저 선택해 주세요.", Toast.LENGTH_SHORT).show()
                    return
                }
                val dest = File(filesDir, "profile_photo.jpg")
                File(path).copyTo(dest, overwrite = true)

                ProfileImagePrefs.setPhotoUri(this, dest.absolutePath)
                ProfileImagePrefs.setMode(this, "PHOTO")
            }
            ProfileMode.AVATAR -> {
                ProfileImagePrefs.setProfileIds(
                    context = this,
                    face  = curFaceId,
                    eyes  = curEyesId,
                    mouth = curMouthId,
                    acc   = curAccId
                )
                ProfileImagePrefs.setMode(this, "AVATAR")
            }
        }

        Toast.makeText(this, "프로필이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        finish()
    }
}