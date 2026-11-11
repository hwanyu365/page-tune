package com.hwanyu365.pagetune.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestGenerationTest {

    @Test
    fun `TC#53 마지막 요청의 결과만 최신이다`() {
        val gen = RequestGeneration()
        val a = gen.next()
        val b = gen.next()

        assertFalse(gen.isCurrent(a))
        assertTrue(gen.isCurrent(b))
    }

    @Test
    fun `TC#53 요청 뒤 해제하면 그 요청의 결과는 무시된다`() {
        val gen = RequestGeneration()
        val a = gen.next()
        gen.invalidate()

        assertFalse(gen.isCurrent(a))
    }

    @Test
    fun `TC#53 결과가 오기 전 다른 요청이 없으면 최신이다`() {
        val gen = RequestGeneration()
        val a = gen.next()

        assertTrue(gen.isCurrent(a))
        assertTrue(gen.isCurrent(a))
    }

    @Test
    fun `TC#53 편집 화면은 강조 결과를 최신 요청일 때만 반영하고, 해제·페이지 로드 시작에서 대기 중인 결과를 무효화한다`() {
        val edit = File("src/main/java").walk().single { it.name == "PluginEditActivity.kt" }.readText()

        assertEquals(1, Regex("""getHighlightScript\(.*\) \{ result ->\s*if \(!highlightRequests\.isCurrent\(token\)\) return@evaluateJavascript""").findAll(edit).count())
        assertEquals(2, Regex("""highlightRequests\.invalidate\(\)""").findAll(edit).count())
    }
}
