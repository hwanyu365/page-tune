package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.R

object TriggerOptions {
    class Option(val type: String, val labelRes: Int)

    /** 드롭다운 순서와 저장되는 트리거 값이 이 목록 하나에서 정해진다. */
    val all = listOf(
        Option("gesture_top", R.string.edit_trigger_double_top),
        Option("gesture_bottom", R.string.edit_trigger_double_bottom),
        Option("gnb_prev", R.string.edit_trigger_button_prev),
        Option("gnb_next", R.string.edit_trigger_button_next),
        Option("pull_top", R.string.edit_trigger_pull_top),
        Option("pull_bottom", R.string.edit_trigger_pull_bottom)
    )

    fun typeAt(index: Int): String? = all.getOrNull(index)?.type

    fun labelResOf(type: String?): Int? = all.firstOrNull { it.type == type }?.labelRes
}
