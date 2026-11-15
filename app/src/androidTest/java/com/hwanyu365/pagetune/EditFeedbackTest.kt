package com.hwanyu365.pagetune

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputLayout
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.util.ColorPresets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditFeedbackTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc42")
    fun `TC#42 규칙을 누르면 요소를 강조하고 숨기기는 다시 누를 때까지 보인다`() = highlight()

    @Test
    @JvmName("tc44")
    fun `TC#44 색 선택의 프리셋·헥스·슬라이더가 같은 색을 공유하고 잘못된 헥스는 확인을 막는다`() = colorPicker()

    @Test
    @JvmName("tc45")
    fun `TC#45 불러오기가 실패하면 선택을 풀고 인라인 오류를 보이며 다시 시도로 복구한다`() = loadErrors()

    private fun editorFor(tune: Long) = ActivityScenario.launch<PluginEditActivity>(Intent(targetContext, PluginEditActivity::class.java).putExtra("plugin_id", tune))

    private fun sheetState(): Int = onMain { BottomSheetBehavior.from(resumedActivity().findViewById<View>(R.id.layout_bottom_config_container)).state }

    private fun tapRule(index: Int) = onMain {
        resumedActivity().findViewById<ViewGroup>(R.id.layout_actions_list).getChildAt(index).findViewById<View>(R.id.layout_rule_body).performClick()
    }

    private val highlightPage = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0;background:#fff">
        <div id="top">TOP</div><div style="height:4000px"></div><div id="low">LOW</div><div id="ad">AD</div><div id="feed"></div><div style="height:400px"></div>
        </body></html>
    """.trimIndent()

    private fun highlight() = LocalPage(highlightPage).use { page ->
        val details = { id: String -> """{"selector":"#$id","tagName":"div","id":"$id","attributes":{}}""" }
        val tune = addTune(
            "Highlight",
            listOf(
                PluginAction(pluginId = 0, actionType = "color", targetSelector = details("low"), styleCss = "background-color:#D32F2F"),
                PluginAction(pluginId = 0, actionType = "hidden", targetSelector = details("ad")),
                PluginAction(pluginId = 0, actionType = "scroll", targetSelector = """{"y":500}"""),
                PluginAction(pluginId = 0, actionType = "color", targetSelector = details("gone"), styleCss = "color:#FFFFFF"),
            ),
            testUrl = page.url,
        )
        editorFor(tune).use { scenario ->
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals("\"none\"", web.js("document.getElementById('feed') ? getComputedStyle(document.getElementById('ad')).display : 'loading'")) }
            fun outlined(id: String) = web.js("document.getElementById('$id').style.outline !== ''") == "true"

            onView(withId(R.id.sheet_handle)).perform(click())
            eventually { assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheetState()) }
            tapRule(0)
            eventually { assertTrue(outlined("low")) }
            eventually { assertTrue(web.js("Math.round(window.pageYOffset)").toInt() > 3000) }
            eventually { assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheetState()) }
            assertEquals("강조가 규칙을 지움", 4, ruleCount())
            eventually(timeoutMs = 6_000) { assertFalse("색상 강조가 3초 뒤에도 남음", outlined("low")) }

            tapRule(0)
            eventually { assertTrue(outlined("low")) }
            tapRule(1)
            eventually { assertFalse("다른 규칙을 눌러도 이전 강조가 남음", outlined("low")) }
            eventually { assertEquals("\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
            assertTrue(outlined("ad"))
            SystemClock.sleep(10_500)
            assertEquals("숨기기 강조가 시간으로 풀림", "\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display"))
            web.js("for (var i = 0; i < 20; i++) { var d = document.createElement('div'); d.textContent = 'more ' + i; document.getElementById('feed').appendChild(d); }")
            SystemClock.sleep(1_000)
            assertEquals("DOM 이 바뀌자 강조 중인 요소가 다시 숨겨짐", "\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display"))
            tapRule(1)
            eventually { assertEquals("\"none\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
            tapRule(1)
            eventually { assertEquals("\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
            tapRule(0)
            eventually { assertEquals("\"none\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }

            tapRule(2)
            eventually { assertEquals("500", web.js("Math.round(window.pageYOffset)")) }

            onView(withId(R.id.sheet_handle)).perform(click())
            eventually { assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheetState()) }
            tapRule(3)
            SystemClock.sleep(1_000)
            // 찾지 못했다는 안내는 토스트다 → 강조도 시트 접기도 없는 것으로 판정한다.
            assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheetState())
            assertEquals("0", web.js("document.querySelectorAll('[style*=\"outline\"]').length"))

            tapRule(1)
            eventually { assertEquals("\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
            tap(R.id.btn_plugin_cancel)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        editorFor(tune).use { scenario ->
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals("\"none\"", web.js("document.getElementById('feed') ? getComputedStyle(document.getElementById('ad')).display : 'loading'")) }
            tapRule(1)
            eventually { assertEquals("\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        assertEquals(4, rulesOf(tune).size)
        editorFor(tune).use {
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals("\"none\"", web.js("document.getElementById('feed') ? getComputedStyle(document.getElementById('ad')).display : 'loading'")) }
        }
    }

    private val colorPage = """<html><body style="background:#fff"><div id="box">BOX</div></body></html>"""

    private fun dialogView(id: Int): View = dialogRoot().let { root -> onMain { root.findViewById<View>(id) } }

    private fun sliders(): List<Int> = dialogRoot().let { root -> onMain { listOf(R.id.sb_red, R.id.sb_green, R.id.sb_blue).map { root.findViewById<SeekBar>(it).progress } } }

    private fun hexField(): String = dialogRoot().let { root -> onMain { root.findViewById<android.widget.EditText>(R.id.et_color_hex).text.toString() } }

    private fun okEnabled(): Boolean = dialogRoot().let { root -> onMain { root.rootView.shownViews().filterIsInstance<Button>().first { it.id == android.R.id.button1 }.isEnabled } }

    private fun colorPicker() = LocalPage(colorPage).use { page ->
        ActivityScenario.launch(PluginEditActivity::class.java).use {
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            pick(web, "box")
            tap(R.id.rb_action_color)

            tap(R.id.btn_color_bg)
            val density = targetContext.resources.displayMetrics.density
            for (hex in ColorPresets.ALL) {
                val root = dialogRoot()
                val swatch = onMain { root.findViewById<ViewGroup>(R.id.grid_color_presets).shownViews().first { it.contentDescription == hex } }
                assertEquals("$hex 견본 크기", (48 * density).toInt(), onMain { swatch.width })
            }
            onView(withContentDescription("#1976D2")).inRoot(isDialog()).perform(click())
            assertEquals("#1976D2", hexField())
            assertEquals(listOf(25, 118, 210), sliders())
            assertEquals(android.graphics.Color.parseColor("#1976D2"), previewColor())

            onView(withId(R.id.et_color_hex)).inRoot(isDialog()).perform(replaceText("1a2"))
            assertEquals(listOf(0x11, 0xAA, 0x22), sliders())
            assertEquals(android.graphics.Color.parseColor("#11AA22"), previewColor())

            dialogView(R.id.layout_color_advanced).let { v -> onMain { assertEquals(View.GONE, v.visibility) } }
            onView(withId(R.id.btn_color_advanced)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.sb_red)).inRoot(isDialog()).check(matches(isDisplayed())).perform(GeneralClickAction(Tap.SINGLE, rightEnd, Press.FINGER, 0, 0))
            eventually { assertEquals(255, sliders()[0]) }
            assertEquals("#FFAA22", hexField())

            onView(withId(R.id.et_color_hex)).inRoot(isDialog()).perform(replaceText("#12345"))
            dialogView(R.id.til_color_hex).let { v -> assertNotNull(onMain { (v as TextInputLayout).error }) }
            assertFalse(okEnabled())
            onView(withId(R.id.et_color_hex)).inRoot(isDialog()).perform(replaceText("1a2"))
            assertTrue(okEnabled())
            onView(withText(R.string.ok)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(targetContext.getString(R.string.edit_color_bg, "#11AA22"), textOf(R.id.btn_color_bg)) }

            pickColor(R.id.btn_color_text, "#FFFFFF")
            tap(R.id.btn_color_text)
            onView(withText(R.string.clear)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(targetContext.getString(R.string.edit_color_text, targetContext.getString(R.string.edit_color_none)), textOf(R.id.btn_color_text)) }

            tap(R.id.btn_add_action)
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Colors"))
            tap(R.id.btn_plugin_save)
        }
        val tune = eventually { runBlocking { database.pluginDao().getAllPlugins() }.single() }
        assertEquals(listOf("background-color:#11AA22"), rulesOf(tune.id).map { it.third })
    }

    private fun previewColor(): Int = dialogView(R.id.v_color_preview).let { v -> onMain { (v.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor } }

    private val rightEnd = CoordinatesProvider { v ->
        val xy = IntArray(2).also { v.getLocationOnScreen(it) }
        floatArrayOf(xy[0] + v.width - 1f, xy[1] + v.height / 2f)
    }

    private fun step(): String = textOf(R.id.tv_edit_step)

    private fun stepOne() = targetContext.getString(R.string.edit_step, 1, targetContext.getString(R.string.edit_step_address))

    private fun screenRect(id: Int): Rect = onMain {
        val v = resumedActivity().findViewById<View>(id)
        val xy = IntArray(2).also(v::getLocationOnScreen)
        Rect(xy[0], xy[1], xy[0] + v.width, xy[1] + v.height)
    }

    private val okPage = """<html><body style="background:#fff"><div id="box">BOX</div></body></html>"""

    private fun loadErrors() {
        val port = LocalPage(okPage).use { it.url.substringAfterLast(':').trimEnd('/').toInt() }
        val url = "http://127.0.0.1:$port/"
        ActivityScenario.launch(PluginEditActivity::class.java).use {
            onView(withId(R.id.et_inp_taddr)).perform(replaceText(url))
            onView(withId(R.id.btn_load_url)).perform(click())
            eventually(timeoutMs = 20_000) { onView(withId(R.id.layout_load_error)).check(matches(isDisplayed())) }
            // 오류 종류는 `spec_page_view.md` §6.10 분류가 정한다 → 여기서는 인라인 오류가 뜨는지만 본다.
            val titles = listOf(R.string.load_error_cannot_connect_title, R.string.load_error_no_response_title, R.string.load_error_other_title).map { targetContext.getString(it) }
            assertTrue(textOf(R.id.tv_load_error_title), textOf(R.id.tv_load_error_title) in titles)
            assertEquals(stepOne(), step())
            onView(withId(R.id.btn_load_retry)).check(matches(isDisplayed()))
            assertFalse("오류가 주소 칸을 가림", Rect.intersects(screenRect(R.id.layout_load_error), screenRect(R.id.til_inp_taddr)))
            assertFalse("오류가 시트를 가림", Rect.intersects(screenRect(R.id.layout_load_error), screenRect(R.id.sheet_handle)))

            LocalPage(okPage, port).use {
                onView(withId(R.id.btn_load_retry)).perform(click())
                val web = webView(R.id.wv_inspector)
                eventually { assertEquals("\"complete\"", web.js("document.getElementById('box') ? document.readyState : ''")) }
                eventually { onMain { assertEquals(View.GONE, resumedActivity().findViewById<View>(R.id.layout_load_error).visibility) } }

                inspectOn(web)
                pick(web, "box")
                eventually { assertEquals(targetContext.getString(R.string.edit_step, 3, targetContext.getString(R.string.edit_step_rule)), step()) }
                onView(withId(R.id.et_inp_taddr)).perform(replaceText("http://missing.invalid/"))
                onView(withId(R.id.btn_load_url)).perform(click())
                eventually(timeoutMs = 20_000) { onView(withId(R.id.layout_load_error)).check(matches(isDisplayed())) }
                eventually { assertEquals(stepOne(), step()) }
                onMain { assertTrue(resumedActivity().findViewById<View>(R.id.layout_action_config).visibility != View.VISIBLE) }
                eventually { assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheetState()) }
                assertNull(onMain { resumedActivity().findViewById<TextInputLayout>(R.id.til_inp_taddr).error })

                onView(withId(R.id.et_inp_taddr)).perform(replaceText(url))
                onView(withId(R.id.btn_load_url)).perform(click())
                eventually { assertEquals("\"complete\"", web.js("document.getElementById('box') ? document.readyState : ''")) }
                eventually { onMain { assertEquals(View.GONE, resumedActivity().findViewById<View>(R.id.layout_load_error).visibility) } }
            }
        }
    }
}
