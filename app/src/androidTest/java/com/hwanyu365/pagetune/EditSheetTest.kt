package com.hwanyu365.pagetune

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditSheetTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc518")
    fun `TC#518 요소를 고르면 시트는 선택 줄만큼만 올라오고 고른 요소가 시트 위에 보이며 규칙 만들기에서 펼쳐진다`() = lowSheet()

    private val page = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0;background:#fff">
        <div id="top">TOP</div><div style="height:3000px"></div>
        <div id="wrap" style="padding:8px"><div id="low" style="height:40px">LOW</div></div>
        <div style="height:600px"></div><div id="big" style="height:5000px">BIG</div>
        <div style="height:600px"></div><div id="end" style="height:40px">END</div>
        <div id="bar" style="position:fixed;left:0;right:0;bottom:0;height:40px;background:#eee">BAR</div>
        <div id="sunk" style="position:fixed;right:0;bottom:-30px;width:60px;height:60px;background:#ccc">SUNK</div>
        </body></html>
    """.trimIndent()

    private fun lowSheet() = LocalPage(page).use { server ->
        ActivityScenario.launch<PluginEditActivity>(Intent(targetContext, PluginEditActivity::class.java)).use {
            val web = loadInEditor(server.url, "document.getElementById('bar')")
            inspectOn(web)
            assertSheetBottomOfWeb(selectionRow = false)

            pick(web, "low")
            assertLow(web, "low")

            tap(R.id.btn_select_parent)
            eventually { assertEquals("#wrap", textOf(R.id.tv_selector_detail)) }
            assertLow(web, "wrap")
            tap(R.id.btn_select_child)
            eventually { assertEquals("#low", textOf(R.id.tv_selector_detail)) }
            assertLow(web, "low")

            onView(withId(R.id.btn_make_rule)).perform(click())
            eventually { assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheetState()) }
            eventually { onView(withId(R.id.rg_action_type)).check(matches(isDisplayed())) }
            onView(withId(R.id.btn_add_action)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_select_parent)).perform(click())
            eventually { assertEquals("#wrap", textOf(R.id.tv_selector_detail)) }
            assertLow(web, "wrap")

            onView(withId(R.id.btn_make_rule)).perform(click())
            eventually { assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheetState()) }
            pick(web, "big")
            assertLow(web, "big")
            eventually { assertTrue("큰 요소의 위쪽이 보이지 않음", rect(web, "big").let { it.getDouble("top") in -1.0..1.0 }) }

            tap(R.id.btn_toggle_inspect)
            eventually { onMain { assertEquals(View.GONE, resumedActivity().findViewById<View>(R.id.layout_traversal_actions).visibility) } }
            assertSheetBottomOfWeb(selectionRow = false)

            scrollToEnd(web)
            eventually { assertEquals("true", web.js("document.getElementById('end').getBoundingClientRect().bottom <= window.innerHeight")) }
            inspectOn(web)
            pick(web, "end")
            assertLow(web, "end")
            tap(R.id.btn_toggle_inspect)
            eventually { onMain { assertEquals(View.GONE, resumedActivity().findViewById<View>(R.id.layout_traversal_actions).visibility) } }
            assertSheetBottomOfWeb(selectionRow = false)

            web.js("window.scrollTo(0, 0)")
            eventually { assertEquals("0", web.js("Math.round(window.pageYOffset)")) }
            inspectOn(web)
            pick(web, "bar")
            assertLow(web, "bar")
            assertEquals("고정 요소를 골랐는데 페이지가 스크롤됨", "0", web.js("Math.round(window.pageYOffset)"))
            pick(web, "sunk")
            SystemClock.sleep(1_500)
            assertEquals("화면 밖 고정 요소를 골랐는데 페이지가 스크롤됨", "0", web.js("Math.round(window.pageYOffset)"))

            tap(R.id.btn_toggle_inspect)
            assertSheetBottomOfWeb(selectionRow = false)
        }
        LocalPage(innerScrollPage).use { server ->
            ActivityScenario.launch<PluginEditActivity>(Intent(targetContext, PluginEditActivity::class.java)).use {
                val web = loadInEditor(server.url, "document.getElementById('deep')")
                inspectOn(web)
                pick(web, "deep")
                assertLow(web, "deep")
            }
        }
    }

    // 문서는 스크롤되지 않고 안쪽 영역만 스크롤된다 → 문서 스크롤로는 요소를 보일 수 없다.
    private val innerScrollPage = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0;height:100vh;overflow:hidden;background:#fff">
        <div id="inner" style="height:100%;overflow:auto"><div style="height:2000px"></div><div id="deep" style="height:40px">DEEP</div><div style="height:600px"></div></div>
        </body></html>
    """.trimIndent()

    private fun sheetState(): Int = onMain { BottomSheetBehavior.from(resumedActivity().findViewById<View>(R.id.layout_bottom_config_container)).state }

    private fun rect(web: android.webkit.WebView, id: String): JSONObject =
        JSONObject(JSONObject("{\"v\":${web.js("JSON.stringify((function(r){return {top:r.top,bottom:r.bottom,height:window.innerHeight};})(document.getElementById('$id').getBoundingClientRect()))")}}").getString("v"))

    // 고른 요소가 WebView 안에 있고 WebView 가 시트 바로 위에서 끝나면, 그 요소는 시트에 가려지지 않는다.
    private fun assertLow(web: android.webkit.WebView, id: String) {
        eventually { assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheetState()) }
        onView(withId(R.id.layout_traversal_actions)).check(matches(isDisplayed()))
        onMain { assertTrue("접힌 시트의 규칙 종류가 보임", !resumedActivity().findViewById<View>(R.id.rg_action_type).isShown) }
        assertSheetBottomOfWeb(selectionRow = true)
        assertEquals("true", web.js("document.getElementById('$id').style.outline !== ''"))
        eventually {
            val r = rect(web, id)
            val visible = minOf(r.getDouble("bottom"), r.getDouble("height")) - maxOf(r.getDouble("top"), 0.0)
            assertTrue("$id 가 WebView 밖: $r", visible > 0 && r.getDouble("top") >= -1)
            if (r.getDouble("bottom") - r.getDouble("top") <= r.getDouble("height")) assertTrue("$id 가 일부만 보임: $r", r.getDouble("bottom") <= r.getDouble("height") + 1)
        }
    }

    private fun assertSheetBottomOfWeb(selectionRow: Boolean) = eventually {
        onMain {
            val activity = resumedActivity()
            val row = activity.findViewById<View>(R.id.layout_traversal_actions)
            assertEquals(selectionRow, row.isShown)
            val web = IntArray(2).also { activity.findViewById<View>(R.id.wv_inspector).getLocationOnScreen(it) }[1] + activity.findViewById<View>(R.id.wv_inspector).height
            val sheet = IntArray(2).also { activity.findViewById<View>(R.id.layout_bottom_config_container).getLocationOnScreen(it) }[1]
            assertTrue("WebView 하단 $web, 시트 상단 $sheet", kotlin.math.abs(web - sheet) <= 1)
        }
    }
}
