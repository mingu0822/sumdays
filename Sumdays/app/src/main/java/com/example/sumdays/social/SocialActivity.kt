package com.example.sumdays.social

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt
import com.example.sumdays.LoginActivity
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.settings.EditProfileActivity
import com.example.sumdays.settings.prefs.ProfileImagePrefs
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.settings.profileimage.ProfileImageItem
import com.example.sumdays.settings.profileimage.ProfileImageItemType
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import android.widget.EditText
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
import com.example.sumdays.social.reqeust.AddFriendDialog
import com.example.sumdays.social.reqeust.FriendRequestDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository

class SocialActivity : AppCompatActivity() {
    private lateinit var navBarController: NavBarController
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var recyclerSocial: RecyclerView
    private lateinit var etSearchSocial: EditText
    private lateinit var socialAdapter: SocialAdapter

    private lateinit var tvTitle: TextView
    private lateinit var tvAllSocialSection: TextView
    private lateinit var tvSocialRequests: TextView
    private lateinit var btnAddSocial: ImageButton
    private lateinit var btnUpdate: ImageButton
    private lateinit var myProfileCard: View
    private lateinit var myProfileImageContainer: FrameLayout
    private lateinit var imgMyPhoto: ImageView
    private lateinit var imgMyBase: ImageView
    private lateinit var imgMyMouth: ImageView
    private lateinit var imgMyEyes: ImageView
    private lateinit var imgMyAccessory: ImageView
    private lateinit var tvEmpty: TextView
    private lateinit var tvError: TextView

