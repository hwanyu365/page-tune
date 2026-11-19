package com.hwanyu365.pagetune

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ScrollView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBarsTest {

    private val problems = mutableListOf<String>()
    private val checked = mutableListOf<String>()

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc507")
    fun `TC#507 툴바와 버튼은 시스템 바에 가려지지 않고 시스템 바 아이콘은 밝다`() = checkAllScreens()

    private fun checkAllScreens() {
        LocalPage("<html><body style=\"background:#fff\"><p>page</p></body></html>").use { page ->
            val tune = addTune("Tune A", listOf(clickRule("gnb_prev", "#prev"), clickRule("gnb_next", "#next")))
            val pageId = addWebPage("Page A", page.url, tune)
            ActivityScenario.launch(MainActivity::class.java).use {
                eventually { onView(withText("Page A")).check(matches(isDisplayed())) }
                check(it, "pages", bars)
                onView(allOf(withText(R.string.tab_tunes), isDescendantOfA(withId(R.id.tab_layout)))).perform(click())
                eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Tune A"))).check(matches(isDisplayed())) }
                check(it, "tunes", bars)
            }
            ActivityScenario.launch(PluginEditActivity::class.java).use {
                check(it, "edit", bars)
                onView(withId(R.id.sheet_handle)).perform(click())
                onView(withId(R.id.btn_plugin_save)).check(matches(isDisplayed()))
                check(it, "edit_sheet", bars)
            }
            ActivityScenario.launch(SettingsActivity::class.java).use { check(it, "settings", bars) }
            val viewer = Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId)
            ActivityScenario.launch<WebBrowserActivity>(viewer).use {
                eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
                tapQuickHandle()
                onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
                // 열람 화면은 상태바를 숨기고 노치까지 쓰는 전체화면이다 → 내비게이션 바만 피한다.
                check(it, "viewer", Type.navigationBars())
            }
        }
        assertEquals(listOf("pages", "tunes", "edit", "edit_sheet", "settings", "viewer"), checked)
        assertEquals(emptyList<String>(), problems)
    }

    private val bars = Type.systemBars() or Type.displayCutout()

    private fun <A : Activity> check(scenario: ActivityScenario<A>, screen: String, avoid: Int) {
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)!!.getInsets(avoid)
            val controller = WindowCompat.getInsetsController(activity.window, decor)
            if (controller.isAppearanceLightStatusBars) problems += "$screen 상태바 아이콘이 어두움"
            if (controller.isAppearanceLightNavigationBars) problems += "$screen 내비게이션 바 아이콘이 어두움"
            val window = Rect(0, 0, decor.width, decor.height)
            val safe = Rect(insets.left, insets.top, decor.width - insets.right, decor.height - insets.bottom)
            var targets = 0
            for (v in decor.shownViews().filter(::isBarTarget)) {
                val at = IntArray(2).also(v::getLocationInWindow)
                val rect = Rect(at[0], at[1], at[0] + v.width, at[1] + v.height)
                // 화면 밖으로 밀려난 칸(접힌 시트 아래 등)은 보이지 않으므로 가려질 것도 없다.
                if (rect.isEmpty || !Rect.intersects(rect, window)) continue
                targets++
                if (!safe.contains(rect)) problems += "$screen ${name(v)} $rect 가 안전 영역 $safe 밖"
            }
            if (targets == 0) problems += "$screen 확인한 툴바·버튼 없음"
        }
        checked += screen
    }

    // 목록·스크롤 안의 칸은 스크롤해서 시스템 바 밖으로 꺼낼 수 있다 → 고정된 툴바·버튼만 본다.
    private fun isBarTarget(v: View): Boolean {
        if (v !is Toolbar && v !is Button && v !is ImageButton && v !is CompoundButton && v !is FloatingActionButton) return false
        var p = v.parent
        while (p is View) {
            if (p is RecyclerView || p is ScrollView || p is NestedScrollView || p is HorizontalScrollView || p is WebView) return false
            p = p.parent
        }
        return true
    }

    private fun name(v: View) = if (v.id != View.NO_ID) runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault(v.javaClass.simpleName) else v.javaClass.simpleName
}
