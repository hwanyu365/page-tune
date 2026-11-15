package com.hwanyu365.pagetune

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.Matchers.allOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreFlowTest {

    private val html = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;background:#fff;color:#000">
        <div id="ad">AD</div><div id="box">BOX</div><div style="height:5000px"></div>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc509")
    fun `TC#509 편집 화면으로 만든 Tune 이 열람 화면에 적용되고 복제하면 규칙까지 복사된다`() = createApplyDuplicate()

    private fun createApplyDuplicate() {
        LocalPage(html).use { page ->
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(tunesTab).perform(click())
                eventually { onView(withId(R.id.fab_add_plugin)).check(matches(isDisplayed())) }
                onView(withId(R.id.fab_add_plugin)).perform(click())
                createTune(page.url)
                eventually { onView(allOf(withId(R.id.tv_plugin_name), withText(NAME))).check(matches(isDisplayed())) }
            }

            val tune = runBlocking { database.pluginDao().getAllPlugins() }.single { it.name == NAME }
            val rules = rulesOf(tune.id)
            assertEquals(listOf("hidden", "color", "scroll"), rules.map { it.first })
            assertEquals("#ad", JSONObject(rules[0].second).getString("selector"))
            assertEquals("#box", JSONObject(rules[1].second).getString("selector"))
            assertEquals("background-color:$BG;color:$TEXT", rules[1].third)
            assertEquals(SCROLL_Y, JSONObject(rules[2].second).getInt("y"))

            val viewer = Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", addWebPage("Core Page", page.url, tune.id))
            ActivityScenario.launch<WebBrowserActivity>(viewer).use {
                val web = webView(R.id.wv_browser)
                eventually { assertEquals("\"none\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
                eventually { assertEquals("\"rgb(211, 47, 47)|rgb(255, 255, 255)\"", web.js("var s = getComputedStyle(document.getElementById('box')); s.backgroundColor + '|' + s.color")) }
                eventually { assertEquals("$SCROLL_Y", web.js("Math.round(window.pageYOffset)")) }
            }

            ActivityScenario.launch(MainActivity::class.java).use {
                onView(tunesTab).perform(click())
                eventually { onView(allOf(withId(R.id.btn_more), isDescendantOfA(withChild(NAME)))).perform(click()) }
                onView(withText(R.string.tune_duplicate)).perform(click())
                val copy = eventually { runBlocking { database.pluginDao().getAllPlugins() }.single { it.id != tune.id } }
                assertNotEquals(tune.id, copy.id)
                assertEquals(rules, rulesOf(copy.id))
            }
        }
    }

    private fun createTune(url: String) {
        eventually { onView(withId(R.id.et_inp_taddr)).perform(replaceText(url)) }
        onView(withId(R.id.btn_load_url)).perform(click())
        val web = webView(R.id.wv_inspector)
        eventually { assertEquals("\"complete\"", web.js("document.getElementById('box') ? document.readyState : ''")) }

        tap(R.id.btn_toggle_inspect)
        eventually { assertEquals("true", web.js("window.webPluginInspectEnabled === true")) }

        pick(web, "ad")
        tap(R.id.rb_action_hidden)
        tap(R.id.btn_add_action)

        pick(web, "box")
        tap(R.id.rb_action_color)
        pickColor(R.id.btn_color_bg, BG)
        pickColor(R.id.btn_color_text, TEXT)
        tap(R.id.btn_add_action)

        web.js("window.scrollTo(0, $SCROLL_Y); Math.round(window.pageYOffset)").also { assertEquals("$SCROLL_Y", it) }
        tap(R.id.btn_set_scroll)
        eventually { assertTrue(rulesText().contains("$SCROLL_Y")) }

        onView(withId(R.id.et_inp_pname)).perform(replaceText(NAME))
        tap(R.id.btn_plugin_save)
    }

    private fun rulesText(): String = allText(R.id.layout_actions_list)

    private fun withChild(text: String) = androidx.test.espresso.matcher.ViewMatchers.hasDescendant(withText(text))

    private companion object {
        const val NAME = "Core Tune"
        const val BG = "#D32F2F"
        const val TEXT = "#FFFFFF"
        const val SCROLL_Y = 600
    }
}
