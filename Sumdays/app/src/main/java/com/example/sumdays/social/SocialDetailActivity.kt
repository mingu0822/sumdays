package com.example.sumdays.social

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.sumdays.R
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo

class SocialDetailActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnManage: ImageButton
    private lateinit var btnOpenDiary: Button

    private lateinit var tvDetailTitle: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var tvTotalDiaryCount: TextView
    private lateinit var tvJoinDate: TextView
    private lateinit var tvPublicDiaryCount: TextView
    private lateinit var tvRecentWriteDate: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social_detail)

        initViews()
        bindData()
        setupListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnManage = findViewById(R.id.btnManage)
        btnOpenDiary = findViewById(R.id.btnOpenDiary)

        tvDetailTitle = findViewById(R.id.tvDetailTitle)
        ivProfile = findViewById(R.id.ivProfile)
        tvTotalDiaryCount = findViewById(R.id.tvTotalDiaryCount)
        tvJoinDate = findViewById(R.id.tvJoinDate)
        tvPublicDiaryCount = findViewById(R.id.tvPublicDiaryCount)
        tvRecentWriteDate = findViewById(R.id.tvRecentWriteDate)
    }

    private fun bindData() {
        val friendInfo = intent.getParcelableExtra<FriendInfo>("friendInfo")

        tvDetailTitle.text = friendInfo?.nickname
        tvTotalDiaryCount.text = friendInfo?.countDiaries.toString()
        tvJoinDate.text = friendInfo?.createdAt
        tvPublicDiaryCount.text = friendInfo?.countDiaries.toString()
        tvRecentWriteDate.text = friendInfo?.lastDiaryUpdateDate

        val fullUrl = "${ApiClient.BASE_URL.removeSuffix("/")}${friendInfo?.profileImageUrl}"
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
            val socialName = intent.getStringExtra("social_name") ?: "사용자"

            Toast.makeText(
                this,
                "${socialName}의 공개 일기 화면으로 이동",
                Toast.LENGTH_SHORT
            ).show()

            // 나중에 공개 일기 화면 만들면 여기서 이동
            // val intent = Intent(this, SocialDiaryActivity::class.java)
            // intent.putExtra("social_name", socialName)
            // startActivity(intent)
        }
    }

    private fun showManagePopup(anchorView: android.view.View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_social_user_manage, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_delete_friend -> {
                    val socialName = intent.getStringExtra("social_name") ?: "이 친구"

                    Toast.makeText(
                        this,
                        "${socialName} 삭제 클릭",
                        Toast.LENGTH_SHORT
                    ).show()

                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }
}