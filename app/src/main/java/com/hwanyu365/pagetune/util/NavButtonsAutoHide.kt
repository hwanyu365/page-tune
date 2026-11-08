package com.hwanyu365.pagetune.util

import kotlin.math.sign

/** [accumulatedDp] 는 아래 방향이 양수다. */
data class NavButtonsState(val visible: Boolean = true, val touched: Boolean = false, val accumulatedDp: Float = 0f, val atEnd: Boolean = false)

object NavButtonsAutoHide {
    private const val THRESHOLD_DP = 24f

    fun onPageLoaded(): NavButtonsState = NavButtonsState()

    fun onTouch(state: NavButtonsState): NavButtonsState = state.copy(touched = true)

    fun onScroll(state: NavButtonsState, dyDp: Float, remainingBelowDp: Float): NavButtonsState {
        if (remainingBelowDp <= THRESHOLD_DP) return state.copy(visible = true, accumulatedDp = 0f, atEnd = true)
        val notEnd = state.copy(atEnd = false)
        if (!state.touched || dyDp == 0f) return notEnd
        val sum = if (sign(dyDp) == sign(state.accumulatedDp)) state.accumulatedDp + dyDp else dyDp
        return when {
            sum >= THRESHOLD_DP -> notEnd.copy(visible = false, accumulatedDp = 0f)
            sum <= -THRESHOLD_DP -> notEnd.copy(visible = true, accumulatedDp = 0f)
            else -> notEnd.copy(accumulatedDp = sum)
        }
    }

    /** 맨 아래의 폭 전체 버튼 영역 높이. 레이아웃의 버튼 높이와 같아야 아래 당기기 안내가 버튼 위에 놓인다. */
    fun stackHeightDp(buttons: Int): Int = buttons * 56
}
