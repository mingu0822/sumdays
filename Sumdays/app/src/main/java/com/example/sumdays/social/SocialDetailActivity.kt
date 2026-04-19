package com.example.sumdays.social

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.R

class SocialDetailActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnManage: ImageButton
    private lateinit var btnOpenDiary: Button

    private lateinit var tvDetailTitle: TextView
    private lateinit var tvProfileEmoji: TextView
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
        tvProfileEmoji = findViewById(R.id.tvProfileEmoji)
        tvTotalDiaryCount = findViewById(R.id.tvTotalDiaryCount)
        tvJoinDate = findViewById(R.id.tvJoinDate)
        tvPublicDiaryCount = findViewById(R.id.tvPublicDiaryCount)
        tvRecentWriteDate = findViewById(R.id.tvRecentWriteDate)
    }

    private fun bindData() {
        val socialName = intent.getStringExtra("social_name") ?: "이름 없음"
        val socialProfileEmoji = intent.getStringExtra("social_profile_emoji") ?: "😊"
        val totalDiaryCount = intent.getIntExtra("total_diary_count", 128)
        val publicDiaryCount = intent.getIntExtra("public_diary_count", 16)
        val joinDate = intent.getStringExtra("join_date") ?: "2025.03.14"
        val recentWriteDate = intent.getStringExtra("recent_write_date") ?: "오늘"

        tvDetailTitle.text = socialName
        tvProfileEmoji.text = socialProfileEmoji
        tvTotalDiaryCount.text = totalDiaryCount.toString()
        tvPublicDiaryCount.text = publicDiaryCount.toString()
        tvJoinDate.text = joinDate
        tvRecentWriteDate.text = recentWriteDate
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