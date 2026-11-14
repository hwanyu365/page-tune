package com.hwanyu365.pagetune

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hwanyu365.pagetune.ui.view.ColorPickerDialog
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayoutFitTest {

    private val problems = mutableListOf<String>()
    private val checked = mutableListOf<String>()
    private var condition = ""

    @Before
    fun setUp() {
        resetApp()
    }

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc210")
    fun `TC#210 모든 화면이 영어·한국어 글꼴 100%·200% 에서 잘리거나 겹치지 않는다`() {
        val oldScale = shell("settings get system font_scale").trim()
        changeDeviceSetting(
            "settings put system font_scale 1.0",
            restore = if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale",
        )
        val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
        changeDeviceSetting("$locales --locales en", restore = locales)
        LocalPage("<html><body style=\"background:#fff\"><p>page</p></body></html>").use { page ->
            for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                waitUntil { Resources.getSystem().configuration.fontScale == scale.toFloat() }
                for (tag in listOf("en", "ko")) {
                    shell("$locales --locales $tag")
                    waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
                    condition = "$tag ${scale}x"
                    checkAllScreens(page.url)
                }
            }
        }
        assertEquals(checked.toString(), 4 * SCREENS, checked.size)
        assertEquals(emptyList<String>(), problems)
    }

    private fun checkAllScreens(pageUrl: String) {
        clearData()
        targetContext.getSharedPreferences("intro", Context.MODE_PRIVATE).edit(commit = true) { clear() }
        ActivityScenario.launch(MainActivity::class.java).use {
            check(eventually { dialogRoot() }, "intro")
            androidx.test.espresso.Espresso.pressBack()
            eventually { onView(withId(R.id.layout_empty)).check(matches(isDisplayed())) }
            checkActivity(it, "pages_empty")
            onView(tab(R.string.tab_tunes)).perform(click())
            eventually { onView(allOf(withId(R.id.layout_empty), isDisplayed())).check(matches(isDisplayed())) }
            checkActivity(it, "tunes_empty")
        }

        markIntroSeen()
        val tune = addTune("Tune A", listOf(clickRule("gnb_prev", "#prev"), clickRule("gnb_next", "#next")))
        val pageId = addWebPage("Page A", pageUrl, tune)
        addWebPage("Page B", "https://b.example/")
        ActivityScenario.launch(MainActivity::class.java).use {
            eventually { onView(withText("Page A")).check(matches(isDisplayed())) }
            checkActivity(it, "pages")
            onView(withId(R.id.fab_add_webpage)).perform(click())
            check(dialogRoot(), "webpage_dialog")
            androidx.test.espresso.Espresso.pressBack()
            onView(tab(R.string.tab_tunes)).perform(click())
            eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Tune A"))).check(matches(isDisplayed())) }
            checkActivity(it, "tunes")
        }

        ActivityScenario.launch(PluginEditActivity::class.java).use {
            checkActivity(it, "edit")
            onView(withId(R.id.sheet_handle)).perform(click())
            onView(withId(R.id.btn_plugin_save)).check(matches(isDisplayed()))
            checkActivity(it, "edit_sheet")
            it.onActivity { activity -> ColorPickerDialog.show(activity, activity.getString(R.string.app_name), null) {} }
            check(dialogRoot(), "color_picker")
        }

        val viewer = Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId)
        ActivityScenario.launch<WebBrowserActivity>(viewer).use {
            eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
            eventually { onView(withId(R.id.tv_tune_status)).check(matches(isDisplayed())) }
            onView(withId(R.id.btn_quick_handle)).perform(click())
            onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
            checkActivity(it, "viewer_overlay")
        }

        ActivityScenario.launch(SettingsActivity::class.java).use { checkActivity(it, "settings") }
    }

    private fun tab(text: Int) = allOf(withText(text), isDescendantOfA(withId(R.id.tab_layout)))

    private fun <A : Activity> checkActivity(scenario: ActivityScenario<A>, screen: String) {
        var root: View? = null
        scenario.onActivity { root = it.window.decorView }
        check(root!!, screen)
    }

    private fun check(root: View, screen: String) {
        checked += "$condition $screen"
        problems += layoutProblemsOnMain(root, "$condition $screen")
    }

    private companion object {
        const val SCREENS = 11
    }
}
