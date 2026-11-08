package com.hwanyu365.pagetune.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavButtonsAutoHideTest {

    private val far = 1000f

    private fun NavButtonsState.scroll(dy: Float, remaining: Float = far) = NavButtonsAutoHide.onScroll(this, dy, remaining)
    private fun touched() = NavButtonsAutoHide.onTouch(NavButtonsState())

    @Test
    fun `TC#39 터치 없이 아래로 스크롤하면 보이는 채로 남는다`() {
        assertTrue(NavButtonsState().scroll(100f).visible)
    }

    @Test
    fun `TC#39 터치 후 아래 24dp 부터 숨긴다`() {
        assertTrue(touched().scroll(23f).visible)
        assertFalse(touched().scroll(24f).visible)
        assertFalse(touched().scroll(10f).scroll(14f).visible)
    }

    @Test
    fun `TC#39 숨긴 뒤 위로 24dp 면 다시 보인다`() {
        val hidden = touched().scroll(30f)
        assertFalse(hidden.scroll(-23f).visible)
        assertTrue(hidden.scroll(-24f).visible)
    }

    @Test
    fun `TC#39 방향이 바뀌면 누적을 다시 센다`() {
        assertTrue(touched().scroll(20f).scroll(-10f).scroll(20f).visible)
        assertFalse(touched().scroll(20f).scroll(-10f).scroll(20f).scroll(4f).visible)
    }

    @Test
    fun `TC#39 페이지 끝이면 보인다`() {
        val hidden = touched().scroll(30f)
        assertTrue(hidden.scroll(0f, remaining = 24f).visible)
        assertTrue(hidden.scroll(5f, remaining = 0f).visible)
    }

    @Test
    fun `TC#39 로드 완료는 보이고 터치 기록을 지운다`() {
        touched().scroll(30f)
        val loaded = NavButtonsAutoHide.onPageLoaded()
        assertTrue(loaded.visible)
        assertTrue(loaded.scroll(100f).visible)
    }

    @Test
    fun `TC#39 끝 행이 숨김 행보다 앞선다`() {
        assertTrue(touched().scroll(30f, remaining = 10f).visible)
    }
}
