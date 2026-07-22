package com.example.sumdays

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class FoxAlchemyActivity : AppCompatActivity() {

    private lateinit var alchemyPot: ImageButton

    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fox_alchemy)

        alchemyPot = findViewById(R.id.alchemy_pot)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        startPotAnimation()
    }

    private fun startPotAnimation() {
        val animator = ObjectAnimator.ofFloat(
            alchemyPot,
            "translationY",
            -20f,   // 위로 20px
            20f     // 아래로 20px
        )

        animator.duration = 1200L                // 왕복 시간
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }
}