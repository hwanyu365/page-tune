package com.hwanyu365.pagetune

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabsTest {

    private val pagesTab = allOf(withText(R.string.tab_webpages), isDescendantOfA(withId(R.id.tab_layout)))
    private val tunesTab = allOf(withText(R.string.tab_tunes), isDescendantOfA(withId(R.id.tab_layout)))
    private val tuneRow = allOf(withId(R.id.tv_plugin_name), withText("Tune A"))

    @Before
    fun setUp() {
        resetApp()
        val tune = addTune("Tune A")
        addWebPage("Page A", "https://a.example/", tune)
        addWebPage("Page B", "https://b.example/")
    }

    @Test
    @JvmName("tc209a")
    fun `TC#209 탭은 누르기·밀기로 전환되고 재생성 뒤에도 보던 탭이 유지된다`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(pagesTab).check(matches(isSelected()))
            onView(tunesTab).perform(click()).check(matches(isSelected()))
            onView(withId(R.id.view_pager)).perform(swipeRight())
            onView(pagesTab).check(matches(isSelected()))
            onView(withId(R.id.view_pager)).perform(swipeLeft())
            onView(tunesTab).check(matches(isSelected()))
            eventually { onView(tuneRow).check(matches(isDisplayed())) }

            scenario.recreate()
            onView(tunesTab).check(matches(isSelected()))
            eventually { onView(tuneRow).check(matches(isDisplayed())) }
        }
    }

    @Test
    @JvmName("tc209b")
    fun `TC#209 툴바 삭제는 보이는 탭에만 작용하고 탭을 바꾸면 선택 모드가 닫힌다`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            eventually { onView(withText("Page B")).check(matches(isDisplayed())) }
            onView(withId(R.id.action_delete_mode)).perform(click())
            onView(withId(R.id.action_delete_selected)).check(matches(isDisplayed()))
            eventually { assertEquals(2, shownCheckboxes(scenario, R.id.rv_webpages)) }
            eventually { assertEquals(0, shownCheckboxes(scenario, R.id.rv_plugins)) }

            onView(withId(R.id.view_pager)).perform(swipeLeft())
            onView(tunesTab).check(matches(isSelected()))
            eventually { onView(withId(R.id.action_delete_selected)).check(doesNotExist()) }
            eventually { assertEquals(0, shownCheckboxes(scenario, R.id.rv_webpages)) }

            onView(withId(R.id.action_delete_mode)).perform(click())
            eventually { assertEquals(1, shownCheckboxes(scenario, R.id.rv_plugins)) }
            eventually { assertEquals(0, shownCheckboxes(scenario, R.id.rv_webpages)) }

            onView(withId(R.id.view_pager)).perform(swipeRight())
            eventually { onView(withId(R.id.action_delete_selected)).check(doesNotExist()) }
            eventually { assertEquals(0, shownCheckboxes(scenario, R.id.rv_plugins)) }

            onView(withText("Page B")).perform(longClick())
            onView(withId(R.id.action_delete_selected)).check(matches(isDisplayed()))
            onView(withId(R.id.view_pager)).perform(swipeLeft())
            eventually { onView(withId(R.id.action_delete_selected)).check(doesNotExist()) }
        }
    }

    @Test
    @JvmName("tc209c")
    fun `TC#209 탭을 바꾸면 되돌리기 창이 닫히고 지운 Tune 의 연결 표시가 갱신된다`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(pagesTab).perform(click())
            eventually { onView(withText("Page B")).check(matches(isDisplayed())) }
            deleteFromItemMenu(withText("Page B"))
            onView(withId(com.google.android.material.R.id.snackbar_text)).check(matches(isDisplayed()))
            onView(withId(R.id.view_pager)).perform(swipeLeft())
            // 되돌리기 창은 5초 뒤 저절로 닫힌다 → 그보다 짧게 기다려야 탭 전환이 닫았는지 가려진다.
            waitUntil(timeoutMs = 1_500) { snackbarGone() }

            eventually { onView(tuneRow).check(matches(isDisplayed())) }
            deleteFromItemMenu(tuneRow)
            onView(withId(R.id.view_pager)).perform(swipeRight())
            onView(pagesTab).check(matches(isSelected()))
            eventually {
                onView(allOf(withId(R.id.tv_plugin), inRowWith("Page A"))).check(matches(withText(R.string.webpage_item_tune_none)))
            }
        }
    }

    private fun inRowWith(text: String): Matcher<View> = inRowWith(withText(text))

    private fun inRowWith(content: Matcher<View>): Matcher<View> = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("목록 줄 안: ").appendDescriptionOf(content)
        }

        override fun matchesSafely(view: View): Boolean {
            var row: View = view
            while (true) {
                val parent = row.parent as? View ?: return false
                if (parent is RecyclerView) return row.shownViews().any { content.matches(it) }
                row = parent
            }
        }
    }

    private fun deleteFromItemMenu(item: Matcher<View>) {
        onView(allOf(withId(R.id.btn_more), inRowWith(item), isDisplayed())).perform(click())
        onView(withText(R.string.delete)).inRoot(isPlatformPopup()).perform(click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    private fun snackbarGone(): Boolean = runCatching {
        onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist())
    }.isSuccess

    private fun shownCheckboxes(scenario: ActivityScenario<MainActivity>, list: Int): Int {
        var count = 0
        scenario.onActivity { activity ->
            val rv = activity.findViewById<RecyclerView?>(list) ?: return@onActivity
            count = rv.shownViews().count { it.id == R.id.chk_delete }
        }
        return count
    }
}
