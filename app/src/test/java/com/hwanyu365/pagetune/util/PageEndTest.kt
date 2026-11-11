package com.hwanyu365.pagetune.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEndTest {

    @Test
    fun `TC#139 남은 아래 거리 24dp 이하면 맨 아래다`() {
        assertTrue(PageEnd.reached(1000, 24f))
        assertTrue(PageEnd.reached(1000, 0f))
        assertTrue(PageEnd.reached(1000, -5f))
        assertFalse(PageEnd.reached(1000, 25f))
    }

    @Test
    fun `TC#139 문서 높이가 0 이면 맨 아래가 아니다`() {
        assertFalse(PageEnd.reached(0, -2000f))
    }
}
