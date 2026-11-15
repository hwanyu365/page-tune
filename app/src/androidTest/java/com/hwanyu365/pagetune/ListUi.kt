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

fun openItemMenu(item: Matcher<View>) {
    onView(allOf(withId(R.id.btn_more), inRowWith(item), isDisplayed())).perform(click())
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

fun resumedActivity(): Activity {
    val find = { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single() }
    return if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) find() else onMain(find)
}

fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}