    private lateinit var viewModel: SocialViewModel
    private val userStatsPrefs: UserStatsPrefs by lazy { UserStatsPrefs(this) }
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val deletedFriendId = result.data?.getIntExtra("deletedFriendId", -1) ?: -1
                if (deletedFriendId != -1) {
                    viewModel.removeFriendLocally(deletedFriendId)
                }
            }
        }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 기본 설정
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social)
        SessionManager.init(applicationContext)
        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.SOCIAL)

        initViewModel()
        initView()
        setupClickListeners()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        applyThemeModeSettings()
        viewModel.loadFriends()
    }
    private fun initViewModel() {
        val repository = SocialRepository()
        val factory = SocialViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[SocialViewModel::class.java]
    }
    private fun initView() {
        rootLayout = findViewById(R.id.setting_main_root)
        recyclerSocial = findViewById(R.id.recyclerSocial)
        etSearchSocial = findViewById(R.id.etSearchSocial)
        tvTitle = findViewById(R.id.tvTitle)
        tvAllSocialSection = findViewById(R.id.tvAllSocialSection)
        tvSocialRequests = findViewById(R.id.tvSocialRequests)
        btnAddSocial = findViewById(R.id.btnAddSocial)
        btnUpdate = findViewById(R.id.btnUpdate)
        myProfileCard = findViewById(R.id.myProfileCard)
        myProfileImageContainer = findViewById(R.id.myProfileImageContainer)
        imgMyPhoto = findViewById(R.id.imgMyPhoto)
        imgMyBase = findViewById(R.id.imgMyBase)
        imgMyMouth = findViewById(R.id.imgMyMouth)
        imgMyEyes = findViewById(R.id.imgMyEyes)
        imgMyAccessory = findViewById(R.id.imgMyAccessory)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvError = findViewById(R.id.tvError)

    }

    private fun setupClickListeners() {

        tvSocialRequests.setOnClickListener {
            val dialog = FriendRequestDialog()
            dialog.show(supportFragmentManager, "FriendRequestDialog")
        }

        btnAddSocial.setOnClickListener {
            val dialog = AddFriendDialog()
            dialog.show(supportFragmentManager, "AddFriendDialog")
        }
        btnUpdate.setOnClickListener{
            viewModel.loadFriends()
        }
        myProfileCard.setOnClickListener {
            showMyProfileDialog()
        }

    }
    private fun setupRecyclerView() {
        socialAdapter = SocialAdapter(
            friendList = mutableListOf(),
            onItemClick = { friendInfo ->
                val intent = Intent(this, SocialDetailActivity::class.java)
                intent.putExtra("friendInfo", friendInfo)
                detailLauncher.launch(intent)
            },
            onButtonClick = { friendInfo ->
                Toast.makeText(
                    this,
                    "${friendInfo.nickname} 버튼 클릭",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        recyclerSocial.layoutManager = LinearLayoutManager(this)
        recyclerSocial.adapter = socialAdapter
    }

    private fun getCurrentThemeOrNull(): Theme? {
        ThemeRepository.updateOwned()
        val themeKey = ThemePrefs.getTheme(this)
        return ThemeRepository.ownedThemes[themeKey]
            ?: ThemeRepository.allThemeMap[themeKey]
    }

    private fun applyThemeModeSettings() {
        val currentTheme = getCurrentThemeOrNull() ?: return
        val basicTextColor = ContextCompat.getColor(this, currentTheme.themeTextColorBasic)
        val pointColor = ContextCompat.getColor(this, currentTheme.themeColorB)
        val iconColor = ContextCompat.getColor(this, currentTheme.themeColorD)

        rootLayout.setBackgroundResource(currentTheme.backgroundColor)
        etSearchSocial.setBackgroundResource(currentTheme.blockStyleA)

        tvTitle.setTextColor(basicTextColor)
        tvAllSocialSection.setTextColor(basicTextColor)
        tvSocialRequests.setTextColor(pointColor)
        etSearchSocial.setTextColor(basicTextColor)
        etSearchSocial.setHintTextColor(iconColor)
        tvEmpty.setTextColor(pointColor)
        tvError.setTextColor(pointColor)
        btnAddSocial.imageTintList = ColorStateList.valueOf(iconColor)
        btnUpdate.imageTintList = ColorStateList.valueOf(iconColor)
        myProfileImageContainer.setBackgroundResource(currentTheme.blockStyleA)
        updateProfileImagePreview(
            imgMyPhoto,
            imgMyBase,
            imgMyMouth,
            imgMyEyes,
            imgMyAccessory
        )
    }

    override fun onResume() {
        super.onResume()
        if (::imgMyPhoto.isInitialized) {
            updateProfileImagePreview(
                imgMyPhoto,
                imgMyBase,
                imgMyMouth,
                imgMyEyes,
                imgMyAccessory
            )
        }
    }

    private fun showMyProfileDialog() {
        val currentTheme = getCurrentThemeOrNull()
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_social_profile, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = view.findViewById<LinearLayout>(R.id.profileDialogRoot)
        val userBlock = view.findViewById<LinearLayout>(R.id.userBlock)
        val nickname = view.findViewById<TextView>(R.id.nickname)
        val profileImageContainer = view.findViewById<FrameLayout>(R.id.profileImageContainer)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)
        val btnShop = view.findViewById<Button>(R.id.btnShop)
        val btnCustomize = view.findViewById<Button>(R.id.btnCustomize)

        currentTheme?.let { theme ->
            root.background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@SocialActivity, theme.backgroundColor))
                cornerRadius = 24 * resources.displayMetrics.density
            }
            userBlock.setBackgroundResource(theme.blockStyleA)
            profileImageContainer.setBackgroundResource(theme.blockStyleA)
            nickname.setTextColor(ContextCompat.getColor(this, theme.themeTextColorBasic))

            val logoutColor = ContextCompat.getColor(this, theme.themeColorB)
            val buttonTextColor = ContextCompat.getColor(this, theme.themeColorC)
            btnLogout.backgroundTintList = ColorStateList.valueOf(logoutColor)
            btnLogout.setTextColor(buttonTextColor)
            listOf(btnShop, btnCustomize).forEach { button ->
                button.backgroundTintList = null
                button.setBackgroundResource(theme.blockStyleA)
                button.setTextColor(ContextCompat.getColor(this, theme.themeTextColorBasic))
            }
        }

        nickname.text = userStatsPrefs.getNickname()
        updateProfileImagePreview(
            view.findViewById(R.id.imgPhoto),
            view.findViewById(R.id.imgBase),
            view.findViewById(R.id.imgMouth),
            view.findViewById(R.id.imgEyes),
            view.findViewById(R.id.imgAccessory)
        )

        view.findViewById<FrameLayout>(R.id.profileImageContainer).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        btnLogout.setOnClickListener {
            dialog.dismiss()
            logout()
        }
        btnShop.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ShopActivity::class.java))
        }
        btnCustomize.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        dialog.show()
        val horizontalMargin = (24 * resources.displayMetrics.density).toInt()
        dialog.window?.setLayout(
            resources.displayMetrics.widthPixels - horizontalMargin * 2,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun logout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.memoDao().clearAll()
                db.dailyEntryDao().clearAll()
                db.userStyleDao().clearAll()
                db.weekSummaryDao().clearAll()
            }
            SessionManager.clearSession()
            val intent = Intent(this@SocialActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun updateProfileImagePreview(
        imgPhoto: ImageView,
        imgBase: ImageView,
        imgMouth: ImageView,
        imgEyes: ImageView,
        imgAccessory: ImageView
    ) {
        if (ProfileImagePrefs.getMode(this) == "PHOTO") {
            imgPhoto.visibility = View.VISIBLE
            imgBase.visibility = View.GONE
            imgMouth.visibility = View.GONE
            imgEyes.visibility = View.GONE
            imgAccessory.visibility = View.GONE

            val path = ProfileImagePrefs.getPhotoUri(this)
            val bitmap: Bitmap? = path?.let { BitmapFactory.decodeFile(it) }
            if (bitmap != null) {
                imgPhoto.setImageBitmap(bitmap)
            } else {
                imgPhoto.setImageResource(R.drawable.ic_account_circle)
            }
        } else {
            imgPhoto.visibility = View.GONE
            imgBase.visibility = View.VISIBLE
            imgMouth.visibility = View.VISIBLE
            imgEyes.visibility = View.VISIBLE
            imgAccessory.visibility = View.VISIBLE

            val faceId = ProfileImagePrefs.getFaceId(this)
            val eyesId = ProfileImagePrefs.getEyesId(this)
            val mouthId = ProfileImagePrefs.getMouthId(this)
            val accId = ProfileImagePrefs.getAccId(this)
            val items = listOf(
                ProfileImageItem(1, ProfileImageItemType.FACE, R.drawable.nav_fox_button),
                ProfileImageItem(2, ProfileImageItemType.FACE, R.drawable.dailyread_fox_face_level_5),
                ProfileImageItem(3, ProfileImageItemType.FACE, 0),
                ProfileImageItem(4, ProfileImageItemType.EYES, R.drawable.loading_animation),
                ProfileImageItem(5, ProfileImageItemType.EYES, 0)
            )

            imgBase.setImageResource(items.find { it.id == faceId }?.resId ?: R.drawable.nav_fox_button)
            imgBase.setColorFilter("#FFE0BD".toColorInt())
            imgEyes.setImageResource(items.find { it.id == eyesId }?.resId ?: 0)
            imgEyes.setColorFilter(Color.BLACK)
            imgMouth.setImageResource(items.find { it.id == mouthId }?.resId ?: 0)
            imgMouth.setColorFilter(Color.CYAN)
            imgAccessory.setImageResource(items.find { it.id == accId }?.resId ?: 0)
            imgAccessory.setColorFilter(Color.YELLOW)
        }
    }
    private fun setupSearch() {
        etSearchSocial.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchQuery(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SocialUiState.Idle -> {
                        recyclerSocial.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        tvError.visibility = View.GONE
                    }

                    is SocialUiState.Loading -> {
                        recyclerSocial.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        tvError.visibility = View.GONE
                    }

                    is SocialUiState.Success -> {
                        tvError.visibility = View.GONE

                        if (state.friends.isEmpty()) {
                            recyclerSocial.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            recyclerSocial.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                        }
                    }

                    is SocialUiState.Error -> {
                        recyclerSocial.visibility = View.GONE
                        tvEmpty.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.filteredFriends.collect { friends ->
                socialAdapter.updateList(friends)
            }
        }
    }
}

