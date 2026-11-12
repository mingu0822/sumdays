package com.example.sumdays.social.diary

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.R
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.databinding.ActivitySocialDailyReadBinding
import com.example.sumdays.mission.MissionEventReporter
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.content.IntentCompat
import com.example.sumdays.auth.InventoryOwner
import com.example.sumdays.image.GalleryItem
import com.example.sumdays.image.PhotoGalleryAdapter
import com.example.sumdays.stickers.DiaryStickerController
import androidx.recyclerview.widget.LinearLayoutManager
import android.app.Dialog
import android.widget.ImageView
import com.bumptech.glide.Glide
import android.net.Uri
import android.util.Base64

class SocialDailyReadActivity : AppCompatActivity() {
    private lateinit var inventoryOwner: InventoryOwner
    private lateinit var stickerController: DiaryStickerController
    private lateinit var photoGalleryAdapter: PhotoGalleryAdapter
    private lateinit var binding: ActivitySocialDailyReadBinding
    private lateinit var btnBack: ImageButton
    private var dailyEntry: DailyEntry? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        inventoryOwner = InventoryOwner(savedInstanceState)
        super.onCreate(savedInstanceState)
        if (!inventoryOwner.requireCurrent(this)) return
        binding = ActivitySocialDailyReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dailyEntry = IntentCompat.getParcelableExtra(
            intent,
            "dailyEntry",
            DailyEntry::class.java
        )

        val friendId = intent.getIntExtra("friendId", -1)
        if (dailyEntry == null || friendId <= 0 || dailyEntry?.isAllowed != true || dailyEntry?.isDeleted == true) {
            Toast.makeText(this, "일기 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setupPhotoGallery()

        MissionEventReporter.recordFriendDiaryView(
            context = this,
            friendId = intent.getIntExtra("friendId", -1),
            diaryDate = dailyEntry!!.date,
        )

        initUi()
        stickerController = DiaryStickerController(this, inventoryOwner, binding.diaryStickerCanvas,
            binding.stickerButton, binding.stickerVisibilityButton, onAccessDenied = {
                binding.diaryContentTextView.text = ""
                finish()
            })
        stickerController.bind(friendId, dailyEntry)
        applyThemeModeSettings()

        val rootView = findViewById<View>(R.id.main)
        setupEdgeToEdge(rootView)

        btnBack = findViewById(R.id.read_back_button)
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun initUi(){
        binding.dateText.text = dailyEntry?.date
        binding.diaryContentTextView.text = dailyEntry?.diary
        val photos = dailyEntry?.photoUrls
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // GalleryItem.Add를 넣지 않으므로 추가 버튼 없음
        photoGalleryAdapter.submitList(
            photos.map { GalleryItem.Photo(it) }
        )

        binding.photoGalleryRecyclerView.visibility =
            if (photos.isEmpty()) View.GONE else View.VISIBLE
    }


    private fun applyThemeModeSettings() {

        val themeRepo = ThemeRepository
        val foxRepo = FoxRepository

        // ⭐ owned 목록 갱신 (이거 매우 중요)
        themeRepo.updateOwned()
        foxRepo.updateOwned()

        val themeKey = ThemePrefs.getTheme(this)
//        val foxKey = ThemePrefs.getFox(this)

        val currentTheme =
            themeRepo.ownedThemes[themeKey]
                ?: themeRepo.allThemeMap[themeKey]

//        val currentFox =
//            foxRepo.ownedFoxes[foxKey]
//                ?: foxRepo.allFoxMap[foxKey]

        // ⭐ null 방어
        if (currentTheme == null) {
            Toast.makeText(this, "기본 테마를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val backgroundColor = currentTheme.backgroundColor

        binding.root.setBackgroundResource(backgroundColor)

    }

    fun updateOwned(){
        ThemeRepository.updateOwned()
        FoxRepository.updateOwned()
    }
    private fun setupPhotoGallery() {
        photoGalleryAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photoString ->
                showPhotoDialog(photoString)
            },
            onDeleteClick = {},
            onAddClick = {},
            isReadOnly = true
        )

        binding.photoGalleryRecyclerView.apply {
            layoutManager = LinearLayoutManager(
                this@SocialDailyReadActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = photoGalleryAdapter
        }
    }

    private fun showPhotoDialog(photoString: String) {
        val dialog = Dialog(
            this,
            android.R.style.Theme_Black_NoTitleBar_Fullscreen
        )

        val imageView = ImageView(this).apply {
            setBackgroundColor(getColor(android.R.color.black))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val isUriOrPath = photoString.startsWith("content://") ||
                photoString.startsWith("file://") ||
                photoString.startsWith("http://") ||
                photoString.startsWith("https://") ||
                photoString.startsWith("/")

        try {
            if (isUriOrPath) {
                Glide.with(this)
                    .load(Uri.parse(photoString))
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            } else {
                val imageBytes = Base64.decode(photoString, Base64.DEFAULT)
                Glide.with(this)
                    .load(imageBytes)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(imageView)
            }
        } catch (_: IllegalArgumentException) {
            Toast.makeText(
                this,
                "이미지를 불러올 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }



    override fun onResume() {
        super.onResume()
        if (!inventoryOwner.requireCurrent(this)) return
        updateOwned()
        applyThemeModeSettings()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        inventoryOwner.saveState(outState)
        super.onSaveInstanceState(outState)
    }
}
