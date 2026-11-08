package com.hwanyu365.pagetune.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleHighlightDomTest {

    private fun rule(selector: String, attrs: Map<String, String> = emptyMap(), tag: String = "div", text: String = "", id: String = "") =
        JSONObject().put("selector", selector).put("tagName", tag).put("textContent", text).put("id", id)
            .put("attributes", JSONObject(attrs)).toString()

    private fun highlight(engine: HtmlEngine, type: String, details: String): Int =
        (engine.eval(JsScripts.getHighlightScript(type, details)) as Number).toInt()

    private fun outline(engine: HtmlEngine, css: String) =
        engine.string("document.querySelector(${JsScripts.jsString(css)}).style.outline")

    private val page = """
        <html><body>
          <div id="wrap"><div class="ad">a1</div></div>
          <div class="ad">a2</div>
          <a class="next" href="#1">Next</a>
          <a class="next" href="#2">Next</a>
          <div id="banner" role="banner">b</div>
          <p id="styled" class="styled" style="outline: 1px dotted red">s</p>
          <p id="keep">k</p>
        </body></html>
    """

    @Test
    fun `TC#41 숨기기·색상 규칙은 일치 요소 전부에 테두리를 그린다`() {
        HtmlEngine(page).use { engine ->
            assertEquals(2, highlight(engine, "color", rule("div.ad")))
            assertTrue(outline(engine, "#wrap > div.ad").isNotEmpty())
            assertTrue(outline(engine, "body > div.ad").isNotEmpty())
            assertEquals("", outline(engine, "#keep"))
        }
    }

    private val colorPage = """
        <html><head></head><body>
          <div id="a" role="banner">A</div>
          <div id="b" class="b" data-x="1">B</div>
        </body></html>
    """

    private fun outlined(engine: HtmlEngine) = listOf("a", "b").filter { outline(engine, "#$it").isNotEmpty() }

    private fun colored(engine: HtmlEngine, details: String): List<String> {
        engine.eval(JsScripts.getColorScriptFor(listOf(com.hwanyu365.pagetune.db.PluginAction(pluginId = 1, actionType = "color", targetSelector = details, styleCss = "color:#ffffff"))))
        return listOf("a", "b").filter { engine.string("getComputedStyle(document.getElementById('$it')).color") == "rgb(255, 255, 255)" }
    }

    private fun coloredCount(engine: HtmlEngine, details: String): Int {
        engine.eval(JsScripts.getColorScriptFor(listOf(com.hwanyu365.pagetune.db.PluginAction(pluginId = 1, actionType = "color", targetSelector = details, styleCss = "color:#ffffff"))))
        return engine.int("Array.from(document.body.getElementsByTagName('*')).filter(function(e) { return getComputedStyle(e).color === 'rgb(255, 255, 255)'; }).length")
    }

    @Test
    fun `TC#41 색상 규칙은 CSS 계약을 통과하지 못한 셀렉터 대신 대체 셀렉터 대상만 강조한다`() {
        val details = rule("div.b[data-x]", mapOf("role" to "banner"))
        HtmlEngine(colorPage).use { engine ->
            assertEquals(1, highlight(engine, "color", details))
            assertEquals(listOf("a"), outlined(engine))
            engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)
            assertEquals(listOf("a"), colored(engine, details))
        }
    }

    @Test
    fun `TC#41 색상 규칙의 셀렉터가 무효면 저장 id 가 아니라 대체 셀렉터 대상을 강조한다`() {
        HtmlEngine(colorPage).use { engine ->
            assertEquals(1, highlight(engine, "color", rule("div[[", mapOf("role" to "banner"), id = "b")))
            assertEquals(listOf("a"), outlined(engine))
        }
    }

    @Test
    fun `TC#41 색상 규칙의 셀렉터가 유효하지만 0개면 대체 없이 찾지 못함이다`() {
        HtmlEngine(colorPage).use { engine ->
            assertEquals(0, highlight(engine, "color", rule("div.none", mapOf("role" to "banner"))))
            assertEquals(emptyList<String>(), outlined(engine))
        }
    }

    @Test
    fun `TC#41 클릭 규칙은 첫 요소 하나에만 테두리를 그린다`() {
        HtmlEngine(page).use { engine ->
            assertEquals(1, highlight(engine, "click", rule("a.next", tag = "a", text = "Next")))
            assertTrue(outline(engine, "a.next:nth-of-type(1)").isNotEmpty())
            assertEquals("", outline(engine, "a.next:nth-of-type(2)"))
        }
    }

    @Test
    fun `TC#41 셀렉터가 무효여도 태그와 속성이 전부 일치하는 요소를 찾는다`() {
        HtmlEngine(page).use { engine ->
            assertEquals(1, highlight(engine, "hidden", rule("div[[", mapOf("role" to "banner"))))
            assertTrue(outline(engine, "#banner").isNotEmpty())
        }
    }

    @Test
    fun `TC#41 찾지 못하면 0 을 돌려준다`() {
        HtmlEngine(page).use { engine ->
            assertEquals(0, highlight(engine, "hidden", rule("section.none")))
            assertEquals(0, highlight(engine, "click", rule("section.none", tag = "section")))
            assertEquals(0, highlight(engine, "color", "not json"))
        }
    }

    @Test
    fun `TC#41 해제하면 원래 outline 으로 돌아온다`() {
        HtmlEngine(page).use { engine ->
            val before = outline(engine, "#styled")
            assertEquals(1, highlight(engine, "color", rule("p.styled", tag = "p")))
            assertTrue(outline(engine, "#styled") != before)
            engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)
            assertEquals(before, outline(engine, "#styled"))
        }
    }

    @Test
    fun `TC#41 다른 규칙을 강조하면 이전 강조가 풀린다`() {
        HtmlEngine(page).use { engine ->
            highlight(engine, "color", rule("p.styled", tag = "p"))
            highlight(engine, "color", rule("#banner", id = "banner"))
            assertEquals("1px dotted red", outline(engine, "#styled"))
            assertTrue(outline(engine, "#banner").isNotEmpty())
        }
    }

    @Test
    fun `TC#41 숨기기 규칙 강조는 숨긴 요소와 접힌 부모를 보이고, 해제하면 다시 숨긴다`() {
        HtmlEngine(page).use { engine ->
            val ad = rule("#wrap > div.ad")
            engine.eval(JsScripts.getMutationObserverHideScript(listOf(ad)))
            assertTrue(engine.isHidden("#wrap > div.ad"))
            assertTrue(engine.isHidden("#wrap"))

            assertEquals(1, highlight(engine, "hidden", ad))
            assertFalse(engine.isHidden("#wrap > div.ad"))
            assertFalse(engine.isHidden("#wrap"))
            assertTrue(outline(engine, "#wrap > div.ad").isNotEmpty())

            engine.eval("window.webPluginApplyHide();")
            assertFalse("강조 중에는 다시 적용해도 숨기지 않는다", engine.isHidden("#wrap > div.ad"))

            engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)
            assertTrue(engine.isHidden("#wrap > div.ad"))
            assertTrue(engine.isHidden("#wrap"))
            assertEquals("", outline(engine, "#wrap > div.ad"))
        }
    }

    @Test
    fun `TC#41 해제 스크립트는 강조가 없어도 오류 없이 끝난다`() {
        HtmlEngine(page).use { engine ->
            engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)
            assertEquals("", outline(engine, "#keep"))
        }
    }

    private fun pick(engine: HtmlEngine, css: String) {
        engine.eval(JsScripts.ELEMENT_PICKER_SCRIPT)
        engine.click(css)
    }

    @Test
    fun `TC#41 선택 중인 요소를 강조했다가 해제하면 선택 테두리로 돌아온다`() {
        HtmlEngine(page).use { engine ->
            pick(engine, "#keep")
            val selected = outline(engine, "#keep")
            assertTrue(selected.isNotEmpty())

            assertEquals(1, highlight(engine, "color", rule("#keep", tag = "p", id = "keep")))
            engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)

            assertEquals(selected, outline(engine, "#keep"))
        }
    }

    @Test
    fun `TC#41 강조 중 선택이 다른 요소로 옮겨 가면 해제 때 이전 선택 테두리를 남기지 않는다`() {
        HtmlEngine("<html><body><div id=\"box\"><p id=\"child\">c</p></div></body></html>").use { engine ->
            pick(engine, "#child")
            assertEquals(1, highlight(engine, "color", rule("#child", tag = "p", id = "child")))
            engine.eval("window.webPluginSelectParent();")
            engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)

            assertEquals("", outline(engine, "#child"))
            assertTrue(outline(engine, "#box").isNotEmpty())
        }
    }

    private val hostile = listOf(
        "');window.pwned=1;//",
        "</script><script>window.pwned=2</script>",
        "\\'",
        "\";window.pwned=3;//",
        "a b\nc",
    )

    @Test
    fun `TC#41 적대적 문자열·경계 문자 셀렉터로 만든 강조 스크립트는 코드를 실행하지 않는다`() {
        val selectors = listOf("div[", "div.a} body { color: red", "div.a) { x", "div.a/* x")
        var colorFound = 0
        hostile.forEach { value ->
            selectors.forEach { selector ->
                HtmlEngine("<html><body><div id=\"t\">t</div></body></html>").use { engine ->
                    engine.eval("document.getElementById('t').setAttribute('title', ${JsScripts.jsString(value)});")
                    val details = rule(selector, mapOf("title" to value), text = value)
                    listOf("hidden", "click").forEach { type ->
                        assertEquals("$type $value", 1, highlight(engine, type, details))
                    }
                    val colorHighlighted = highlight(engine, "color", details)
                    engine.eval(JsScripts.CLEAR_HIGHLIGHT_SCRIPT)
                    // HtmlUnit 셀렉터 엔진은 속성값 문자열 안의 이스케이프된 따옴표를 매칭하지 못한다 → 색상은 실제로 칠해진 수와 같은지로 본다.
                    assertEquals("color $value", coloredCount(engine, details), colorHighlighted)
                    colorFound += colorHighlighted
                    assertEquals(value, "undefined", engine.string("typeof window.pwned"))
                }
            }
        }
        assertTrue("색상 강조가 한 번도 대상을 찾지 못하면 비교가 공허하다", colorFound >= selectors.size * (hostile.size - 1))
    }
}
