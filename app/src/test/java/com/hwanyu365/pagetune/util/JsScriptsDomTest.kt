package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsScriptsDomTest {

    private fun adPage(adId: String) = """
        <html><body>
          <div id="main">
            <section>
              <div class="title">t</div>
              <div id="$adId" class="slot12"><span>ad</span></div>
              <div class="body">b</div>
            </section>
          </div>
        </body></html>
    """

    private fun pick(engine: HtmlEngine, css: String): Pair<JSONObject, Int> {
        engine.eval(JsScripts.ELEMENT_PICKER_SCRIPT)
        engine.click(css)
        val (json, count) = engine.lastSelection()
        return JSONObject(json) to count
    }

    @Test
    fun `TC#16 불안정 id 요소는 안정 id 조상에서 시작하는 구조 경로로 저장되고 다른 판에서도 그 요소만 숨긴다`() {
        val info = HtmlEngine(adPage("div-gpt-ad-1712345678-0")).use { pick(it, "section > div:nth-of-type(2)").first }

        assertEquals("", info.getString("id"))
        assertEquals("#main > section:nth-of-type(1) > div:nth-of-type(2)", info.getString("selector"))

        HtmlEngine(adPage("div-gpt-ad-1799999999-0")).use { second ->
            second.eval(JsScripts.getMutationObserverHideScript(listOf(info.toString())))
            assertTrue(second.isHidden("section > div:nth-of-type(2)"))
            assertFalse(second.isHidden("div.title"))
            assertFalse(second.isHidden("div.body"))
            assertFalse(second.isHidden("section"))
        }
    }

    @Test
    fun `TC#16 안정 id 조상이 없으면 문서 루트에서 시작한다`() {
        val html = "<html><body><div class=\"wrap\"><p>a</p><p>b</p></div></body></html>"
        val info = HtmlEngine(html).use { pick(it, "p:nth-of-type(2)").first }

        assertEquals("html:nth-of-type(1) > body:nth-of-type(1) > div.wrap > p:nth-of-type(2)", info.getString("selector"))
    }

    @Test
    fun `TC#16 안정 id 요소는 그 id 로 저장한다`() {
        HtmlEngine("<html><body><div id=\"header\">h</div></body></html>").use {
            val (info, count) = pick(it, "#header")
            assertEquals("#header", info.getString("selector"))
            assertEquals("header", info.getString("id"))
            assertEquals(1, count)
        }
    }

    @Test
    fun `TC#17 숫자로 시작하는 id 와 특수문자 class 는 이스케이프돼 그 요소 하나만 매칭한다`() {
        val html = "<html><body><div id=\"1abc\">x</div><div class=\"a:b\">y</div><div class=\"a\">z</div></body></html>"
        HtmlEngine(html).use { engine ->
            val (byId, idCount) = pick(engine, "div:nth-of-type(1)")
            assertEquals("#\\31 abc", byId.getString("selector"))
            assertEquals(1, idCount)

            val (byClass, classCount) = pick(engine, "div:nth-of-type(2)")
            assertTrue(byClass.getString("selector").endsWith("div.a\\:b"))
            assertEquals(1, classCount)
            assertEquals(1, engine.int("document.querySelectorAll(${JsScripts.jsString(byClass.getString("selector"))}).length"))
        }
    }

    @Test
    fun `TC#18 같은 틀 3개 중 하나를 고르면 매칭 3, 고유 요소는 매칭 1, 무효 셀렉터는 예외 없이 매칭 0`() {
        val html = "<html><body><div class=\"ad\">1</div><div class=\"ad\">2</div><div class=\"ad\">3</div><p class=\"only\">p</p></body></html>"
        HtmlEngine(html).use { engine ->
            assertEquals(3, pick(engine, "div:nth-of-type(2)").second)
            assertEquals(1, pick(engine, "p").second)
            assertEquals(0, engine.int("window.webPluginCountMatches('div[')"))
        }
    }

    private fun rule(selector: String, attrs: Map<String, String>, tag: String = "div", id: String = "") =
        JSONObject().put("selector", selector).put("tagName", tag).put("textContent", "").put("id", id)
            .put("attributes", JSONObject(attrs)).toString()

    private val bannerAttrs = mapOf("role" to "banner", "aria-label" to "ad box")

    private val bannerPage = """
        <html><body>
          <div id="roleOnly" role="banner" onclick="window.clicked='roleOnly'">r</div>
          <div id="both" role="banner" aria-label="ad box" onclick="window.clicked='both'">b</div>
          <div id="quote" title="it's &quot;q&quot;" @click="go()" onclick="window.clicked='quote'">q</div>
          <div id="keep">k</div>
          <p class="plain" onclick="window.clicked='plain'">p</p>
        </body></html>
    """

    private fun hide(engine: HtmlEngine, vararg details: String) =
        engine.eval(JsScripts.getMutationObserverHideScript(details.toList()))

    private fun clickRule(engine: HtmlEngine, details: String): String {
        engine.eval("window.clicked = 'none';")
        engine.eval(JsScripts.getClickEventScript(details))
        return engine.string("window.clicked")
    }

    @Test
    fun `TC#123 a 셀렉터가 무효면 태그와 속성이 모두 일치하는 요소만 숨기고 클릭한다`() {
        HtmlEngine(bannerPage).use { engine ->
            hide(engine, rule("div[", bannerAttrs))
            assertTrue(engine.isHidden("#both"))
            assertFalse(engine.isHidden("#roleOnly"))
        }
        HtmlEngine(bannerPage).use { assertEquals("both", clickRule(it, rule("div[", bannerAttrs))) }
    }

    @Test
    fun `TC#123 b 속성값의 따옴표와 at 속성 이름도 예외 없이 찾는다`() {
        val attrs = mapOf("title" to "it's \"q\"", "@click" to "go()")
        HtmlEngine(bannerPage).use { engine ->
            hide(engine, rule("div[", attrs))
            assertTrue(engine.isHidden("#quote"))
        }
        HtmlEngine(bannerPage).use { assertEquals("quote", clickRule(it, rule("div[", attrs))) }
    }

    @Test
    fun `TC#123 c 저장 속성이 없으면 태그만으로 찾지 않는다`() {
        HtmlEngine(bannerPage).use { engine ->
            hide(engine, rule("div[", emptyMap()))
            assertEquals(0, engine.int("Array.from(document.querySelectorAll('div')).filter(e => e.style.display === 'none').length"))
        }
        HtmlEngine(bannerPage).use { assertEquals("none", clickRule(it, rule("div[", emptyMap()))) }
    }

    @Test
    fun `TC#123 d 잘못된 규칙이 있어도 같은 Tune 의 정상 규칙은 동작한다`() {
        HtmlEngine(bannerPage).use { engine ->
            hide(engine, rule("div[", emptyMap()), rule("p.plain", emptyMap(), tag = "p"))
            assertTrue(engine.isHidden("p.plain"))
        }
    }

    @Test
    fun `TC#123 e 유효하지만 0개인 셀렉터도 3단계는 전부 일치 기준이다`() {
        HtmlEngine(bannerPage).use { engine ->
            hide(engine, rule("div.missing", bannerAttrs))
            assertTrue(engine.isHidden("#both"))
            assertFalse(engine.isHidden("#roleOnly"))
        }
        HtmlEngine(bannerPage).use { assertEquals("both", clickRule(it, rule("div.missing", bannerAttrs))) }
    }

    @Test
    fun `TC#123 f 요소 정보가 JSON 이 아니면 그 규칙만 건너뛴다`() {
        HtmlEngine(bannerPage).use { engine ->
            hide(engine, "not json", rule("p.plain", emptyMap(), tag = "p"))
            assertTrue(engine.isHidden("p.plain"))
        }
        HtmlEngine(bannerPage).use { assertEquals("none", clickRule(it, "not json")) }
    }

    private val hostile = listOf(
        "');window.pwned=1;//",
        "</script><script>window.pwned=2</script>",
        "\\'",
        "\";window.pwned=3;//",
        "a\u2028b\nc",
    )

    @Test
    fun `TC#30 리터럴을 JS 에서 평가하면 입력과 같은 문자열이 된다`() {
        HtmlEngine("<html><body></body></html>").use { engine ->
            hostile.forEach { assertEquals(it, engine.string(JsScripts.jsString(it))) }
        }
    }

    @Test
    fun `TC#31 적대적 문자열이 든 요소 정보로 만든 숨기기, 클릭, 색상 스크립트는 코드를 실행하지 않는다`() {
        hostile.forEach { value ->
            val details = rule("div[", mapOf("title" to value))
            HtmlEngine("<html><body><div id=\"t\">t</div><div id=\"k\">k</div></body></html>").use { engine ->
                engine.eval("document.getElementById('t').setAttribute('title', ${JsScripts.jsString(value)}); window.clicked = 'none'; document.getElementById('t').onclick = function() { window.clicked = 't'; };")

                hide(engine, details)
                engine.eval(JsScripts.getClickEventScript(details))
                engine.eval(JsScripts.getColorScriptFor(listOf(PluginAction(pluginId = 1, actionType = "color", targetSelector = details, styleCss = "color:#ffffff"))))

                assertEquals(value, "undefined", engine.string("typeof window.pwned"))
                assertTrue(value, engine.isHidden("#t"))
                assertFalse(value, engine.isHidden("#k"))
                assertEquals(value, "t", engine.string("window.clicked"))
                assertEquals(value, 1, engine.int("document.getElementById('webPluginColorStyle').sheet.cssRules.length"))
            }
        }
    }

    @Test
    fun `TC#31 경계 문자가 든 class 셀렉터의 색상 규칙은 기대한 수의 CSS 규칙만 만든다`() {
        val selectors = listOf("div.a} body { color: red", "div.a\\}b", "div.a) { x", "div.a/* x", "#main > div.ok")
        val actions = selectors.map {
            PluginAction(pluginId = 1, actionType = "color", targetSelector = rule(it, mapOf("role" to "x")), styleCss = "color:#ffffff")
        }
        HtmlEngine("<html><head></head><body><div role=\"x\">x</div></body></html>").use { engine ->
            engine.eval(JsScripts.getColorScriptFor(actions))
            // 거부된 4개는 대체 규칙 1개씩, 통과한 1개는 원래 규칙 + @supports 블록이다.
            assertEquals(6, topLevelBlocks(engine.string("document.getElementById('webPluginColorStyle').textContent")))
            // HtmlUnit CSS 파서는 @supports 블록을 버리므로 엔진이 센 규칙은 하나 적다.
            assertEquals(5, engine.int("document.getElementById('webPluginColorStyle').sheet.cssRules.length"))
        }
    }

    /** CSS 텍스트의 최상위 { } 블록 수. 문자열과 이스케이프 안의 괄호는 세지 않는다. */
    private fun topLevelBlocks(css: String): Int {
        var depth = 0
        var blocks = 0
        var inString = false
        var i = 0
        while (i < css.length) {
            val c = css[i]
            when {
                c == '\\' -> i++
                inString -> if (c == '"') inString = false
                c == '"' -> inString = true
                c == '{' -> if (depth++ == 0) blocks++
                c == '}' -> depth--
            }
            i++
        }
        return blocks
    }
}
