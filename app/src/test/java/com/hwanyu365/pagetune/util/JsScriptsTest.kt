package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsScriptsTest {

    @Test
    fun `색상 선언마다 important 를 붙인다`() {
        val css = JsScripts.buildColorCss(listOf("#content" to "background-color:#000000;color:#ffffff"))

        assertTrue(css.contains("background-color:#000000 !important"))
        assertTrue(css.contains("color:#ffffff !important"))
    }

    @Test
    fun `하위 노드까지 셀렉터에 포함한다`() {
        // 자식이 자기 색을 갖고 있으면 부모에만 건 규칙은 덮인다.
        val css = JsScripts.buildColorCss(listOf("#content" to "color:#ffffff"))

        assertTrue(css.startsWith("#content, #content * {"))
    }

    @Test
    fun `셀렉터나 선언이 비면 규칙을 만들지 않는다`() {
        val css = JsScripts.buildColorCss(listOf("#a" to "", "" to "color:#fff", " " to " "))

        assertEquals("", css)
    }

    @Test
    fun `규칙이 여러 개면 줄로 나눈다`() {
        val css = JsScripts.buildColorCss(
            listOf("#a" to "color:#fff", ".b" to "background-color:#000")
        )

        assertEquals(2, css.lines().size)
    }

    @Test
    fun `주입 스크립트는 CSS 의 따옴표와 개행을 이스케이프한다`() {
        // 셀렉터는 사이트에서 온 문자열이라 JS 문자열 리터럴을 깨뜨릴 수 있다.
        val script = JsScripts.getColorStyleScript("a[x='y'] {\n color:#fff; }")

        assertTrue(script.contains("""a[x=\'y\']"""))
        assertFalse(script.contains("{\n color"))
    }

    @Test
    fun `스크롤 스크립트는 저장된 y 로 이동한다`() {
        val script = JsScripts.getScrollScript(1234)

        assertTrue(script.contains("window.scrollTo(0, 1234)"))
    }

    @Test
    fun `스크롤 스크립트는 도달하지 못하면 재시도한다`() {
        // 페이지가 늦게 자라면 첫 scrollTo 는 문서 끝에서 잘려 목표에 못 간다.
        val script = JsScripts.getScrollScript(1234)

        assertTrue(script.contains("setTimeout"))
    }

    @Test
    fun `스크롤 스크립트는 사용자가 화면을 만지면 멈춘다`() {
        // 재시도가 사용자의 스크롤을 계속 되돌리면 페이지를 쓸 수 없다.
        val script = JsScripts.getScrollScript(1234)

        assertTrue(script.contains("touchstart"))
    }
}
