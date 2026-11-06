package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetHeightTest {

    @Test
    fun `TC#12 최대 높이는 화면의 80 퍼센트다`() {
        assertEquals(1920, SheetHeight.max(rootHeight = 2400, topBottom = 100, peekHeight = 500))
    }

    @Test
    fun `TC#12 이름·주소 영역 아래까지만 올라온다`() {
        assertEquals(1900, SheetHeight.max(rootHeight = 2400, topBottom = 500, peekHeight = 500))
    }

    @Test
    fun `TC#12 남는 높이가 접힌 높이보다 작아도 접힌 높이 이상이다`() {
        assertEquals(500, SheetHeight.max(rootHeight = 600, topBottom = 400, peekHeight = 500))
    }

    @Test
    fun `TC#12 상단 영역이 화면보다 커 남는 높이가 음수여도 접힌 높이 이상이다`() {
        assertEquals(500, SheetHeight.max(rootHeight = 600, topBottom = 900, peekHeight = 500))
    }
}
