package com.example.sumdays.social

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.R

class SocialDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social_detail)

        val tvDetailName = findViewById<TextView>(R.id.tvDetailName)
        val tvDetailSummary = findViewById<TextView>(R.id.tvDetailSummary)
        val tvDetailEmotion = findViewById<TextView>(R.id.tvDetailEmotion)

        val name = intent.getStringExtra("social_name") ?: "이름 없음"
        val summary = intent.getStringExtra("social_summary") ?: "요약 없음"
        val emotion = intent.getStringExtra("social_emotion") ?: "감정 없음"

        tvDetailName.text = name
        tvDetailSummary.text = summary
        tvDetailEmotion.text = emotion
    }
}