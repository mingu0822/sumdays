package com.example.sumdays.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding


fun Activity.setupEdgeToEdge(rootView: View) {
    // 1. Edge-to-Edge 모드 활성화 및 시스템 바 색상 투명 설정
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    // 2. 내비게이션 바 오버레이 제거
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // 명암 대비 강제 적용을 해제하여 투명도를 100%로 만듭니다.
        window.isNavigationBarContrastEnforced = false
    }

    // 3. 시스템 아이콘 밝기 설정
    val controller = ViewCompat.getWindowInsetsController(window.decorView)
    if (controller != null) {
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    // 4. 인셋 리스너를 통한 패딩 설정
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

        view.updatePadding(
            top = statusBarHeight,
            bottom = navBarHeight
        )

        insets
    }
}