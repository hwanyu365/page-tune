package com.hwanyu365.pagetune

import android.app.Activity
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher

val pagesTab: Matcher<View> = allOf(withText(R.string.tab_webpages), isDescendantOfA(withId(R.id.tab_layout)))
val tunesTab: Matcher<View> = allOf(withText(R.string.tab_tunes), isDescendantOfA(withId(R.id.tab_layout)))

fun inRowWith(text: String): Matcher<View> = inRowWith(withText(text))

fun inRowWith(content: Matcher<View>): Matcher<View> = object : TypeSafeMatcher<View>() {
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

fun openItemMenu(item: Matcher<View>) = openMenu(allOf(withId(R.id.btn_more), inRowWith(item), isDisplayed()))

// 목록을 다시 그리는 순간에 누른 클릭은 메뉴를 열지 못한다 → 메뉴 창이 포커스를 가져갈 때까지 다시 누른다.
fun openMenu(button: Matcher<View>) = eventually {
    onView(button).perform(click())
    waitUntil(timeoutMs = 2_000) { !onMain { resumedActivity().window.decorView.hasWindowFocus() } }
}

fun deleteFromItemMenu(item: Matcher<View>) {
    openItemMenu(item)
    onView(withText(R.string.delete)).inRoot(isPlatformPopup()).perform(click())
    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
}

fun snackbarGone(): Boolean = runCatching {
    onView(withId(com.google.android.material.R.id.snackbar_text)).check(doesNotExist())
}.isSuccess

/** 화면에 보이는 목록 줄의 이름을 위에서부터 읽는다. */
fun shownNames(list: Int, name: Int): List<String> = onMain {
    val rv = resumedActivity().findViewById<RecyclerView>(list)
    (0 until rv.childCount).map { rv.getChildAt(it) }.sortedBy { it.top }
        .mapNotNull { row -> row.findViewById<android.widget.TextView>(name)?.text?.toString() }
}

// 줄을 바꾸는 동안에는 변경 애니메이션 때문에 같은 줄이 둘 있다 → 하나로 정해질 때 누른다.
fun tapOnce(view: Matcher<View>) = eventually { onView(view).perform(click()) }

fun assertSelectedCount(count: Int) {
    org.junit.Assert.assertEquals(count.toString(), onMain { selectionTitle() })
}

private fun selectionTitle(): String {
    // 선택 수는 ActionMode 상단 바의 제목이다.
    val bar = resumedActivity().window.decorView.shownViews().first { it.javaClass.simpleName == "ActionBarContextView" }
    return bar.shownViews().filterIsInstance<android.widget.TextView>().first { it.id == androidx.appcompat.R.id.action_bar_title }.text.toString()
}

fun resumedActivity(): Activity {
    val find = { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single() }
    return if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) find() else onMain(find)
}

fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}
