package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEndTest {

    @Test
    fun `TC#39 남은 아래 거리 24dp 이하면 맨 아래다`() {
        assertTrue(PageEnd.reached(24f))
        assertTrue(PageEnd.reached(0f))
        assertTrue(PageEnd.reached(-5f))
        assertFalse(PageEnd.reached(25f))
    }

    @Test
    fun `TC#62 2단 버튼 영역 높이는 버튼 수로 정해진다`() {
        assertEquals(0, PageEnd.stackHeightDp(0))
        assertEquals(56, PageEnd.stackHeightDp(1))
        assertEquals(112, PageEnd.stackHeightDp(2))
    }
}
