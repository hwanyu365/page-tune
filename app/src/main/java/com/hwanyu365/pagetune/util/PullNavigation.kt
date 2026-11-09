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
    val armedAtMs: Long? = null,
    val armedAtDistance: Float = 0f,
) {
    val showing: Boolean get() = side != null && distance >= PullNavigation.SLOP_DP
}

/** 좌표는 dp, 시각은 ms 다. */
object PullNavigation {
    const val SLOP_DP = 8f
    const val THRESHOLD_DP = 160f
    const val CANCEL_DP = 16f
    const val HOLD_MS = 300L

    // 손가락을 댈 때 이미 끝이어야 한다 → 관성으로 끝에 닿은 채 이어지는 당기기는 후보가 없다.
    fun onDown(x: Float, y: Float, atTop: Boolean, atBottom: Boolean, topEnabled: Boolean, bottomEnabled: Boolean) =
        PullState(x, y, canTop = atTop && topEnabled, canBottom = atBottom && bottomEnabled)

    fun onSecondPointer(state: PullState) = PullState(state.startX, state.startY)

    fun onCancel() = PullState()

    fun onMove(state: PullState, x: Float, y: Float, nowMs: Long): PullState {
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
        val next = state.copy(
            canTop = state.canTop && dy >= -SLOP_DP,
            canBottom = state.canBottom && dy <= SLOP_DP,
            side = side,
            distance = distance,
            peak = max(state.peak, distance),
        )
        // 문턱 위에서도 8dp 넘게 움직이면 멈춘 것이 아니다 → 멈춘 지점에서 시간을 다시 센다.
        val still = state.armedAtMs != null && abs(distance - state.armedAtDistance) <= SLOP_DP
        return when {
            distance < THRESHOLD_DP -> next.copy(armedAtMs = null)
            still -> next
            else -> next.copy(armedAtMs = nowMs, armedAtDistance = distance)
        }
    }

    // 문턱을 넘은 채 0.3초 멈춰야 한다 → 끝에서 습관적으로 한 번 더 쓸어 올리는 스크롤은 멈춤 없이 지나가 이동하지 않는다.
    fun isReady(state: PullState, nowMs: Long): Boolean =
        state.side != null && state.armedAtMs != null && nowMs - state.armedAtMs >= HOLD_MS

    fun onUp(state: PullState, nowMs: Long): PullSide? = state.side.takeIf { isReady(state, nowMs) }
}
