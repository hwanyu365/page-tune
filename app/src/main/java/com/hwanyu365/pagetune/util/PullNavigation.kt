package com.hwanyu365.pagetune.util

import kotlin.math.abs

enum class PullSide { PREV, NEXT }

data class PullState(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val canPrev: Boolean = false,
    val canNext: Boolean = false,
    val side: PullSide? = null,
    val distance: Float = 0f,
) {
    val showing: Boolean get() = side != null && distance >= PullNavigation.SLOP_DP
    val ready: Boolean get() = side != null && distance >= PullNavigation.THRESHOLD_DP
}

/** 좌표는 dp 다. */
object PullNavigation {
    const val SLOP_DP = 8f
    const val THRESHOLD_DP = 72f

    // 손가락을 댈 때 이미 끝이어야 한다 → 관성으로 끝에 닿은 채 이어지는 당기기는 후보가 없다.
    fun onDown(x: Float, y: Float, atTop: Boolean, atBottom: Boolean, prevEnabled: Boolean, nextEnabled: Boolean) =
        PullState(x, y, canPrev = atTop && prevEnabled, canNext = atBottom && nextEnabled)

    fun onSecondPointer(state: PullState) = PullState(state.startX, state.startY)

    fun onCancel() = PullState()

    fun onMove(state: PullState, x: Float, y: Float): PullState {
        val dx = x - state.startX
        val dy = y - state.startY
        val horizontal = abs(dx) > abs(dy) && abs(dx) > SLOP_DP
        val canPrev = state.canPrev && !horizontal && dy >= -SLOP_DP
        val canNext = state.canNext && !horizontal && dy <= SLOP_DP
        val side = when {
            dy > 0 && canPrev -> PullSide.PREV
            dy < 0 && canNext -> PullSide.NEXT
            else -> null
        }
        return state.copy(canPrev = canPrev, canNext = canNext, side = side, distance = if (side == null) 0f else abs(dy))
    }

    fun onUp(state: PullState): PullSide? = state.side.takeIf { state.ready }
}
