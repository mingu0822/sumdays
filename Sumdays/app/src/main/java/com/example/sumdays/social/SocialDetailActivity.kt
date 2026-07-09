package com.example.sumdays.social

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.sumdays.R
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
import com.example.sumdays.social.diary.SocialCalendarActivity
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.getErrorMessage
import kotlinx.coroutines.launch

class SocialDetailActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnManage: ImageButton
    private lateinit var btnOpenDiary: Button

    private lateinit var tvDetailTitle: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var cardTotalDiary: LinearLayout
    private lateinit var cardJoinDate: LinearLayout
    private lateinit var cardPublicDiary: LinearLayout
    private lateinit var cardRecentWrite: LinearLayout
    private lateinit var tvTotalDiaryCount: TextView
    private lateinit var tvJoinDate: TextView
    private lateinit var tvPublicDiaryCount: TextView
    private lateinit var tvRecentWriteDate: TextView
    private var friendInfo: FriendInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social_detail)

        friendInfo = intent.getParcelableExtra("friendInfo")
        initViews()
        applyThemeModeSettings()
        bindData()
        setupListeners()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.socialDetailRoot)
        btnBack = findViewById(R.id.btnBack)
        btnManage = findViewById(R.id.btnManage)
        btnOpenDiary = findViewById(R.id.btnOpenDiary)

        tvDetailTitle = findViewById(R.id.tvDetailTitle)
        ivProfile = findViewById(R.id.ivProfile)
        cardTotalDiary = findViewById(R.id.cardTotalDiary)
        cardJoinDate = findViewById(R.id.cardJoinDate)
        cardPublicDiary = findViewById(R.id.cardPublicDiary)
        cardRecentWrite = findViewById(R.id.cardRecentWrite)
        tvTotalDiaryCount = findViewById(R.id.tvTotalDiaryCount)
        tvJoinDate = findViewById(R.id.tvJoinDate)
        tvPublicDiaryCount = findViewById(R.id.tvPublicDiaryCount)
        tvRecentWriteDate = findViewById(R.id.tvRecentWriteDate)
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
        val blockPointColor = ContextCompat.getColor(this, currentTheme.themeColorA)
        val buttonTextColor = ContextCompat.getColor(this, currentTheme.themeColorC)
        val strongPointColor = ContextCompat.getColor(this, currentTheme.themeColorD)

        rootLayout.setBackgroundResource(currentTheme.backgroundColor)
        ivProfile.setBackgroundResource(currentTheme.blockStyleA)
        listOf(cardTotalDiary, cardJoinDate, cardPublicDiary, cardRecentWrite).forEach {
            it.setBackgroundResource(currentTheme.blockStyleA)
        }

        tvDetailTitle.setTextColor(basicTextColor)
        tvTotalDiaryCount.setTextColor(strongPointColor)
        tvJoinDate.setTextColor(strongPointColor)
        tvPublicDiaryCount.setTextColor(strongPointColor)
        tvRecentWriteDate.setTextColor(strongPointColor)
        btnBack.imageTintList = ColorStateList.valueOf(strongPointColor)
        btnManage.imageTintList = ColorStateList.valueOf(strongPointColor)
        btnOpenDiary.backgroundTintList = ColorStateList.valueOf(blockPointColor)
        btnOpenDiary.setTextColor(buttonTextColor)
    }

    private fun bindData() {
        val friend = friendInfo ?: return

        tvDetailTitle.text = friend.nickname
        tvTotalDiaryCount.text = friend.countDiaries.toString()
        tvJoinDate.text = friend.createdAt
        tvPublicDiaryCount.text = friend.countDiaries.toString()
        tvRecentWriteDate.text = friend.lastDiaryUpdateDate

        val fullUrl = "${ApiClient.BASE_URL.removeSuffix("/")}${friend.profileImageUrl}"
        Glide.with(this)
            .load(fullUrl)
            .placeholder(R.drawable.loading_animation) // 로딩 중에 보여줄 이미지
            .error(R.drawable.ic_account_circle)             // 로드 실패 시 보여줄 이미지
            .circleCrop()                                   // 사진을 동그랗게 깎아줌! (꿀팁)
            .transition(DrawableTransitionOptions.withCrossFade()) // 부드럽게 나타나게
            .into(ivProfile) // ImageView에 꽂아넣기

    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnManage.setOnClickListener { view ->
            showManagePopup(view)
        }

        btnOpenDiary.setOnClickListener {
            val nickname = friendInfo?.nickname
            val friendId = friendInfo?.id
            val intent = Intent(this, SocialCalendarActivity::class.java)
            intent.putExtra("nickname", nickname)
            intent.putExtra("friendId", friendId)
            startActivity(intent)
        }
    }

    private fun showManagePopup(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_social_user_manage, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_delete_friend -> {
                    val friend = friendInfo ?: return@setOnMenuItemClickListener false
                    showDeleteConfirmDialog(friend)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }
    private fun showDeleteConfirmDialog(friend: FriendInfo) {
        AlertDialog.Builder(this)
            .setTitle("친구 삭제")
            .setMessage("${friend.nickname}을(를) 정말 삭제하시겠습니까?")
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("삭제") { dialog, _ ->
                deleteFriend(friend.id)
                dialog.dismiss()
            }
            .show()
    }
    private fun deleteFriend(friendId: Int) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.socialApi.deleteFriend(friendId)


                if (response.isSuccessful) {
                    val body = response.body()
                    Toast.makeText(
                        this@SocialDetailActivity,
                        body?.message,
                        Toast.LENGTH_SHORT
                    ).show()

                    val resultIntent = Intent().apply {
                        putExtra("deletedFriendId", friendId)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()

                } else {
                    val errorMessage = response.getErrorMessage("서버 요청에 실패하였습니다.")
                    Toast.makeText(
                        this@SocialDetailActivity,
                        errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    this@SocialDetailActivity,
                    "네트워크 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
