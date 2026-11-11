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

    @Test
    fun `TC#59 하단 여백은 내비게이션 바와 홈 제스처 영역 중 큰 쪽이다`() {
        assertEquals(SheetHeight.BottomInset(64, 0), SheetHeight.bottomInset(systemBars = 48, gestures = 64, ime = 0))
        assertEquals(SheetHeight.BottomInset(48, 0), SheetHeight.bottomInset(systemBars = 48, gestures = 0, ime = 0))
    }

    @Test
    fun `TC#59 키보드가 더 높으면 키보드 높이이고 그 초과분만 WebView 여백에서 뺀다`() {
        assertEquals(SheetHeight.BottomInset(800, 736), SheetHeight.bottomInset(systemBars = 48, gestures = 64, ime = 800))
        assertEquals(SheetHeight.BottomInset(64, 0), SheetHeight.bottomInset(systemBars = 48, gestures = 64, ime = 60))
    }
}
