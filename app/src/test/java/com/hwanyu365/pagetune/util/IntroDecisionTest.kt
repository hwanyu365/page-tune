package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Test

class IntroDecisionTest {

    private val notes = listOf(10200, 10300)

    @Test
    fun `TC#36 기록 없음·데이터 없음 → 시작 안내, 기록은 닫을 때`() {
        assertEquals(IntroDecision(Intro.GettingStarted, null), decideIntro(null, 10300, false, notes))
    }

    @Test
    fun `TC#36 기록 없음·데이터 있음 → 현재 이하 새 소식 전부 최신부터`() {
        assertEquals(IntroDecision(Intro.WhatsNew(listOf(10300, 10200)), null), decideIntro(null, 10300, true, notes))
    }

    @Test
    fun `TC#36 기록 초과 현재 이하만`() {
        assertEquals(IntroDecision(Intro.WhatsNew(listOf(10300)), null), decideIntro(10200, 10300, true, notes))
        assertEquals(IntroDecision(Intro.WhatsNew(listOf(10200)), null), decideIntro(10101, 10201, true, listOf(10200)))
    }

    @Test
    fun `TC#36 사이에 변경 안내가 없으면 없음 + 바로 현재 기록`() {
        assertEquals(IntroDecision(Intro.None, 10201), decideIntro(10200, 10201, true, listOf(10200)))
        assertEquals(IntroDecision(Intro.None, 10300), decideIntro(null, 10300, true, emptyList()))
    }

    @Test
    fun `TC#36 기록이 현재 이상이면 없음, 기록 불변`() {
        assertEquals(IntroDecision(Intro.None, null), decideIntro(10300, 10300, true, notes))
        assertEquals(IntroDecision(Intro.None, null), decideIntro(10400, 10300, false, notes))
    }

    @Test
    fun `TC#36 개발 빌드 현재 1 은 새 소식이 없다`() {
        assertEquals(Intro.None, decideIntro(null, 1, true, notes).show)
    }

}
