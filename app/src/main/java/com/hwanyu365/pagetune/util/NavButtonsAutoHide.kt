package com.hwanyu365.pagetune.util

import kotlin.math.sign

/** [accumulatedDp] 는 아래 방향이 양수다. */
data class NavButtonsState(val visible: Boolean = true, val touched: Boolean = false, val accumulatedDp: Float = 0f)

object NavButtonsAutoHide {
    private const val THRESHOLD_DP = 24f

    fun onPageLoaded(): NavButtonsState = NavButtonsState()

    fun onTouch(state: NavButtonsState): NavButtonsState = state.copy(touched = true)

    fun onScroll(state: NavButtonsState, dyDp: Float, remainingBelowDp: Float): NavButtonsState {
        if (remainingBelowDp <= THRESHOLD_DP) return state.copy(visible = true, accumulatedDp = 0f)
        if (!state.touched || dyDp == 0f) return state
        val sum = if (sign(dyDp) == sign(state.accumulatedDp)) state.accumulatedDp + dyDp else dyDp
        return when {
            sum >= THRESHOLD_DP -> state.copy(visible = false, accumulatedDp = 0f)
            sum <= -THRESHOLD_DP -> state.copy(visible = true, accumulatedDp = 0f)
            else -> state.copy(accumulatedDp = sum)
        }
    }
}
