package com.example.sumdays.social

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R

class SocialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val recyclerSocial = findViewById<RecyclerView>(R.id.recyclerSocial)

        val socialUserList = listOf(
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

        recyclerSocial.layoutManager = LinearLayoutManager(this)
        recyclerSocial.adapter = SocialAdapter(socialUserList){ socialUser ->
            android.widget.Toast.makeText(
                this,
                "${socialUser.name} 클릭됨",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}