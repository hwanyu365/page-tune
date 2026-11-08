package com.hwanyu365.pagetune.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickRuleScriptTest {

    private val page = """
        <html><body>
          <a id="blank" href="/next" target="_blank" onclick="window.clicked='blank'">next</a>
          <a id="named" href="/named" target="other">named</a>
          <a id="self" href="#same" target="_self" onclick="window.clicked='self'">self</a>
          <a id="nested" href="/nested" target="_blank"><span id="inner">in</span></a>
          <button id="opener" onclick="window.open('/opened'); window.clicked='opener'">open</button>
          <button id="plain" onclick="window.clicked='plain'">plain</button>
        </body></html>
    """.trimIndent()

    private fun details(id: String) = JSONObject().put("selector", "#$id").put("id", id).put("tagName", "").put("textContent", "").toString()

    private fun press(engine: HtmlEngine, id: String): String {
        engine.eval("window.clicked = 'none';")
        return engine.eval(JsScripts.getClickEventScript(details(id)))?.toString() ?: "null"
    }

    @Test
    fun `TC#57 새 창 링크는 클릭하지 않고 절대 주소를 돌려준다`() {
        HtmlEngine(page).use { engine ->
            assertEquals("http://fixture.test/next", press(engine, "blank"))
            assertEquals("none", engine.string("window.clicked"))
            assertEquals("http://fixture.test/named", press(engine, "named"))
            assertEquals("http://fixture.test/nested", press(engine, "inner"))
        }
    }

    @Test
    fun `TC#57 클릭 처리기의 window_open 주소를 가로채 돌려주고 원래 함수를 되돌린다`() {
        HtmlEngine(page).use { engine ->
            engine.eval("window.originalOpen = window.open;")
            assertEquals("http://fixture.test/opened", press(engine, "opener"))
            assertEquals("opener", engine.string("window.clicked"))
            assertTrue(engine.bool("window.open === window.originalOpen"))
        }
    }

    @Test
    fun `TC#57 같은 창 요소는 그대로 클릭하고 주소를 돌려주지 않는다`() {
        HtmlEngine(page).use { engine ->
            assertEquals("null", press(engine, "self"))
            assertEquals("self", engine.string("window.clicked"))
            assertEquals("null", press(engine, "plain"))
            assertEquals("plain", engine.string("window.clicked"))
        }
    }

    @Test
    fun `TC#57 base target 이 새 창이면 대상 없는 링크도 새 창 링크로 본다`() {
        val based = "<html><head><base target=\"_blank\"></head><body><a id=\"x\" href=\"/based\">x</a></body></html>"
        HtmlEngine(based).use { engine -> assertEquals("http://fixture.test/based", press(engine, "x")) }
    }

    @Test
    fun `TC#57 두 번 탭과 버튼이 쓰는 한 스크립트가 새 창 링크 주소를 돌려준다`() {
        HtmlEngine(page).use { engine ->
            engine.eval("window.clicked = 'none';")
            assertEquals("http://fixture.test/next", engine.eval(JsScripts.getClickEventScript(details("blank"))).toString())
            assertEquals("none", engine.string("window.clicked"))
        }
    }
}
