package com.example.sumdays.social

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import android.widget.EditText
import android.text.TextWatcher
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
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

    private val allFriendList = mutableListOf<FriendInfo>()
    private val filteredFriendList = mutableListOf<FriendInfo>()

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

        tvSocialRequests.setOnClickListener {
            val dialog = FriendRequestDialog()
            dialog.show(supportFragmentManager, "FriendRequestDialog")
        }
        btnAddSocial.setOnClickListener {
            val dialog = AddFriendDialog()
            dialog.show(supportFragmentManager, "AddFriendDialog")
        }


        socialAdapter = SocialAdapter(
            filteredFriendList,
            onItemClick = { friendInfo ->
                val intent = Intent(this, SocialDetailActivity::class.java)
                intent.putExtra("friendInfo", friendInfo)
                startActivity(intent)
            },
            onButtonClick = { friendInfo ->
                android.widget.Toast.makeText(
                    this,
                    "${friendInfo.nickname} 버튼 클릭",
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
    override fun onResume() {
        super.onResume()
        loadFriendsFromServer()
    }
    private fun loadFriendsFromServer() {
        lifecycleScope.launch {
            try {
                // [핵심] ApiClient를 통해 서버의 친구 목록 API 호출
                val response = ApiClient.socialApi.getMyFriends()

                if (response.success) {
                    val friends = response.friends
                    allFriendList.clear()
                    allFriendList.addAll(friends)

                    // 현재 검색어에 맞춰 필터링 및 UI 갱신
                    filterSocialList(etSearchSocial.text.toString())

                    Log.d("SOCIAL_DEBUG", "친구 로드 성공: ${friends.size}명")
                }
            } catch (e: Exception) {
                Log.e("SOCIAL_DEBUG", "네트워크 에러", e)
            }
        }
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

        filteredFriendList.clear()

        if (keyword.isEmpty()) {
            filteredFriendList.addAll(allFriendList)
        } else {
            for (friend in allFriendList) {
                if (friend.nickname.contains(keyword, ignoreCase = true)) {
                    filteredFriendList.add(friend)
                }
            }
        }
        socialAdapter.notifyDataSetChanged()
    }
}

