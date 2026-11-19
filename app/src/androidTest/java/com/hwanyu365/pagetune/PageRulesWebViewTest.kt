package com.hwanyu365.pagetune

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageRulesWebViewTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc128")
    fun `TC#128 실제 WebView 에서 무효 셀렉터 규칙은 태그와 속성이 모두 일치하는 요소에만 적용되고 0개 색상 규칙은 대체하지 않는다`() = fallbackInWebView()

    private fun fallbackInWebView() = LocalPage(PAGE).use { server ->
        val colors = listOf(
            color(rule("div[", BANNER), "background-color:#112233"),
            color(rule("#keep", emptyMap()), "background-color:#445566"),
            color(rule("div.nothing", QUOTE), "background-color:#778899"),
            color(rule("div:unknownpseudo", PSEUDO), "background-color:#aabbcc"),
        )
        open(server.url, colors) { web ->
            eventually { assertEquals("\"rgb(17, 34, 51)\"", web.js(background("both"))) }
            assertEquals("\"rgb(68, 85, 102)\"", web.js(background("keep")))
            assertEquals("속성 하나만 일치하는 요소에 대체가 적용됨", "\"rgba(0, 0, 0, 0)\"", web.js(background("roleOnly")))
            assertEquals("셀렉터가 유효하지만 0개인 색상 규칙에 대체가 적용됨", "\"rgba(0, 0, 0, 0)\"", web.js(background("quote")))
            assertEquals("브라우저가 해석하지 못하는 셀렉터에 대체가 적용되지 않음", "\"rgb(170, 187, 204)\"", web.js(background("pseudo")))
        }

        open(server.url, listOf(color(rule("div[", QUOTE), "background-color:#778899"))) { web ->
            eventually { assertEquals("\"rgb(119, 136, 153)\"", web.js(background("quote"))) }
        }

        open(server.url, listOf(hide(rule("div[", BANNER)), hide(rule("div[", QUOTE)))) { web ->
            eventually { assertEquals("\"none|none\"", web.js(display("both") + " + '|' + " + display("quote"))) }
            assertEquals("\"block\"", web.js(display("roleOnly")))
        }

        for ((attrs, expected) in listOf(BANNER to "both", QUOTE to "quote")) {
            open(server.url, listOf(clickRule("gnb_next", rule("div[", attrs)))) { web ->
                eventually { onView(withId(R.id.btn_stack_next)).check(matches(isDisplayed())) }
                onView(withId(R.id.btn_stack_next)).perform(click())
                eventually { assertEquals("\"$expected\"", web.js("String(window.clicked)")) }
            }
        }
    }

    private fun open(url: String, rules: List<PluginAction>, check: (android.webkit.WebView) -> Unit) {
        val pageId = addWebPage("Page", url, addTune("Rules ${rules.size}", rules))
        ActivityScenario.launch<WebBrowserActivity>(Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId)).use {
            val web = webView(R.id.wv_browser)
            eventually { assertEquals("\"complete\"", web.js("document.body ? document.readyState : ''")) }
            check(web)
        }
    }

    private fun rule(selector: String, attrs: Map<String, String>) =
        JSONObject().put("selector", selector).put("tagName", "div").put("textContent", "").put("id", "").put("attributes", JSONObject(attrs)).toString()

    private fun color(target: String, css: String) = PluginAction(pluginId = 0, actionType = "color", targetSelector = target, styleCss = css)

    private fun hide(target: String) = PluginAction(pluginId = 0, actionType = "hidden", targetSelector = target)

    private fun background(id: String) = "getComputedStyle(document.getElementById('$id')).backgroundColor"

    private fun display(id: String) = "getComputedStyle(document.getElementById('$id')).display"

    private companion object {
        val BANNER = mapOf("role" to "banner", "aria-label" to "ad box")
        val QUOTE = mapOf("title" to "it's \"q\"", "@click" to "go()")
        val PSEUDO = mapOf("data-k" to "v")
        val PAGE = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0;background:#fff">
              <div id="roleOnly" role="banner" onclick="window.clicked='roleOnly'">r</div>
              <div id="both" role="banner" aria-label="ad box" onclick="window.clicked='both'">b</div>
              <div id="quote" title="it's &quot;q&quot;" @click="go()" onclick="window.clicked='quote'">q</div>
              <div id="keep">k</div>
              <div id="pseudo" data-k="v">u</div>
            </body></html>
        """.trimIndent()
    }
}
