package com.hwanyu365.pagetune

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Before
    fun setUp() = markIntroSeen()

    // DEX 는 메서드 이름에 '#'·공백을 받지 않는다 → 소스 이름은 TC 규칙대로 두고 바이트코드 이름만 바꾼다.
    @Test
    @JvmName("tc516")
    fun `TC#516 메인 화면에 Pages·Tunes 탭이 보이고 전환된다`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val pages = allOf(withText(R.string.tab_webpages), isDescendantOfA(withId(R.id.tab_layout)))
            val tunes = allOf(withText(R.string.tab_tunes), isDescendantOfA(withId(R.id.tab_layout)))
            onView(pages).check(matches(isDisplayed()))
            onView(tunes).check(matches(isDisplayed())).perform(click())
            onView(tunes).check(matches(isSelected()))
        }
    }
}
