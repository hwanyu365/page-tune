package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullNavigationTest {

    private fun down(atTop: Boolean = false, atBottom: Boolean = false, top: Boolean = true, bottom: Boolean = true) =
        PullNavigation.onDown(100f, 500f, atTop, atBottom, top, bottom)

    private fun PullState.move(dy: Float, dx: Float = 0f, t: Long = 0) = PullNavigation.onMove(this, 100f + dx, 500f + dy, t)
    private fun PullState.up(t: Long = 300) = PullNavigation.onUp(this, t)

    @Test
    fun `TC#60 맨 위에서 아래로 160dp 당겨 0_3초 멈춘 뒤 떼면 위 당기기`() {
        assertEquals(PullSide.TOP, down(atTop = true).move(160f).up())
    }

    @Test
    fun `TC#60 맨 아래에서 위로 160dp 당겨 0_3초 멈춘 뒤 떼면 아래 당기기`() {
        assertEquals(PullSide.BOTTOM, down(atBottom = true).move(-160f).up())
    }

    @Test
    fun `TC#60 문턱 미만이면 누르지 않는다`() {
        assertNull(down(atTop = true).move(159f).up(1000))
        assertNull(down(atBottom = true).move(-159f).up(1000))
    }

    @Test
    fun `TC#60 문턱을 넘고 0_3초가 되기 전에 떼면 누르지 않는다`() {
        assertNull(down(atBottom = true).move(-300f, t = 0).up(299))
    }

    @Test
    fun `TC#60 문턱 위에서 8dp 이내로만 움직이면 멈춘 것으로 센다`() {
        val s = down(atTop = true).move(100f, t = 0).move(160f, t = 200).move(168f, t = 400)
        assertNull(s.up(499))
        assertEquals(PullSide.TOP, s.up(500))
    }

    @Test
    fun `TC#60 문턱 위에서 계속 당기면 멈춘 지점부터 시간을 새로 센다`() {
        val s = down(atBottom = true).move(-160f, t = 0).move(-200f, t = 250).move(-260f, t = 400)
        assertNull(s.up(500))
        assertNull(s.up(699))
        assertEquals(PullSide.BOTTOM, s.up(700))
    }

    @Test
    fun `TC#60 문턱 아래로 내려갔다 다시 넘으면 시간을 새로 센다`() {
        val s = down(atTop = true).move(170f, t = 0).move(155f, t = 100).move(165f, t = 200)
        assertNull(s.up(450))
        assertEquals(PullSide.TOP, s.up(500))
    }

    @Test
    fun `TC#60 터치 시작 때 끝이 아니면 누르지 않는다`() {
        assertNull(down().move(200f).up())
        assertNull(down().move(-200f).up())
        assertNull(down(atBottom = true).move(200f).up())
        assertNull(down(atTop = true).move(-200f).up())
    }

    @Test
    fun `TC#60 그 쪽 연결이 없으면 누르지 않는다`() {
        assertNull(down(atTop = true, top = false).move(200f).up())
        assertNull(down(atBottom = true, bottom = false).move(-200f).up())
    }

    @Test
    fun `TC#60 8dp 부터 안내를 보이고 160dp 에서 0_3초 뒤 준비다`() {
        val s = down(atTop = true)
        assertFalse(s.move(7f).showing)
        assertTrue(s.move(8f).showing)
        assertFalse(PullNavigation.isReady(s.move(159f), 1000))
        assertFalse(PullNavigation.isReady(s.move(160f, t = 0), 299))
        assertTrue(PullNavigation.isReady(s.move(160f, t = 0), 300))
        assertEquals(PullSide.TOP, s.move(8f).side)
    }

    @Test
    fun `TC#60 문턱을 넘었다 되돌려 떼면 누르지 않는다`() {
        assertNull(down(atTop = true).move(170f).move(100f).up(1000))
    }

    @Test
    fun `TC#60 취소하면 누르지 않는다`() {
        assertNull(PullNavigation.onCancel().up())
    }

    @Test
    fun `TC#60 두 번째 손가락이 닿으면 누르지 않는다`() {
        val s = PullNavigation.onSecondPointer(down(atTop = true).move(10f))
        assertNull(s.move(200f).up())
    }

    @Test
    fun `TC#60 가로가 우세하면 누르지 않는다`() {
        assertNull(down(atTop = true).move(20f, dx = 30f).move(200f).up())
    }

    @Test
    fun `TC#60 짧은 페이지는 당긴 방향대로다`() {
        assertEquals(PullSide.TOP, down(atTop = true, atBottom = true).move(170f).up())
        assertEquals(PullSide.BOTTOM, down(atTop = true, atBottom = true).move(-170f).up())
    }

    @Test
    fun `TC#60 반대로 8dp 넘게 움직였다 되돌아오면 그 터치에서는 누르지 않는다`() {
        assertNull(down(atTop = true).move(-9f).move(200f).up())
        assertNull(down(atBottom = true).move(9f).move(-200f).up())
    }

    @Test
    fun `TC#60 가장 많이 당긴 지점에서 16dp 되돌리면 취소되고 안내가 사라진다`() {
        val s = down(atTop = true).move(200f).move(184f)
        assertFalse(s.showing)
        assertNull(s.up())
        assertNull(down(atBottom = true).move(-200f).move(-184f).up())
    }

    @Test
    fun `TC#60 16dp 미만 되돌림은 유지된다`() {
        assertEquals(PullSide.TOP, down(atTop = true).move(200f).move(185f).up())
    }

    @Test
    fun `TC#60 취소된 터치는 다시 당겨도 누르지 않는다`() {
        assertNull(down(atTop = true).move(200f).move(180f).move(300f).up())
    }
}
