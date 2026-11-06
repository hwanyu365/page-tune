package com.hwanyu365.pagetune.util

object SheetHeight {
    const val MAX_RATIO = 0.8f

    /** 접힌 높이 아래로 내려가면 핸들·취소/저장이 잘리므로 하한으로 둔다. */
    fun max(rootHeight: Int, topBottom: Int, peekHeight: Int): Int =
        maxOf(peekHeight, minOf((rootHeight * MAX_RATIO).toInt(), rootHeight - topBottom))
}
