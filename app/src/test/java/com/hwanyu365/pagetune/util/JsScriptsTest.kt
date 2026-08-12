package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsScriptsTest {

    @Test
    fun `hex 와 CSS 색상 이름을 모두 받는다`() {
        listOf("#000000", "#FFF", "red", "white", "rebeccapurple", "transparent").forEach {
            assertTrue(it, JsScripts.isColorValue(it))
        }
    }

    @Test
    fun `CSS 규칙을 깨뜨릴 수 있는 값은 거부한다`() {
        // 세미콜론이나 중괄호가 통과하면 임의의 CSS 선언을 덧붙일 수 있다.
        listOf("", " ", "red; }", "#12345", "#00ff00aa", "rgb(0,0,0)", "url(x)").forEach {
            assertFalse(it, JsScripts.isColorValue(it))
        }
    }

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
}
