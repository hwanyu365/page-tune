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

    /** 맨 아래의 폭 전체 버튼 영역: 아래 여백 16 + 버튼 56 씩 + 버튼 사이 8. 레이아웃 값과 같아야 페이지 끝 여백이 버튼에 맞는다. */
    fun stackHeightDp(buttons: Int): Int = if (buttons == 0) 0 else 16 + buttons * 56 + (buttons - 1) * 8
}
