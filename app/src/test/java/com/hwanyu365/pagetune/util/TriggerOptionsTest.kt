package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TriggerOptionsTest {

    @Test
    fun `TC#12 TC#60 드롭다운 순서와 트리거 값 라벨 매핑이 고정된다`() {
        assertEquals(
            listOf(
                "gesture_top" to R.string.edit_trigger_double_top,
                "gesture_bottom" to R.string.edit_trigger_double_bottom,
                "gnb_prev" to R.string.edit_trigger_button_prev,
                "gnb_next" to R.string.edit_trigger_button_next,
                "pull_top" to R.string.edit_trigger_pull_top,
                "pull_bottom" to R.string.edit_trigger_pull_bottom
            ),
            TriggerOptions.all.map { it.type to it.labelRes }
        )
    }

    @Test
    fun `TC#12 선택 위치에서 트리거 값을 얻고 범위 밖이면 null 이다`() {
        assertEquals("gnb_prev", TriggerOptions.typeAt(2))
        assertNull(TriggerOptions.typeAt(-1))
        assertEquals("pull_top", TriggerOptions.typeAt(4))
        assertNull(TriggerOptions.typeAt(6))
    }

    @Test
    fun `TC#12 트리거 값으로 라벨 리소스를 찾고 모르는 값이면 null 이다`() {
        assertEquals(R.string.edit_trigger_button_next, TriggerOptions.labelResOf("gnb_next"))
        assertNull(TriggerOptions.labelResOf("unknown"))
    }
}
