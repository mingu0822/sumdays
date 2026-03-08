package com.example.sumdays.settings

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.sumdays.R
import com.example.sumdays.daily.memo.MemoMergeUtils.convertStylePromptToMap
import com.example.sumdays.daily.memo.MemoMergeUtils.extractMergedText
import com.example.sumdays.daily.memo.MemoPayload
import com.example.sumdays.daily.memo.MergeRequest
import com.example.sumdays.data.style.UserStyle
import com.example.sumdays.data.style.UserStyleViewModel
import com.example.sumdays.databinding.ActivityStyleExtractionBinding
import com.example.sumdays.image.GalleryItem
import com.example.sumdays.image.PhotoGalleryAdapter
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.StyleExtractionResponse
import com.example.sumdays.settings.prefs.LabsPrefs
import com.example.sumdays.utils.FileUtil
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import kotlin.coroutines.CoroutineContext

class StyleExtractionActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var binding: ActivityStyleExtractionBinding
    private lateinit var styleViewModel: UserStyleViewModel

    private var isBlocking = false
    private var isExtracting = false
    private var currentExtractCall: Call<StyleExtractionResponse>? = null

    private lateinit var backCallback: OnBackPressedCallback

    // 코루틴 스코프
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    /*--------------------------- PhotoGallery 관련 필드 ------------------*/
    private lateinit var photoGalleryAdapter: PhotoGalleryAdapter
    private lateinit var pickImageLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private val currentPhotoUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStyleExtractionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        styleViewModel = ViewModelProvider(this)[UserStyleViewModel::class.java]

        setupHeader()
        initializeImagePicker()
        setupPhotoGallery()
        setupListeners()
        setBackCallback()
        updateImageCountUi()

        val rootView = findViewById<View>(R.id.setting_style_extraction_root)
        setupEdgeToEdge(rootView)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentExtractCall?.cancel()
        job.cancel()
    }

    private fun setBackCallback() {
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isExtracting) {
                    Toast.makeText(
                        this@StyleExtractionActivity,
                        "스타일 추출이 취소되었습니다.",
                        Toast.LENGTH_SHORT
                    ).show()

                    isExtracting = false
                    currentExtractCall?.cancel()
                    currentExtractCall = null
                    job.cancel()

                    finish()
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun setupHeader() {
        binding.header.headerTitle.text = "스타일 추출"
        binding.header.headerBackIcon.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        binding.runExtractionButton.setOnClickListener {
            handleExtractStyle()
        }
    }

    private fun updateImageCountUi() {
        binding.selectedImageCount.text = "선택된 이미지: ${currentPhotoUris.size}개"
    }

    // --- 1. 스타일 추출 실행 (유효성 검사 포함) ---
    private fun handleExtractStyle() {
        val textInput = binding.diaryTextInput.text.toString().trim()

        val textDiaries = textInput.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val totalCount = textDiaries.size + currentPhotoUris.size

        if (totalCount < 5) {
            Toast.makeText(
                this,
                "텍스트와 이미지를 합쳐 최소 5개 이상 제공해야 합니다. (현재: $totalCount 개)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        binding.runExtractionButton.isEnabled = false
        binding.runExtractionButton.text = "스타일 분석 중..."

        isExtracting = true
        showLoading(true)

        launch(Dispatchers.IO) {
            try {
                val diaryPart = createTextPart(textDiaries)
                val imageParts = createImageParts(currentPhotoUris)

                callApi(diaryPart, imageParts)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@StyleExtractionActivity,
                        "파일 처리 중 오류 발생: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    isExtracting = false
                    showLoading(false)
                    resetUi()
                }
            }
        }
    }

    // --- 2. Multipart 구성 도우미 함수 ---
    private fun createTextPart(diaries: List<String>): RequestBody {
        val jsonDiaries = Gson().toJson(diaries)
        return jsonDiaries.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }

    private fun createImageParts(uris: List<Uri>): List<MultipartBody.Part> {
        return uris.mapNotNull { uri ->
            val file = FileUtil.getFileFromUri(this, uri)
            if (file != null && file.exists()) {
                val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("images", file.name, requestBody)
            } else {
                null
            }
        }
    }

    // --- 3. Retrofit API 호출 ---
    private fun callApi(diaryPart: RequestBody, imageParts: List<MultipartBody.Part>) {
        currentExtractCall = ApiClient.api.extractStyle(diaryPart, imageParts)

        currentExtractCall!!.enqueue(object : Callback<StyleExtractionResponse> {
            override fun onResponse(
                call: Call<StyleExtractionResponse>,
                response: Response<StyleExtractionResponse>
            ) {
                launch {
                    if (response.isSuccessful) {
                        val styleResponse = response.body()
                        if (styleResponse != null && styleResponse.style_vector != null) {
                            saveStyleData(styleResponse)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@StyleExtractionActivity,
                                    "스타일 추출 완료! 설정 목록에서 확인하세요.",
                                    Toast.LENGTH_LONG
                                ).show()
                                isExtracting = false
                                showLoading(false)
                                resetUi()
                                finish()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isExtracting = false
                                showLoading(false)
                                resetUi()
                                Toast.makeText(
                                    this@StyleExtractionActivity,
                                    styleResponse?.message
                                        ?: "스타일 추출에 필요한 데이터가 서버로부터 오지 않았습니다.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isExtracting = false
                            showLoading(false)
                            resetUi()
                            Toast.makeText(
                                this@StyleExtractionActivity,
                                "서버 응답 오류 (코드: ${response.code()})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            override fun onFailure(call: Call<StyleExtractionResponse>, t: Throwable) {
                launch(Dispatchers.Main) {
                    isExtracting = false
                    showLoading(false)
                    resetUi()
                    Toast.makeText(
                        this@StyleExtractionActivity,
                        "네트워크 오류: ${t.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    // --- 4. Room DB 저장 로직 ---
    private suspend fun saveStyleData(response: StyleExtractionResponse) {
        val styleVector = response.style_vector
        val styleExamples = response.style_examples
        val stylePrompt = response.style_prompt

        if (styleVector == null || styleExamples == null || stylePrompt == null) {
            return
        }

        val newStyleName = styleViewModel.generateNextStyleName()

        val newStyle = UserStyle(
            styleName = newStyleName,
            styleVector = styleVector,
            styleExamples = styleExamples,
            stylePrompt = stylePrompt,
            sampleDiary = ""
        )

        val diary = generateSampleDiary(newStyle)
        val finalStyle = newStyle.copy(sampleDiary = diary)
        styleViewModel.insertStyle(finalStyle)
    }

    private suspend fun generateSampleDiary(style: UserStyle): String {
        val promptMap = convertStylePromptToMap(style.stylePrompt)
        val examples = style.styleExamples
        val styleVector = style.styleVector
        val advancedFlag = LabsPrefs.getAdvancedFlag(this@StyleExtractionActivity)
        val temperature = LabsPrefs.getTemperature(this@StyleExtractionActivity)
        val lengthLevel = LabsPrefs.getLengthLevel(this@StyleExtractionActivity)

        val memosPayload = listOf(
            MemoPayload("아침에 늦잠을 자서 밥을 굶었다.", 1),
            MemoPayload("점심에는 라면을 먹었다. 맛있었다.", 2),
            MemoPayload("저녁에는 삼겹살을 구워먹었다. 즐거운 하루였다.", 3)
        )

        val request = MergeRequest(
            memos = memosPayload,
            endFlag = true,
            stylePrompt = promptMap,
            styleExamples = examples,
            styleVector = styleVector,
            advancedFlag = advancedFlag,
            temperature = temperature,
            lengthLevel = lengthLevel
        )

        return withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.api.mergeMemos(request)
                val json = response.body()
                extractMergedText(json ?: JsonObject())
            } catch (e: Exception) {
                "샘플 생성 실패 :("
            }
        }
    }

    private fun resetUi() {
        isExtracting = false
        binding.runExtractionButton.isEnabled = true
        binding.runExtractionButton.text = "스타일 추출 실행"
    }

    private fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            isBlocking = isLoading

            binding.runExtractionButton.isEnabled = !isLoading
            binding.diaryTextInput.isEnabled = !isLoading

            if (isLoading) {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.loadingGifView.visibility = View.VISIBLE

                Glide.with(this)
                    .asGif()
                    .load(R.drawable.loading_animation)
                    .into(binding.loadingGifView)
            } else {
                binding.loadingOverlay.visibility = View.GONE
                binding.loadingGifView.visibility = View.GONE

                Glide.with(this).clear(binding.loadingGifView)
            }
        }
    }

    /*--------------------------- PhotoGallery 관련 구현 ------------------*/

    private fun initializeImagePicker() {
        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    addPhoto(uri)
                }
            }
    }

    private fun addPhoto(uri: Uri) {
        if (!currentPhotoUris.contains(uri)) {
            currentPhotoUris.add(uri)
            updatePhotoGalleryUI()
            updateImageCountUi()
        } else {
            Toast.makeText(this, "이미 추가된 사진입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePhotoGalleryUI() {
        val items = currentPhotoUris.map { GalleryItem.Photo(it.toString()) } + GalleryItem.Add
        photoGalleryAdapter.submitList(items)
        binding.photoGalleryRecyclerView.visibility =
            if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupPhotoGallery() {
        photoGalleryAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photoUrl ->
                showPhotoDialog(photoUrl)
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
                this@StyleExtractionActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = photoGalleryAdapter
        }

        updatePhotoGalleryUI()
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
        if (position in currentPhotoUris.indices) {
            currentPhotoUris.removeAt(position)
            updatePhotoGalleryUI()
            updateImageCountUi()
            Toast.makeText(this, "사진이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPhotoDialog(photoUrl: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this).apply {
            setBackgroundColor(getColor(android.R.color.black))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        Glide.with(this)
            .load(Uri.parse(photoUrl))
            .into(imageView)

        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }
}