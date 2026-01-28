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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.sumdays.R
import com.example.sumdays.daily.memo.MemoMergeUtils.convertStylePromptToMap
import com.example.sumdays.daily.memo.MemoMergeUtils.extractMergedText
import com.example.sumdays.daily.memo.MemoPayload
import com.example.sumdays.daily.memo.MergeRequest
import com.example.sumdays.data.style.UserStyle
import com.example.sumdays.data.style.UserStyleViewModel
import com.example.sumdays.databinding.ActivityStyleExtractionBinding
import com.example.sumdays.network.*
import com.example.sumdays.settings.prefs.LabsPrefs
import com.example.sumdays.utils.FileUtil
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.collections.plus
import kotlin.coroutines.CoroutineContext
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.image.PhotoGalleryAdapter
import com.example.sumdays.image.GalleryItem
import com.example.sumdays.image.GalleryDiffCallback
import kotlin.getValue

class StyleExtractionActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    lateinit var binding: ActivityStyleExtractionBinding
    private lateinit var styleViewModel: UserStyleViewModel
    private val selectedImageUris = mutableListOf<Uri>() // 선택된 이미지 URI 목록
    private var isBlocking = false
    private var isExtracting = false
    private var currentExtractCall: Call<StyleExtractionResponse>? = null

    private lateinit var backCallback: OnBackPressedCallback

    // Activity 종료 시 코루틴 Job 취소
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    // 이미지 다중 선택을 위한 Launcher
    private val selectImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(uris)
            updateImageCountUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStyleExtractionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ViewModel 초기화 (UserStyleViewModel 사용 가정)
        styleViewModel = ViewModelProvider(this).get(UserStyleViewModel::class.java)

        setupHeader()
        initializeImagePicker()
        setupPhotoGallery()
        setupListeners()
        setBackCallback()


        // 상태바, 네비게이션바 같은 색으로
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
                    // 추출 중이면 → 강제 취소 + 토스트 + 종료
                    Toast.makeText(
                        this@StyleExtractionActivity,
                        "스타일 추출이 취소되었습니다.",
                        Toast.LENGTH_SHORT
                    ).show()

                    isExtracting = false

                    // Retrofit 콜 강제 취소
                    currentExtractCall?.cancel()
                    currentExtractCall = null

                    // 코루틴 전부 취소
                    job.cancel()

                    finish()
                } else {
                    // 평소처럼 뒤로가기
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
            Toast.makeText(this, "텍스트와 이미지를 합쳐 최소 5개 이상 제공해야 합니다. (현재: $totalCount 개)", Toast.LENGTH_LONG).show()
            return
        }

        binding.runExtractionButton.isEnabled = false
        binding.runExtractionButton.text = "스타일 분석 중..."

        isExtracting = true
        showLoading(true)

        launch(Dispatchers.IO) {
            try {
                // 2. Multipart Part 구성
                // 텍스트 일기 목록을 JSON 문자열로 변환하여 하나의 RequestBody 파트로 만듭니다.
                val diaryPart = createTextPart(textDiaries) // <--- 함수 변경
                val imageParts = createImageParts(currentPhotoUris)

                // 3. 서버 호출
                callApi(diaryPart, imageParts) // <--- 파라미터 변경

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StyleExtractionActivity, "파일 처리 중 오류 발생: ${e.message}", Toast.LENGTH_LONG).show()
                    isExtracting = false
                    showLoading(false)
                    resetUi()
                }
            }
        }
    }

    // --- 2. Multipart 구성 도우미 함수 변경 ---

    // diaries 목록을 JSON 문자열로 변환하여 하나의 RequestBody 파트로 반환하도록 변경
    private fun createTextPart(diaries: List<String>): RequestBody {
        // Gson을 사용하여 List<String>을 JSON 배열 문자열로 변환
        val jsonDiaries = Gson().toJson(diaries)

        // Content-Type: application/json; charset=utf-8로 RequestBody 생성
        return jsonDiaries.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }

    private fun createImageParts(uris: List<Uri>): List<MultipartBody.Part> {
        // 기존 코드 유지 (변경 불필요)
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

    // --- 3. Retrofit API 호출 함수 변경 ---

    // 파라미터를 변경된 createTextPart의 결과(RequestBody)로 받도록 수정
    private fun callApi(diaryPart: RequestBody, imageParts: List<MultipartBody.Part>) {
        // ApiClient.api.extractStyle(diaryParts, imageParts) 호출 부분 수정
        currentExtractCall = ApiClient.api.extractStyle(
            diaryPart, // <--- 하나의 RequestBody 파트로 전달
            imageParts
        )
        currentExtractCall!!
            .enqueue(object : Callback<StyleExtractionResponse> {

                override fun onResponse(call: Call<StyleExtractionResponse>, response: Response<StyleExtractionResponse>) {
                    launch { // CoroutineScope를 사용하여 Main/IO 스레드 관리
                        if (response.isSuccessful) {
                            val styleResponse = response.body()
                            // ★★★ 성공 조건 변경: styleResponse가 null이 아니고 style_vector가 null이 아닌 경우 ★★★
                            if (styleResponse != null && styleResponse.style_vector != null) {
                                // 4. 추출 성공 및 Room DB 저장
                                saveStyleData(styleResponse)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@StyleExtractionActivity, "스타일 추출 완료! 설정 목록에서 확인하세요.", Toast.LENGTH_LONG).show()
                                    isExtracting = false
                                    showLoading(false)
                                    resetUi() // UI 활성화
                                    finish()
                                }
                            } else {
                                // 서버 응답 실패 처리 (success: false 이거나 데이터 불완전)
                                withContext(Dispatchers.Main) {
                                    // message가 있다면 출력하고, 없다면 "스타일 추출 실패" 출력
                                    isExtracting = false
                                    showLoading(false)
                                    resetUi() // UI 활성화
                                    Toast.makeText(
                                        this@StyleExtractionActivity,
                                        styleResponse?.message ?: "스타일 추출에 필요한 데이터가 서버로부터 오지 않았습니다.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            // HTTP 에러 처리
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
                        Toast.makeText(this@StyleExtractionActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
    }

    // --- 4. Room DB 저장 로직 (IO 스레드) ---

    private suspend fun saveStyleData(response: StyleExtractionResponse) {

        // 1. 필수 데이터 널 검사 및 안전 호출 (Safe Call)
        // StyleExtractionResponse에서 데이터 필드가 null이면 저장을 진행할 수 없습니다.
        val styleVector = response.style_vector
        val styleExamples = response.style_examples
        val stylePrompt = response.style_prompt

        // 이 세 필드 중 하나라도 null이면 저장을 중단합니다.
        if (styleVector == null || styleExamples == null || stylePrompt == null) {
            // 이 부분은 UI에 오류를 알리거나 로그를 남기는 등 추가 처리가 필요할 수 있습니다.
            // 현재는 단순히 함수 실행을 종료합니다.
            // 예를 들어, throw Exception("스타일 데이터가 불완전합니다.") 등을 고려할 수 있습니다.
            return
        }

        val newStyleName = styleViewModel.generateNextStyleName() // 고유한 이름 생성 함수 필요

        // 2. Non-nullable 변수를 사용하여 UserStyle 객체 생성
        val newStyle = UserStyle(
            styleName = newStyleName,
            styleVector = styleVector,     // Non-nullable 변수 사용
            styleExamples = styleExamples, // Non-nullable 변수 사용
            stylePrompt = stylePrompt,      // Non-nullable 변수 사용
            sampleDiary = ""
        )

        val diary = generateSampleDiary(newStyle)

        // 3. 새로운 스타일 저장
        val finalStyle = newStyle.copy(sampleDiary = diary)
        styleViewModel.insertStyle(finalStyle)
    }

    // --- 5. UI 및 유틸리티 ---

    private fun resetUi() {
        isExtracting = false
        binding.runExtractionButton.isEnabled = true
        binding.runExtractionButton.text = "스타일 추출 실행"
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

    private fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            isBlocking = isLoading

            // UI 블로킹 처리
            binding.runExtractionButton.isEnabled = !isLoading
            //binding.selectImagesButton.isEnabled = !isLoading
            binding.diaryTextInput.isEnabled = !isLoading

            if (isLoading) {
                // 화면 잠금
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
    /*---------------------------PhotoGallery 관련 수정사항(일단 급해서 여기 넣었는데 뭔가 따로 빼고 싶네요)------------------*/
    private lateinit var photoGalleryAdapter: PhotoGalleryAdapter
    private lateinit var pickImageLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private val currentPhotoUris = mutableListOf<Uri>()
    private fun initializeImagePicker() {
        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    addPhoto(uri)
                }
            }
    }

    private fun addPhoto(uri: Uri) {
        currentPhotoUris.add(uri)
        updatePhotoGalleryUI()
        updateImageCountUi()
    }

    private fun updatePhotoGalleryUI() {
        val items = currentPhotoUris.map { GalleryItem.Photo(it.toString()) } + GalleryItem.Add
        photoGalleryAdapter.submitList(items)
        binding.photoGalleryRecyclerView.visibility = View.VISIBLE
    }
    private fun setupPhotoGallery() {
        photoGalleryAdapter = PhotoGalleryAdapter(
            onPhotoClick = { photoUrl ->
                showPhotoDialog(photoUrl)
            },
            onPhotoLongClick = { position -> // ★ 롱 클릭 시 삭제 다이얼로그 호출
                showDeleteConfirmDialog(position)
            },
            onAddClick = {
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
        binding.photoGalleryRecyclerView.apply {
            layoutManager =
                LinearLayoutManager(this@StyleExtractionActivity, LinearLayoutManager.HORIZONTAL, false)
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
            // 리스트에서 제거 및 UI/DB 업데이트
            currentPhotoUris.removeAt(position)
            updatePhotoGalleryUI()

            Toast.makeText(this, "사진이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
        updateImageCountUi()
    }
    private fun showPhotoDialog(photoUrl: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this)
        imageView.setBackgroundColor(getColor(android.R.color.black))
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        Glide.with(this).load(photoUrl).into(imageView)

        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }
}