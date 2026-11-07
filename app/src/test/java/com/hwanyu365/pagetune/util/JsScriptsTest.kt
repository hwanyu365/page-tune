package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsScriptsTest {

    @Test
    fun `TC#9 색상 선언마다 important 를 붙인다`() {
        val css = JsScripts.buildColorCss(listOf("#content" to "background-color:#000000;color:#ffffff"))

        assertTrue(css.contains("background-color:#000000 !important"))
        assertTrue(css.contains("color:#ffffff !important"))
    }

    @Test
    fun `TC#9 하위 노드까지 셀렉터에 포함한다`() {
        // 자식이 자기 색을 갖고 있으면 부모에만 건 규칙은 덮인다.
        val css = JsScripts.buildColorCss(listOf("#content" to "color:#ffffff"))

        assertTrue(css.startsWith("#content, #content * {"))
    }

    @Test
    fun `TC#9 셀렉터나 선언이 비면 규칙을 만들지 않는다`() {
        val css = JsScripts.buildColorCss(listOf("#a" to "", "" to "color:#fff", " " to " "))

        assertEquals("", css)
    }

    @Test
    fun `TC#9 규칙이 여러 개면 줄로 나눈다`() {
        val css = JsScripts.buildColorCss(
            listOf("#a" to "color:#fff", ".b" to "background-color:#000")
        )

        assertEquals(2, css.lines().size)
    }

    @Test
    fun `TC#9 주입 스크립트는 CSS 의 따옴표와 개행을 이스케이프한다`() {
        // 셀렉터는 사이트에서 온 문자열이라 JS 문자열 리터럴을 깨뜨릴 수 있다.
        val script = JsScripts.getColorStyleScript("a[x='y'] {\n color:#fff; }")

        assertTrue(script.contains("""a[x=\'y\']"""))
        assertFalse(script.contains("{\n color"))
    }

    @Test
    fun `TC#9 스크롤 스크립트는 저장된 y 로 이동한다`() {
        val script = JsScripts.getScrollScript(1234)

        assertTrue(script.contains("window.scrollTo(0, 1234)"))
    }

    @Test
    fun `TC#9 스크롤 스크립트는 도달하지 못하면 재시도한다`() {
        // 페이지가 늦게 자라면 첫 scrollTo 는 문서 끝에서 잘려 목표에 못 간다.
        val script = JsScripts.getScrollScript(1234)

        assertTrue(script.contains("setTimeout"))
    }

    @Test
    fun `TC#9 스크롤 스크립트는 사용자가 화면을 만지면 멈춘다`() {
        // 재시도가 사용자의 스크롤을 계속 되돌리면 페이지를 쓸 수 없다.
        val script = JsScripts.getScrollScript(1234)

        assertTrue(script.contains("touchstart"))
    }

    @Test
    fun `TC#30 E1 역슬래시는 두 개로 바꾼다`() {
        assertEquals("""'a\\b'""", JsScripts.jsString("""a\b"""))
    }

    @Test
    fun `TC#30 E2 작은따옴표는 이스케이프한다`() {
        assertEquals("""'a\'b'""", JsScripts.jsString("a'b"))
    }

    @Test
    fun `TC#30 E3 큰따옴표는 이스케이프한다`() {
        assertEquals("""'a\"b'""", JsScripts.jsString("a\"b"))
    }

    @Test
    fun `TC#30 E4 LF 와 CR 은 이스케이프 문자로 바꾼다`() {
        assertEquals("""'a\nb\rc'""", JsScripts.jsString("a\nb\rc"))
    }

    @Test
    fun `TC#30 E5 U+2028 과 U+2029 는 유니코드 이스케이프로 바꾼다`() {
        assertEquals("""'a\u2028b\u2029c'""", JsScripts.jsString("a\u2028b\u2029c"))
    }

    @Test
    fun `TC#30 E6 꺾쇠 여는 괄호는 유니코드 이스케이프로 바꾼다`() {
        assertEquals("""'\u003C/script>'""", JsScripts.jsString("</script>"))
    }

    @Test
    fun `TC#30 E7 그 밖의 제어 문자는 유니코드 이스케이프로 바꾼다`() {
        assertEquals("""'a\u0000b\u0009c\u001Fd'""", JsScripts.jsString("a\u0000b\tc\u001Fd"))
    }

    @Test
    fun `TC#30 결합 입력의 출력에 이스케이프되지 않은 따옴표, 줄 종결자, 꺾쇠가 없다`() {
        listOf("');alert(1);//", "</script><script>x()</script>", "\\'", "\\\n'\u2028<").forEach { input ->
            val body = JsScripts.jsString(input).removeSurrounding("'")
            var i = 0
            while (i < body.length) {
                val c = body[i]
                if (c == '\\') {
                    i += 2
                    continue
                }
                assertFalse("$input → $body", c in "'\"<\n\r\u2028\u2029")
                i++
            }
        }
    }

    @Test
    fun `TC#34 C1 중괄호를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div.a} body {"))
        assertFalse(JsScripts.isCssSafeSelector("div{"))
    }

    @Test
    fun `TC#34 C2 세미콜론과 at 기호를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div;x"))
        assertFalse(JsScripts.isCssSafeSelector("div.a@b"))
    }

    @Test
    fun `TC#34 C3 따옴표를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div.a\"b"))
        assertFalse(JsScripts.isCssSafeSelector("div.a'b"))
    }

    @Test
    fun `TC#34 C4 주석 기호를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div/*x"))
        assertFalse(JsScripts.isCssSafeSelector("div*/x"))
    }

    @Test
    fun `TC#34 C5 nth-of-type 숫자 괄호가 아닌 괄호를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div:not(.a)"))
        assertFalse(JsScripts.isCssSafeSelector("div.a)"))
        assertFalse(JsScripts.isCssSafeSelector("div:nth-of-type(2n)"))
    }

    @Test
    fun `TC#34 C6 대괄호를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div[x]"))
        assertFalse(JsScripts.isCssSafeSelector("div]"))
    }

    @Test
    fun `TC#34 C7 끝의 역슬래시를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div.a\\"))
        assertFalse(JsScripts.isCssSafeSelector("div.a\\ "))
    }

    @Test
    fun `TC#34 C8 꺾쇠, 개행, 제어 문자를 거부한다`() {
        assertFalse(JsScripts.isCssSafeSelector("div<x"))
        assertFalse(JsScripts.isCssSafeSelector("div\n.a"))
        assertFalse(JsScripts.isCssSafeSelector("div\u0001.a"))
    }

    @Test
    fun `TC#34 선택 스크립트가 이스케이프한 셀렉터는 통과한다`() {
        assertTrue(JsScripts.isCssSafeSelector("#\\31 abc"))
        assertTrue(JsScripts.isCssSafeSelector(".a\\:b > div:nth-of-type(2)"))
        assertTrue(JsScripts.isCssSafeSelector("html > body > div.ad"))
    }

    private fun info(selector: String, id: String = "", tag: String = "div", attrs: Map<String, String> = emptyMap()) =
        JSONObject().put("selector", selector).put("tagName", tag).put("textContent", "").put("id", id)
            .put("attributes", JSONObject(attrs)).toString()

    private fun color(details: String) =
        PluginAction(pluginId = 1, actionType = "color", targetSelector = details, styleCss = "color:#ffffff")

    @Test
    fun `TC#34 거부되는 셀렉터는 출력에 그 셀렉터와 supports 없이 대체 규칙만 남는다`() {
        val script = JsScripts.getColorScriptFor(listOf(color(info("div.a} body { color: red", attrs = mapOf("role" to "banner")))))

        assertFalse(script.contains("a}"))
        assertFalse(script.contains("@supports"))
        assertEquals(JsScripts.getColorStyleScript("""div[role="banner"], div[role="banner"] * { color:#ffffff !important; }"""), script)
    }

    @Test
    fun `TC#22 불안정 id 와 구조 경로가 저장된 규칙은 구조 경로로 CSS 를 만든다`() {
        val path = "#main > div:nth-of-type(2)"
        val script = JsScripts.getColorScriptFor(listOf(color(info(path, id = "div-gpt-ad-1712345678-0"))))

        assertEquals(JsScripts.getColorStyleScript(JsScripts.buildColorCss(listOf(path to "color:#ffffff"))), script)
    }

    @Test
    fun `TC#22 id 가 있어도 저장된 셀렉터를 그대로 쓴다`() {
        val script = JsScripts.getColorScriptFor(listOf(color(info("#a", id = "a"))))

        assertEquals(JsScripts.getColorStyleScript(JsScripts.buildColorCss(listOf("#a" to "color:#ffffff"))), script)
    }

    @Test
    fun `TC#22 대체 블록은 저장 속성이 있으면 selector 지원과 원래 셀렉터 무효를 함께 요구하는 supports 블록이고 없으면 빈 문자열이다`() {
        assertEquals(
            """@supports selector(*) and (not selector(div.x)) { div[role="banner"], div[role="banner"] * { color:#ffffff !important; } }""",
            JsScripts.buildColorFallbackCss("div.x", """div[role="banner"]""", "color:#ffffff"),
        )
        assertEquals("", JsScripts.buildColorFallbackCss("div.x", null, "color:#ffffff"))
    }

    @Test
    fun `TC#22 CSS 계약을 통과한 셀렉터는 원래 규칙 뒤에 대체 블록을 붙인다`() {
        val sel = "#main > div.ad"
        val script = JsScripts.getColorScriptFor(listOf(color(info(sel, attrs = mapOf("role" to "banner")))))
        val expectedCss = JsScripts.buildColorCss(listOf(sel to "color:#ffffff")) + "\n" +
            JsScripts.buildColorFallbackCss(sel, """div[role="banner"]""", "color:#ffffff")

        assertEquals(JsScripts.getColorStyleScript(expectedCss), script)
    }

    @Test
    fun `TC#22 요소 정보가 JSON 이 아닌 규칙만 제외한다`() {
        val script = JsScripts.getColorScriptFor(listOf(color("not json"), color(info("#a"))))

        assertEquals(JsScripts.getColorStyleScript(JsScripts.buildColorCss(listOf("#a" to "color:#ffffff"))), script)
    }

    @Test
    fun `TC#29 CSS 문자열은 따옴표, 역슬래시, 개행을 이스케이프한다`() {
        assertEquals("\"\"", JsScripts.cssString(""))
        assertEquals(""""a\"b\\c\a d"""", JsScripts.cssString("a\"b\\c\nd"))
    }

    @Test
    fun `TC#29 CSS 식별자는 at 기호와 숫자 시작을 이스케이프한다`() {
        assertEquals("""\@click""", JsScripts.cssIdent("@click"))
        assertEquals("""\31 abc""", JsScripts.cssIdent("1abc"))
        assertEquals("""-\31 a""", JsScripts.cssIdent("-1a"))
        assertEquals("""\-""", JsScripts.cssIdent("-"))
        assertEquals("aria-label", JsScripts.cssIdent("aria-label"))
    }

    @Test
    fun `TC#29 대체 셀렉터는 태그와 속성 전부를 속성 셀렉터로 만든다`() {
        assertEquals(
            """div[a="1"][b="x\"y"]""",
            JsScripts.colorFallbackSelector(JSONObject(info("x", attrs = mapOf("b" to "x\"y", "a" to "1")))),
        )
        assertNull(JsScripts.colorFallbackSelector(JSONObject(info("x"))))
    }

    @Test
    fun `TC#29 경계 문자가 든 태그, 이름, 값으로도 규칙 하나를 벗어나지 않는다`() {
        listOf(")", "{", "}", ";", "\\", "*/", "/*", "\"", "\n").forEach { n ->
            val sel = JsScripts.colorFallbackSelector(JSONObject(info("x", tag = "d${n}v", attrs = mapOf("a${n}b" to "v${n}w"))))!!
            assertEquals("$n → $sel", emptyList<String>(), unescapedBoundaries("$sel { color: red }"))
        }
    }

    /** CSS 토큰 규칙대로 훑어 문자열·이스케이프 밖에 남은 경계 문자를 모은다. 규칙 하나의 정상 구조인 { } 한 쌍은 뺀다. */
    private fun unescapedBoundaries(css: String): List<String> {
        val found = mutableListOf<String>()
        var inString = false
        var i = 0
        while (i < css.length) {
            val c = css[i]
            when {
                c == '\\' -> i++
                inString && c == '"' -> inString = false
                inString && c == '\n' -> found += "newline in string"
                inString -> {}
                c == '"' -> inString = true
                c in "{}();" -> found += c.toString()
                c == '/' && css.getOrNull(i + 1) == '*' -> found += "/*"
                c == '\n' -> found += "newline"
            }
            i++
        }
        if (inString) found += "unclosed string"
        return found - listOf("{", "}")
    }
}
