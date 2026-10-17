package com.hwanyu365.pagetune.util

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 앱 배경이 항상 어두워서, 시스템이 라이트 모드면 기본값인 어두운 바 아이콘이 배경에 묻힌다. */
fun ComponentActivity.enableDarkEdgeToEdge() {
    val dark = SystemBarStyle.dark(Color.TRANSPARENT)
    enableEdgeToEdge(dark, dark)
}

/** targetSdk 35 부터는 edge-to-edge 가 강제되어 내용이 시스템 바와 키보드 밑에 깔린다. 그 영역만큼 padding 으로 밀어낸다. */
fun View.padForInsets(
    types: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(types)
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
