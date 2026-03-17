package com.example.sumdays

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sumdays.audio.AudioRecorderHelper
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.daily.memo.MemoAdapter
import com.example.sumdays.daily.memo.MemoDragAndDropCallback
import com.example.sumdays.daily.memo.MemoViewModel
import com.example.sumdays.daily.memo.MemoViewModelFactory
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.image.GalleryItem
import com.example.sumdays.image.GridSpacingItemDecoration
import com.example.sumdays.image.PhotoGalleryAdapter
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DailyWriteActivity : AppCompatActivity() {

    private lateinit var date: String
    private lateinit var memoAdapter: MemoAdapter

    // UI 뷰들
    private lateinit var dateTextView: TextView
    private lateinit var memoListView: RecyclerView
    private lateinit var memoInputEditText: EditText
    private lateinit var sendIcon: ImageView
    private lateinit var micIcon: ImageView
    private lateinit var stopIcon: ImageView
    private lateinit var imageIcon: ImageView
    private lateinit var audioWaveView: LinearLayout
    private lateinit var waveBar1: View
    private lateinit var waveBar2: View
    private lateinit var waveBar3: View
    private lateinit var readDiaryButton: Button
    private lateinit var navBarController: NavBarController
    private lateinit var audioRecorderHelper: AudioRecorderHelper

    // 오른쪽 드로어 & 이미지 갤러리
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageDrawerContainer: LinearLayout
    private lateinit var imagePanelTitle: TextView
    private lateinit var photoGalleryAdapter: PhotoGalleryAdapter

    // DB에 저장할 Uri 문자열 리스트
    private val currentPhotoList = mutableListOf<String>()
    private var currentEntryLiveData: LiveData<DailyEntry?>? = null

    private val memoViewModel: MemoViewModel by viewModels {
        MemoViewModelFactory(
            (application as MyApplication).repository
        )
    }
    private val dailyEntryViewModel: DailyEntryViewModel by viewModels()

    private var isRecording = false
    private var waveAnimatorSet: AnimatorSet? = null
    private var pendingAudioMemoId: Int? = null
    private var isApiProcessingAudio = false

    private lateinit var pickImagesLauncher: ActivityResultLauncher<Array<String>>

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_daily_write)

        drawerLayout = findViewById(R.id.drawer_layout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.write)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeImagePicker()
        initViews()
        audioRecorderHelper = createAudioRecorderHelper()
        handleIntent(intent)
        setupClickListeners()
        applyThemeModeSettings()

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.WRITE) {
            val currentMemos = memoAdapter.currentList
            Intent(this, DailySumActivity::class.java).apply {
                putExtra("date", date)
                putParcelableArrayListExtra("memo_list", ArrayList(currentMemos))
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                finish()
            }
        }

        val rootView = findViewById<View>(R.id.write)
        setupEdgeToEdge(rootView)
    }

    private fun initializeImagePicker() {
        pickImagesLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNullOrEmpty()) return@registerForActivityResult

                uris.forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                    } catch (_: Exception) {
                    }
                }

                addPhotos(uris)
            }
    }

    private fun applyThemeModeSettings() {
        ThemeState.isDarkMode =
            (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode) {
            imageDrawerContainer.setBackgroundResource(R.drawable.bg_image_drawer_rounded_dark)
            imageRecyclerView.setBackgroundColor(getColor(R.color.dark_item_card_background))
            imagePanelTitle.setTextColor(getColor(android.R.color.white))

            readDiaryButton.setTextColor(getColor(R.color.white))
            memoInputEditText.setTextColor(getColor(R.color.black))
            memoInputEditText.setHintTextColor(getColor(R.color.black))
            sendIcon.setImageResource(R.drawable.ic_send_white)
            micIcon.setImageResource(R.drawable.ic_mic_white)
            imageIcon.setImageResource(R.drawable.ic_image_white)
        } else {
            imageDrawerContainer.setBackgroundResource(R.drawable.bg_image_drawer_rounded_light)
            imageRecyclerView.setBackgroundColor(getColor(android.R.color.white))
            imagePanelTitle.setTextColor(getColor(android.R.color.black))

            readDiaryButton.setTextColor(getColor(R.color.white))
            memoInputEditText.setTextColor(getColor(R.color.black))
            memoInputEditText.setHintTextColor(getColor(R.color.black))
            sendIcon.setImageResource(R.drawable.ic_send_black)
            micIcon.setImageResource(R.drawable.ic_mic_black)
            imageIcon.setImageResource(R.drawable.ic_image_black)
        }
    }

    private fun createAudioRecorderHelper(): AudioRecorderHelper {
        return AudioRecorderHelper(
            activity = this,
            onRecordingStarted = {
                runOnUiThread {
                    Toast.makeText(this, "녹음 시작...", Toast.LENGTH_SHORT).show()
                    isRecording = true
                    micIcon.visibility = View.GONE
                    stopIcon.visibility = View.VISIBLE
                    sendIcon.visibility = View.GONE
                    audioWaveView.visibility = View.VISIBLE
                    memoInputEditText.visibility = View.INVISIBLE
                    startWaveAnimation()
                }
            },
            onRecordingStopped = {
                runOnUiThread {
                    Toast.makeText(this, "녹음 완료. 텍스트 변환 중...", Toast.LENGTH_SHORT).show()
                    isRecording = false

                    isApiProcessingAudio = true
                    micIcon.visibility = View.VISIBLE
                    micIcon.isEnabled = false
                    micIcon.alpha = 0.5f
                    stopIcon.visibility = View.GONE
                    sendIcon.visibility = View.VISIBLE

                    audioWaveView.visibility = View.GONE
                    memoInputEditText.visibility = View.VISIBLE
                    stopWaveAnimation()

                    val tempId = System.currentTimeMillis().toInt()
                    val dummyMemo = Memo(
                        id = tempId,
                        content = "음성 인식 중...",
                        timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                            Calendar.getInstance().time
                        ),
                        date = date,
                        order = memoAdapter.itemCount,
                        type = "audio"
                    )
                    pendingAudioMemoId = tempId

                    val currentList = memoAdapter.currentList.toMutableList()
                    currentList.add(dummyMemo)
                    memoAdapter.submitList(currentList)
                    memoListView.smoothScrollToPosition(currentList.size - 1)
                }
            },
            onRecordingComplete = { filePath, transcribedText ->
                Log.d("DailyWriteActivity", "녹음 완료, 파일 경로: $filePath")
                Log.d("DailyWriteActivity", "변환된 텍스트: $transcribedText")
                runOnUiThread {
                    val finalContent = transcribedText ?: "[오디오 파일: $filePath]"
                    removeDummyMemoAndAddFinal(finalContent, "audio")
                    Toast.makeText(this, "텍스트 변환 완료.", Toast.LENGTH_SHORT).show()

                    isApiProcessingAudio = false
                    micIcon.isEnabled = true
                    micIcon.alpha = 1.0f
                }
            },
            onRecordingFailed = { errorMessage ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "음성 인식에 실패했습니다:\n$errorMessage",
                        Toast.LENGTH_SHORT
                    ).show()
                    removeDummyMemo("[오류: $errorMessage]", "audio")

                    isApiProcessingAudio = false
                    micIcon.isEnabled = true
                    micIcon.alpha = 1.0f
                }
            },
            onPermissionDenied = {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "음성 녹음을 사용하려면 마이크 권한이 필요합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onShowPermissionRationale = {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "메모를 음성으로 녹음하려면 마이크 권한이 필요합니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun initViews() {
        dateTextView = findViewById(R.id.date_text_view)
        memoListView = findViewById(R.id.memo_list_view)
        memoInputEditText = findViewById(R.id.memo_input_edittext)
        sendIcon = findViewById(R.id.send_icon)
        micIcon = findViewById(R.id.mic_icon)
        stopIcon = findViewById(R.id.stop_icon)
        imageIcon = findViewById(R.id.image_icon)
        readDiaryButton = findViewById(R.id.read_diary_button)

        audioWaveView = findViewById(R.id.audio_wave_view)
        waveBar1 = findViewById(R.id.wave_bar_1)
        waveBar2 = findViewById(R.id.wave_bar_2)
        waveBar3 = findViewById(R.id.wave_bar_3)

        imageDrawerContainer = findViewById(R.id.image_drawer_container)
        imagePanelTitle = findViewById(R.id.image_panel_title)
        imageRecyclerView = findViewById(R.id.image_recycler_view)

        memoListView.layoutManager = LinearLayoutManager(this)
        memoAdapter = MemoAdapter()
        memoListView.adapter = memoAdapter

        memoAdapter.setOnItemClickListener(object : MemoAdapter.OnItemClickListener {
            override fun onItemClick(memo: Memo) {
                showEditMemoDialog(memo)
            }
        })

        val dragAndDropCallback = MemoDragAndDropCallback(
            adapter = memoAdapter,
            onMove = { _, _ -> },
            onDelete = { position ->
                val memoToDelete = memoAdapter.currentList[position]
                memoViewModel.delete(memoToDelete)
            },
            onDragStart = {},
            onDragEnd = {
                val updatedList = memoAdapter.currentList.toMutableList()
                for (i in updatedList.indices) {
                    updatedList[i] = updatedList[i].copy(order = i)
                }
                memoViewModel.updateAll(updatedList)
            }
        )
        val itemTouchHelper = ItemTouchHelper(dragAndDropCallback)
        itemTouchHelper.attachToRecyclerView(memoListView)

        photoGalleryAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photoString ->
                showPhotoDialog(photoString)
            },
            onDeleteClick = { position ->
                showDeletePhotoDialog(position)
            },
            onAddClick = {
                pickImagesLauncher.launch(arrayOf("image/*"))
            }
        )

        imageRecyclerView.apply {
            layoutManager = GridLayoutManager(this@DailyWriteActivity, 2)

            if (itemDecorationCount == 0) {
                val spacingDp = 8
                val spacingPx = (spacingDp * resources.displayMetrics.density).toInt()
                addItemDecoration(
                    GridSpacingItemDecoration(
                        spanCount = 2,
                        spacing = spacingPx,
                        includeEdge = true
                    )
                )
            }

            adapter = photoGalleryAdapter
        }

        updatePhotoGalleryUI()
    }

    private fun handleIntent(intent: Intent?) {
        date = intent?.getStringExtra("date")
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                Calendar.getInstance().time
            )
        dateTextView.text = date

        memoViewModel.getMemosForDate(date).observe(this) { memos ->
            memos?.let {
                if (pendingAudioMemoId != null && !it.any { memo -> memo.id == pendingAudioMemoId }) {
                    val currentList = memoAdapter.currentList.toMutableList()
                    memoAdapter.submitList(currentList)
                } else {
                    memoAdapter.submitList(it)
                }
            }
        }

        observeDailyEntry()
    }

    private fun observeDailyEntry() {
        currentEntryLiveData?.removeObservers(this)
        currentEntryLiveData = dailyEntryViewModel.getEntry(date)
        currentEntryLiveData?.observe(this) { entry ->
            val diaryExists = !entry?.diary.isNullOrEmpty()

            if (diaryExists) {
                readDiaryButton.isEnabled = true
                readDiaryButton.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.btn_foxrange)
                    )
            } else {
                readDiaryButton.isEnabled = false
                readDiaryButton.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.gray)
            }

            currentPhotoList.clear()
            entry?.photoUrls?.let { photoString ->
                if (photoString.isNotEmpty()) {
                    currentPhotoList.addAll(photoString.split(","))
                }
            }
            updatePhotoGalleryUI()
        }
    }

    fun showEditMemoDialog(memo: Memo) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_memo, null)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text_memo_content)
        editText.setText(memo.content)

        builder.setView(dialogView)
            .setPositiveButton("수정") { _, _ ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    val updatedMemo = memo.copy(content = newContent)
                    memoViewModel.update(updatedMemo)
                } else {
                    Toast.makeText(this, "내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("삭제") { dialog, _ ->
                memoViewModel.delete(memo)
                dialog.dismiss()
            }
            .setNeutralButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun setupClickListeners() {
        readDiaryButton.setOnClickListener {
            val intent = Intent(this, DailyReadActivity::class.java)
            intent.putExtra("date", date)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        sendIcon.setOnClickListener {
            val memoContent = memoInputEditText.text.toString().trim()
            if (memoContent.isNotEmpty()) {
                addTextMemoToList(memoContent, "text")
                memoInputEditText.text.clear()
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            } else {
                Toast.makeText(this, "메모 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        micIcon.setOnClickListener {
            if (isApiProcessingAudio) {
                Toast.makeText(this, "이전 음성을 처리 중입니다...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            audioRecorderHelper.checkPermissionAndToggleRecording()
        }

        stopIcon.setOnClickListener {
            audioRecorderHelper.checkPermissionAndToggleRecording()
        }

        memoInputEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                stopIcon.visibility = View.GONE
            } else {
                if (isRecording) {
                    micIcon.visibility = View.GONE
                    stopIcon.visibility = View.VISIBLE
                } else {
                    micIcon.visibility = View.VISIBLE
                    stopIcon.visibility = View.GONE
                }
            }
        }

        imageIcon.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                drawerLayout.openDrawer(GravityCompat.END)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorderHelper.release()
        waveAnimatorSet?.cancel()
    }

    private fun startWaveAnimation() {
        waveAnimatorSet?.cancel()

        val anim1 = ObjectAnimator.ofFloat(waveBar1, "scaleY", 1.0f, 0.3f, 0.7f, 1.0f).apply {
            duration = 400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val anim2 = ObjectAnimator.ofFloat(waveBar2, "scaleY", 1.0f, 0.5f, 0.2f, 0.8f, 1.0f).apply {
            duration = 400
            startDelay = 150
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val anim3 = ObjectAnimator.ofFloat(waveBar3, "scaleY", 1.0f, 0.6f, 1.0f, 0.4f, 1.0f).apply {
            duration = 400
            startDelay = 300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        waveAnimatorSet = AnimatorSet().apply {
            playTogether(anim1, anim2, anim3)
            start()
        }
    }

    private fun stopWaveAnimation() {
        waveAnimatorSet?.cancel()
        waveAnimatorSet = null
        waveBar1.scaleY = 1.0f
        waveBar2.scaleY = 1.0f
        waveBar3.scaleY = 1.0f
    }

    private fun addTextMemoToList(content: String, memoType: String = "text") {
        val currentTime =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
        val newMemo = Memo(
            id = 0,
            content = content,
            timestamp = currentTime,
            date = date,
            order = memoAdapter.itemCount,
            type = memoType
        )

        Log.d("test", "$content / $currentTime / $date / ${memoAdapter.itemCount} / $memoType")
        memoViewModel.insert(newMemo)
        memoListView.smoothScrollToPosition(memoAdapter.itemCount)
    }

    private fun removeDummyMemoAndAddFinal(newContent: String, memoType: String) {
        if (pendingAudioMemoId != null) {
            val currentList = memoAdapter.currentList.toMutableList()
            val removed = currentList.removeAll { it.id == pendingAudioMemoId }

            if (removed) {
                memoAdapter.submitList(currentList)
            }
            pendingAudioMemoId = null
        }
        addTextMemoToList(newContent, memoType)
    }

    private fun removeDummyMemo(errorContent: String, memoType: String) {
        if (pendingAudioMemoId != null) {
            val currentList = memoAdapter.currentList.toMutableList()
            val removed = currentList.removeAll { it.id == pendingAudioMemoId }

            if (removed) {
                memoAdapter.submitList(currentList)
            }
            pendingAudioMemoId = null
        }
    }

    // 사진 저장 / 복원 / UI 갱신
    private fun addPhotos(uris: List<Uri>) {
        var addedCount = 0

        uris.forEach { uri ->
            val uriString = uri.toString()
            if (!currentPhotoList.contains(uriString)) {
                currentPhotoList.add(uriString)
                addedCount++
            }
        }

        updatePhotoGalleryUI()
        savePhotoUrls()

        if (addedCount > 0) {
            Toast.makeText(this, "${addedCount}장의 사진이 추가되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePhotoGalleryUI() {
        val items = currentPhotoList.map { GalleryItem.Photo(it) } + GalleryItem.Add
        photoGalleryAdapter.submitList(items)
    }

    private fun savePhotoUrls() {
        val photoDataString = currentPhotoList.joinToString(",")
        dailyEntryViewModel.updateEntry(
            date = date,
            photoUrls = photoDataString
        )
    }

    private fun showPhotoDialog(photoString: String) {
        val isDarkMode =
            AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        val dialog = Dialog(
            this,
            if (isDarkMode) android.R.style.Theme_Black_NoTitleBar_Fullscreen
            else android.R.style.Theme_Light_NoTitleBar_Fullscreen
        )

        val bgColor =
            if (isDarkMode) getColor(R.color.dark_item_card_background)
            else getColor(android.R.color.white)

        val imageView = ImageView(this).apply {
            setBackgroundColor(bgColor)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val isUriOrPath =
            photoString.startsWith("content://") ||
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

        dialog.setContentView(imageView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        imageView.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showDeletePhotoDialog(position: Int) {
        if (position !in currentPhotoList.indices) return

        AlertDialog.Builder(this)
            .setMessage("이 사진을 삭제할까요?")
            .setPositiveButton("삭제") { dialog, _ ->
                currentPhotoList.removeAt(position)
                updatePhotoGalleryUI()
                savePhotoUrls()
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}