package com.hwanyu365.pagetune.util

object PageEnd {
    private const val THRESHOLD_DP = 24f
    private const val BUTTON_HEIGHT_DP = 56

    fun reached(remainingBelowDp: Float): Boolean = remainingBelowDp <= THRESHOLD_DP

    /** 레이아웃의 버튼 높이와 같아야 아래 당기기 안내가 버튼 위에 놓인다. */
    fun stackHeightDp(buttons: Int): Int = buttons * BUTTON_HEIGHT_DP
}
