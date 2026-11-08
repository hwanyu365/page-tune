package com.hwanyu365.pagetune.util

import kotlin.math.abs
import kotlin.math.max

enum class PullSide { TOP, BOTTOM }

data class PullState(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val canTop: Boolean = false,
    val canBottom: Boolean = false,
    val side: PullSide? = null,
    val distance: Float = 0f,
    val peak: Float = 0f,
) {
    val showing: Boolean get() = side != null && distance >= PullNavigation.SLOP_DP
    val ready: Boolean get() = side != null && distance >= PullNavigation.THRESHOLD_DP
}

/** 좌표는 dp 다. */
object PullNavigation {
    const val SLOP_DP = 8f
    const val THRESHOLD_DP = 72f
    const val CANCEL_DP = 16f

    // 손가락을 댈 때 이미 끝이어야 한다 → 관성으로 끝에 닿은 채 이어지는 당기기는 후보가 없다.
    fun onDown(x: Float, y: Float, atTop: Boolean, atBottom: Boolean, topEnabled: Boolean, bottomEnabled: Boolean) =
        PullState(x, y, canTop = atTop && topEnabled, canBottom = atBottom && bottomEnabled)

    fun onSecondPointer(state: PullState) = PullState(state.startX, state.startY)

    fun onCancel() = PullState()

    fun onMove(state: PullState, x: Float, y: Float): PullState {
        val dx = x - state.startX
        val dy = y - state.startY
        val horizontal = abs(dx) > abs(dy) && abs(dx) > SLOP_DP
        val side = when {
            dy > 0 && state.canTop -> PullSide.TOP
            dy < 0 && state.canBottom -> PullSide.BOTTOM
            else -> null
        }
        val distance = if (side == null) 0f else abs(dy)
        // 문턱 아래로 되돌릴 때까지 기다리지 않고 조금만 되돌려도 취소한다 → 많이 당긴 뒤에도 취소가 쉽다.
        val reversed = state.peak - distance >= CANCEL_DP
        if (horizontal || reversed) return PullState(state.startX, state.startY)
        return state.copy(
            canTop = state.canTop && dy >= -SLOP_DP,
            canBottom = state.canBottom && dy <= SLOP_DP,
            side = side,
            distance = distance,
            peak = max(state.peak, distance),
        )
    }

    fun onUp(state: PullState): PullSide? = state.side.takeIf { state.ready }
}
