package com.hwanyu365.pagetune.util

object SheetHeight {
    const val MAX_RATIO = 0.8f

    /** 접힌 높이 아래로 내려가면 핸들·취소/저장이 잘리므로 하한으로 둔다. */
    fun max(rootHeight: Int, topBottom: Int, peekHeight: Int): Int =
        maxOf(peekHeight, minOf((rootHeight * MAX_RATIO).toInt(), rootHeight - topBottom))

    data class BottomInset(val padding: Int, val imeExtra: Int)

    /** 시트 내용은 홈 제스처 영역 위에서 끝나야 아래에서 시작한 스크롤이 홈 제스처로 가지 않는다. 키보드는 그보다 높을 때만 더한다. */
    fun bottomInset(systemBars: Int, gestures: Int, ime: Int): BottomInset {
        val base = maxOf(systemBars, gestures)
        val padding = maxOf(base, ime)
        return BottomInset(padding, padding - base)
    }
}
