package com.example.sumdays.social

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import android.widget.EditText
import android.text.TextWatcher
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.social.reqeust.AddFriendDialog
import com.example.sumdays.social.reqeust.FriendRequestDialog
import kotlinx.coroutines.launch

class SocialActivity : AppCompatActivity() {
    private lateinit var navBarController: NavBarController
    private lateinit var recyclerSocial: RecyclerView
    private lateinit var etSearchSocial: EditText
    private lateinit var socialAdapter: SocialAdapter

    private lateinit var tvSocialRequests: TextView
    private lateinit var btnAddSocial: ImageButton

    private val allSocialUserList = listOf(
        SocialUser(
            profileEmoji = "😊",
            name = "민수",
            summary = "오늘은 좀 지쳤지만 그래도 버텼어",
            emotion = "😌 평온 / 과제 / 회복"
        ),
        SocialUser(
            profileEmoji = "🌿",
            name = "유진",
            summary = "오랜만에 산책해서 기분이 좋았어",
            emotion = "🌱 여유 / 산책 / 안정"
        ),
        SocialUser(
            profileEmoji = "😄",
            name = "서연",
            summary = "친구 만나서 웃을 일이 많았어",
            emotion = "💬 즐거움 / 친구 / 대화"
        )
    )
    private val filteredSocialUserList = mutableListOf<SocialUser>()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 기본 설정
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social)
        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.PROFILE)


        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        recyclerSocial = findViewById(R.id.recyclerSocial)
        etSearchSocial = findViewById(R.id.etSearchSocial)
        tvSocialRequests = findViewById(R.id.tvSocialRequests)
        btnAddSocial = findViewById(R.id.btnAddSocial)
        filteredSocialUserList.addAll(allSocialUserList)

        tvSocialRequests.setOnClickListener {
            val dialog = FriendRequestDialog()
            dialog.show(supportFragmentManager, "FriendRequestDialog")
        }
        btnAddSocial.setOnClickListener {
            val dialog = AddFriendDialog()
            dialog.show(supportFragmentManager, "AddFriendDialog")
        }


        socialAdapter = SocialAdapter(
            filteredSocialUserList,
            onItemClick = { socialUser ->
                val intent = Intent(this, SocialDetailActivity::class.java)
                intent.putExtra("social_name", socialUser.name)
                intent.putExtra("social_summary", socialUser.summary)
                intent.putExtra("social_emotion", socialUser.emotion)
                startActivity(intent)
            },
            onButtonClick = { socialUser ->
                android.widget.Toast.makeText(
                    this,
                    "${socialUser.name} 버튼 클릭",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )

        recyclerSocial.layoutManager = LinearLayoutManager(this)
        recyclerSocial.adapter = socialAdapter

        btnBack.setOnClickListener {
            finish()
        }

        setupSearch()
    }
    private fun setupSearch() {
        etSearchSocial.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSocialList(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
    }

    private fun filterSocialList(query: String) {
        val keyword = query.trim()

        filteredSocialUserList.clear()

        if (keyword.isEmpty()) {
            filteredSocialUserList.addAll(allSocialUserList)
        } else {
            for (socialUser in allSocialUserList) {
                if (socialUser.name.contains(keyword, ignoreCase = true)) {
                    filteredSocialUserList.add(socialUser)
                }
            }
        }

        socialAdapter.notifyDataSetChanged()
    }
}

