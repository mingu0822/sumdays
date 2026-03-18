package com.example.sumdays

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.sumdays.daily.diary.AnalysisRepository
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.databinding.ActivityDailyReadBinding
import com.example.sumdays.image.GalleryItem
import com.example.sumdays.image.PhotoGalleryAdapter
import com.example.sumdays.settings.prefs.PersonaPrefs
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DailyReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDailyReadBinding
    private lateinit var currentDate: Calendar
    private val viewModel: DailyEntryViewModel by viewModels()
    private var currentLiveData: LiveData<DailyEntry?>? = null
    private lateinit var navBarController: NavBarController

    private lateinit var photoGalleryAdapter: PhotoGalleryAdapter

    // Uri 문자열 저장 리스트
    private val currentPhotoList = mutableListOf<String>()

    private val repoKeyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var pickImageLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.READ)

        initializeImagePicker()
        initializeDate()
        setupPhotoGallery()
        setupClickListeners()
        observeEntry()
        applyThemeModeSettings()

        val rootView = findViewById<View>(R.id.main)
        setupEdgeToEdge(rootView)
    }

    private fun applyThemeModeSettings() {
        ThemeState.isDarkMode =
            (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode) {
            binding.editMemosButton.setTextColor(getColor(R.color.white))
            binding.prevDayButton.setImageResource(R.drawable.ic_arrow_back_white)
            binding.nextDayButton.setImageResource(R.drawable.ic_arrow_forward_white)
        } else {
            binding.editMemosButton.setTextColor(getColor(R.color.white))
            binding.prevDayButton.setImageResource(R.drawable.ic_arrow_back_black)
            binding.nextDayButton.setImageResource(R.drawable.ic_arrow_forward_black)
        }
    }

    // ──────────────────────────────────
    // 사진 선택 → Uri 문자열 저장
    // ──────────────────────────────────
    private fun initializeImagePicker() {
        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    persistUriPermissionIfPossible(uri)
                    addPhoto(uri.toString())
                }
            }
    }

    private fun persistUriPermissionIfPossible(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // provider가 persistable permission 미지원일 수 있음
        } catch (_: Exception) {
        }
    }

    private fun addPhoto(uriString: String) {
        if (!currentPhotoList.contains(uriString)) {
            currentPhotoList.add(uriString)
            updatePhotoGalleryUI()
            savePhotoUrls()
            Toast.makeText(this, "사진이 추가되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "이미 추가된 사진입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePhotoGalleryUI() {
        val items = currentPhotoList.map { GalleryItem.Photo(it) } + GalleryItem.Add
        photoGalleryAdapter.submitList(items)

        binding.photoGalleryRecyclerView.visibility =
            if (currentPhotoList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun savePhotoUrls() {
        val dateKey = repoKeyFormatter.format(currentDate.time)
        val photoDataString = currentPhotoList.joinToString(",")
        viewModel.updateEntry(
            date = dateKey,
            photoUrls = photoDataString
        )
    }

    // ──────────────────────────────────
    // DB 관찰 & UI 갱신
    // ──────────────────────────────────
    private fun observeEntry() {
        val dateKey = repoKeyFormatter.format(currentDate.time)
        currentLiveData?.removeObservers(this)
        currentLiveData = viewModel.getEntry(dateKey)
        currentLiveData?.observe(this) { entry ->
            updateUI(entry)
        }
    }

    private fun updateUI(entry: DailyEntry?) {
        val isToday = isSameDay(currentDate, Calendar.getInstance())

        if (isToday) {
            binding.dateText.text = "오늘"
            binding.nextDayButton.visibility = View.INVISIBLE
            binding.nextDayButton.isEnabled = false
        } else {
            binding.dateText.text = displayFormatter.format(currentDate.time)
            binding.nextDayButton.visibility = View.VISIBLE
            binding.nextDayButton.isEnabled = true
        }

        val diaryText = entry?.diary ?: ""
        binding.diaryContentEditText.setText(diaryText)
        binding.diaryContentTextView.text = diaryText
        binding.commentIcon.text = entry?.themeIcon ?: "🤔"
        binding.keywordsText.text = entry?.keywords?.replace(";", ", ")
        binding.commentText.text = entry?.aiComment ?: ""

        val score = entry?.emotionScore ?: 0.0
        val foxFaceResId = when {
            score >= 0.6 -> R.drawable.dailyread_fox_face_level_5
            score >= 0.2 -> R.drawable.dailyread_fox_face_level_4
            score >= -0.2 -> R.drawable.dailyread_fox_face_level_3
            score >= -0.6 -> R.drawable.dailyread_fox_face_level_2
            else -> R.drawable.dailyread_fox_face_level_1
        }
        binding.foxFaceImage.setImageResource(foxFaceResId)

        currentPhotoList.clear()
        entry?.photoUrls?.let { urls ->
            if (urls.isNotEmpty()) {
                currentPhotoList.addAll(urls.split(","))
            }
        }
        updatePhotoGalleryUI()
    }

    // ──────────────────────────────────
    // 날짜 초기화 & 이동
    // ──────────────────────────────────
    private fun initializeDate() {
        val dateString = intent.getStringExtra("date")
        currentDate = Calendar.getInstance()
        try {
            if (dateString != null) {
                repoKeyFormatter.parse(dateString)?.let { currentDate.time = it }
            }
        } catch (_: Exception) {
        }
    }

    private fun setupClickListeners() {
        binding.dateText.setOnClickListener { showDatePickerDialog() }
        binding.prevDayButton.setOnClickListener { changeDate(-1) }
        binding.nextDayButton.setOnClickListener { changeDate(1) }

        binding.editInplaceButton.setOnClickListener { toggleEditMode(true) }

        binding.saveButton.setOnClickListener {
            val updatedContent = binding.diaryContentEditText.text.toString()
            showReanalysisDialog(updatedContent)
        }

        binding.editMemosButton.setOnClickListener {
            val intent = Intent(this, DailyWriteActivity::class.java)
            intent.putExtra("date", repoKeyFormatter.format(currentDate.time))
            startActivity(intent)
            finish()
        }

        binding.foxFaceImage.setOnClickListener {
            binding.commentText.visibility =
                if (binding.commentText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    // ──────────────────────────────────
    // 사진 갤러리 RecyclerView + Adapter
    // ──────────────────────────────────
    private fun setupPhotoGallery() {
        photoGalleryAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photoString ->
                showPhotoDialog(photoString)
            },
            onDeleteClick = { position ->
                showDeleteConfirmDialog(position)
            },
            onAddClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        binding.photoGalleryRecyclerView.apply {
            layoutManager = LinearLayoutManager(
                this@DailyReadActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = photoGalleryAdapter
        }
    }

    private fun showDeleteConfirmDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("사진 삭제")
            .setMessage("이 사진을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { dialog, _ ->
                deletePhoto(position)
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deletePhoto(position: Int) {
        if (position in currentPhotoList.indices) {
            currentPhotoList.removeAt(position)
            updatePhotoGalleryUI()
            savePhotoUrls()
            Toast.makeText(this, "사진이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 전체 화면 사진 보기
     * Uri 문자열 저장 방식 기준
     */
    private fun showPhotoDialog(photoString: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
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
                    .into(imageView)
            } else {
                val imageBytes = Base64.decode(photoString, Base64.DEFAULT)
                Glide.with(this)
                    .load(imageBytes)
                    .into(imageView)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    // ──────────────────────────────────
    // 날짜 선택 & 이동
    // ──────────────────────────────────
    private fun showDatePickerDialog() {
        val year = currentDate.get(Calendar.YEAR)
        val month = currentDate.get(Calendar.MONTH)
        val day = currentDate.get(Calendar.DAY_OF_MONTH)

        val dateSetListener =
            DatePickerDialog.OnDateSetListener { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                currentDate.set(selectedYear, selectedMonth, selectedDayOfMonth)
                observeEntry()
            }

        val datePickerDialog = DatePickerDialog(this, dateSetListener, year, month, day)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun changeDate(amount: Int) {
        if (amount > 0 && isAfterToday(currentDate)) return
        currentDate.add(Calendar.DAY_OF_MONTH, amount)
        observeEntry()
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    private fun isAfterToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val compareCal = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return compareCal.equals(today) || compareCal.after(today)
    }

    // ──────────────────────────────────
    // 일기 수정 / 재분석
    // ──────────────────────────────────
    private fun toggleEditMode(isEditing: Boolean) {
        if (isEditing) {
            binding.diaryContentEditText.setText(binding.diaryContentTextView.text)
            binding.diaryContentTextView.visibility = View.GONE
            binding.diaryContentEditText.visibility = View.VISIBLE
            binding.diaryContentEditText.requestFocus()
            showKeyboard(binding.diaryContentEditText)
            binding.editInplaceButton.visibility = View.GONE
            binding.saveButton.visibility = View.VISIBLE
        } else {
            binding.diaryContentTextView.text = binding.diaryContentEditText.text
            binding.diaryContentTextView.visibility = View.VISIBLE
            binding.diaryContentEditText.visibility = View.GONE
            hideKeyboard(binding.diaryContentEditText)
            binding.editInplaceButton.visibility = View.VISIBLE
            binding.saveButton.visibility = View.GONE
        }
    }

    private fun showReanalysisDialog(updatedContent: String) {
        val dateKey = repoKeyFormatter.format(currentDate.time)
        AlertDialog.Builder(this)
            .setTitle("AI 재분석")
            .setMessage("일기 내용을 수정했습니다. AI 코멘트와 분석 결과도 새로고침할까요?")
            .setPositiveButton("예 (새로 분석)") { dialog, _ ->
                lifecycleScope.launch {
                    AnalysisRepository.requestAnalysis(
                        date = dateKey,
                        diary = updatedContent,
                        personaId = PersonaPrefs.getSelectedPersonaId(this@DailyReadActivity),
                        context = this@DailyReadActivity,
                        viewModel = viewModel)
                }
                toggleEditMode(false)
                dialog.dismiss()
            }
            .setNegativeButton("아니오 (텍스트만 저장)") { dialog, _ ->
                saveDiaryContent(updatedContent)
                toggleEditMode(false)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveDiaryContent(updatedContent: String) {
        val dateKey = repoKeyFormatter.format(currentDate.time)
        viewModel.updateEntry(date = dateKey, diary = updatedContent)
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}