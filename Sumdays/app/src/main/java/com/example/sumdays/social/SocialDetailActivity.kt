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

        val name = intent.getStringExtra("social_name")

        tvDetailName.text = name
    }
}