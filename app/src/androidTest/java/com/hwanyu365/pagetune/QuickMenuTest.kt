package com.hwanyu365.pagetune

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickMenuTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc519")
    fun `TC#519 빠른 메뉴 핸들은 조작이 없으면 흐려지고 아래로 스크롤하는 동안 숨으며 툴바는 버튼 폭의 왼쪽 반원 띠다`() = quietHandle()

    private val longPage = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;background:#fff"><p>top</p><div style="height:20000px"></div></body></html>
    """.trimIndent()

    private fun quietHandle() = LocalPage(longPage).use { server ->
        val pageId = addWebPage("Page", server.url)
        ActivityScenario.launch<WebBrowserActivity>(Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId)).use { scenario ->
            val web = webView(R.id.wv_browser)
            eventually { assertEquals("\"complete\"", web.js("document.body ? document.readyState : ''")) }
            val wait = timeoutMs()
            val before = onMain { handleState() }
            assertEquals(1f, before.alpha)
            SystemClock.sleep(wait / 2)
            assertEquals("자동 숨김 시간 전에 흐려짐", 1f, onMain { handleState() }.alpha)

            eventually(timeoutMs = wait + 3_000) { assertEquals(DIM, onMain { handleState() }.alpha) }
            val dimmed = onMain { handleState() }
            assertEquals("흐려지며 위치·크기가 바뀜", before.rect, dimmed.rect)
            assertEquals(before.label, dimmed.label)
            assertEquals(View.VISIBLE, dimmed.visibility)

            tapQuickHandle()
            onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
            assertEquals(1f, onMain { handleState() }.alpha)
            checkToolbarShape()

            scrollDown(web, 1_000) { assertEquals("툴바가 열린 동안 핸들이 숨음", View.VISIBLE, onMain { handleState() }.visibility) }
            SystemClock.sleep(wait / 2)
            onView(withId(R.id.btn_quick_refresh)).perform(click())
            SystemClock.sleep(wait * 3 / 4)
            onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
            assertEquals("툴바가 열린 동안 핸들이 흐려짐", 1f, onMain { handleState() }.alpha)
            eventually(timeoutMs = wait) { onView(withId(R.id.layout_quick_toolbar)).check(matches(not(isDisplayed()))) }
            assertEquals("툴바가 자동으로 숨은 뒤 핸들이 진하지 않음", 1f, onMain { handleState() }.alpha)
            eventually(timeoutMs = wait + 3_000) { assertEquals("툴바가 숨은 뒤 다시 흐려지지 않음", DIM, onMain { handleState() }.alpha) }

            var hiddenWhileScrolling = false
            scrollDown(web, 1_500) { if (onMain { handleState() }.visibility != View.VISIBLE) hiddenWhileScrolling = true }
            assertTrue("아래로 스크롤하는 동안 핸들이 숨지 않음", hiddenWhileScrolling)
            eventually(timeoutMs = 2_000) { assertEquals("스크롤이 멈춰도 핸들이 다시 보이지 않음", View.VISIBLE, onMain { handleState() }.visibility) }
            assertEquals("다시 보인 핸들이 흐리지 않음", DIM, onMain { handleState() }.alpha)

            scrollDown(web, 600) {}
            assertTrue(onMain { handleState() }.visibility != View.VISIBLE)
            web.js("window.scrollBy(0, -200)")
            eventually(timeoutMs = 150) { assertEquals("위로 스크롤해도 핸들이 보이지 않음", View.VISIBLE, onMain { handleState() }.visibility) }

            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertEquals("화면에 돌아와도 핸들이 진하지 않음", 1f, onMain { handleState() }.alpha)
            web.js("window.scrollBy(0, -100)")
            SystemClock.sleep(300)
            assertEquals("보이던 핸들이 위로 스크롤하자 흐려짐", 1f, onMain { handleState() }.alpha)
            scrollDown(web, 600) {}
            eventually(timeoutMs = 2_000) { assertEquals(View.VISIBLE, onMain { handleState() }.visibility) }
            assertEquals("진하던 핸들이 스크롤 뒤 흐리게 나타나지 않음", DIM, onMain { handleState() }.alpha)
        }
    }

    private data class HandleState(val alpha: Float, val rect: Rect, val label: String, val visibility: Int)

    private fun handleState(): HandleState {
        val handle = resumedActivity().findViewById<View>(R.id.btn_quick_handle)
        val rect = IntArray(2).also { handle.getLocationOnScreen(it) }.let { Rect(it[0], it[1], it[0] + handle.width, it[1] + handle.height) }
        return HandleState(handle.alpha, rect, handle.contentDescription.toString(), handle.visibility)
    }

    private fun checkToolbarShape() = onMain {
        val activity = resumedActivity()
        val toolbar = activity.findViewById<View>(R.id.layout_quick_toolbar)
        val handle = activity.findViewById<View>(R.id.btn_quick_handle)
        val t = IntArray(2).also { toolbar.getLocationOnScreen(it) }
        val h = IntArray(2).also { handle.getLocationOnScreen(it) }
        val screen = activity.window.decorView.width
        val density = activity.resources.displayMetrics.density
        assertTrue("툴바가 화면 폭을 다 씀: ${toolbar.width}/$screen", toolbar.width < screen)
        val buttons = listOf(R.id.btn_quick_back, R.id.btn_quick_refresh, R.id.btn_quick_share, R.id.btn_quick_toggle, R.id.btn_quick_edit)
            .sumOf { activity.findViewById<View>(it).width }
        assertEquals("툴바 폭이 버튼 폭 + 핸들 자리가 아님", (buttons + 52 * density), toolbar.width.toFloat(), 2f)
        val overlay = toolbar.parent as View
        val o = IntArray(2).also { overlay.getLocationOnScreen(it) }
        assertEquals("툴바가 오른쪽 끝에 붙지 않음", o[0] + overlay.width - overlay.paddingRight, t[0] + toolbar.width)
        assertEquals("접근성 탐색에서 핸들이 툴바보다 뒤", R.id.layout_quick_toolbar, handle.accessibilityTraversalBefore)
        assertTrue("툴바가 핸들을 감싸지 않음", t[0] < h[0] && t[0] + toolbar.width >= h[0] + handle.width)
        assertEquals("툴바와 핸들 위 끝이 다름", t[1].toFloat(), h[1].toFloat(), 4 * density)
        val radii = (toolbar.background as GradientDrawable).cornerRadii!!
        val half = toolbar.height / 2f
        assertTrue("왼쪽 모서리가 반원이 아님: ${radii.toList()}", radii[0] >= half - 1 && radii[1] >= half - 1 && radii[6] >= half - 1 && radii[7] >= half - 1)
        assertTrue("오른쪽 모서리가 둥금: ${radii.toList()}", radii.slice(2..5).all { it == 0f })
    }

    // 스크롤 이벤트가 끊기지 않도록 짧은 간격으로 계속 내린다.
    private fun scrollDown(web: WebView, durationMs: Long, during: () -> Unit) {
        val end = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < end) {
            web.js("window.scrollBy(0, 30)")
            during()
            SystemClock.sleep(40)
        }
    }

    private fun timeoutMs(): Long {
        val base = 4_000
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return base.toLong()
        return targetContext.getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(base, AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong()
    }

    companion object {
        const val DIM = 0.4f
    }
}
