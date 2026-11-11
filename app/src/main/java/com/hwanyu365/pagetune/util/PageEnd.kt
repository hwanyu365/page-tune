package com.hwanyu365.pagetune.util

object PageEnd {
    private const val THRESHOLD_DP = 24f

    /** 문서 높이가 아직 0 이면 남은 거리가 음수로 나와 맨 아래로 보인다 → 그려지기 전에는 맨 아래가 아니다. */
    fun reached(contentHeight: Int, remainingBelowDp: Float): Boolean = contentHeight > 0 && remainingBelowDp <= THRESHOLD_DP
}
