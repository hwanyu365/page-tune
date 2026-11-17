package com.hwanyu365.pagetune

import android.content.Context
import android.content.res.Resources
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hwanyu365.pagetune.ui.WHATS_NEW_NOTES
import com.hwanyu365.pagetune.util.versionNameOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntroTest {

    @Before
    fun setUp() = resetApp()

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc137")
    fun `TC#137 새 설치의 시작 안내는 3장 이하이고 건너뛰기·시작하기·뒤로가기로 닫으면 다시 보이지 않으며 닫기 전에 끝나면 다시 보인다`() = gettingStarted()

    @Test
    @JvmName("tc151")
    fun `TC#151 이전 버전 사용자는 시작 안내 없이 새 소식만 보고 새 설치는 시작 안내만 본다`() = whatsNew()

    private fun gettingStarted() {
        freshInstall()
        launchMain().use {
            walkPages()
            onView(withText(R.string.intro_start)).inRoot(isDialog()).perform(click())
            assertNoDialog()
        }
        assertEquals(currentCode, recorded())
        launchMain().use { assertNoDialog() }

        for (close in listOf(::skip, { pressBack() })) {
            freshInstall()
            launchMain().use {
                shownTitle(R.string.intro_1_title)
                close()
                assertNoDialog()
            }
            assertEquals(currentCode, recorded())
            launchMain().use { assertNoDialog() }
        }

        freshInstall()
        launchMain().use {
            shownTitle(R.string.intro_1_title)
            onView(withText(R.string.intro_next)).inRoot(isDialog()).perform(click())
            shownTitle(R.string.intro_2_title)
        }
        assertNull(recorded())
        launchMain().use { shownTitle(R.string.intro_1_title) }

        readableInLocales()
    }

    private fun skip() {
        onView(withText(R.string.intro_skip)).inRoot(isDialog()).perform(click())
    }

    private fun walkPages() {
        val titles = listOf(R.string.intro_1_title, R.string.intro_2_title, R.string.intro_3_title)
        for ((index, title) in titles.withIndex()) {
            shownTitle(title)
            onView(withText(R.string.intro_skip)).inRoot(isDialog()).check(matches(isDisplayed()))
            val last = index == titles.lastIndex
            onView(withText(if (last) R.string.intro_start else R.string.intro_next)).inRoot(isDialog()).check(matches(isDisplayed()))
            if (!last) onView(withText(R.string.intro_next)).inRoot(isDialog()).perform(click())
        }
    }

    private fun readableInLocales() {
        val oldScale = shell("settings get system font_scale").trim()
        changeDeviceSetting("settings put system font_scale 2.0", restore = if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale")
        waitUntil { Resources.getSystem().configuration.fontScale == 2.0f }
        val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
        changeDeviceSetting("$locales --locales en", restore = locales)
        val firstTitles = mutableListOf<String>()
        for (tag in listOf("en", "ko")) {
            shell("$locales --locales $tag")
            waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
            freshInstall()
            launchMain().use {
                val pages = listOf(R.string.intro_1_title to R.string.intro_1_body, R.string.intro_2_title to R.string.intro_2_body, R.string.intro_3_title to R.string.intro_3_body)
                for ((index, page) in pages.withIndex()) {
                    shownPage(page.first, page.second)
                    val root = eventually { dialogRoot() }
                    assertEquals("$tag 장 ${index + 1}", emptyList<String>(), layoutProblemsOnMain(root, "intro $tag ${index + 1}"))
                    if (index == 0) firstTitles += onMain { root.findViewById<TextView>(androidx.appcompat.R.id.alertTitle).text.toString() }
                    if (index < pages.lastIndex) onView(withText(R.string.intro_next)).inRoot(isDialog()).perform(click())
                }
            }
        }
        assertNotEquals("영어·한국어 제목이 같음", firstTitles[0], firstTitles[1])
    }

    private fun whatsNew() {
        if (currentCode == 1) devBuild() else taggedBuild()
    }

    // 태그 없는 개발 빌드는 versionCode 가 1 이라 어떤 새 소식도 보이지 않고 기록만 바로 바뀐다 (spec_page_view.md §6.13).
    private fun devBuild() {
        freshInstall()
        addWebPage("One", "https://one.example/")
        addWebPage("Two", "https://two.example/")
        launchMain().use {
            eventually { assertEquals(1, recorded()) }
            assertNoDialog()
        }
    }

    private fun taggedBuild() {
        val expected = WHATS_NEW_NOTES.map { it.first }.filter { it in (PREVIOUS + 1)..currentCode }.sortedDescending()
        assertTrue("현재 버전 $currentCode 까지 보일 새 소식이 없음", expected.isNotEmpty())
        val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
        changeDeviceSetting("$locales --locales en", restore = locales)
        val messages = mutableListOf<String>()
        for (tag in listOf("en", "ko")) {
            shell("$locales --locales $tag")
            waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
            updatedUser()
            launchMain().use {
                messages += shownWhatsNew(expected)
                onView(withText(R.string.ok)).inRoot(isDialog()).perform(click())
                assertNoDialog()
            }
            assertEquals(currentCode, recorded())
            launchMain().use { assertNoDialog() }
        }
        assertNotEquals("영어·한국어 새 소식이 같음", messages[0], messages[1])

        updatedUser()
        launchMain().use { shownWhatsNew(expected) }
        assertEquals(PREVIOUS, recorded())
        launchMain().use {
            shownWhatsNew(expected)
            onView(withText(R.string.ok)).inRoot(isDialog()).perform(click())
        }

        freshInstall()
        launchMain().use {
            walkPages()
            onView(withText(R.string.intro_start)).inRoot(isDialog()).perform(click())
            assertNoDialog()
        }
        launchMain().use { assertNoDialog() }
    }

    private fun updatedUser() {
        freshInstall()
        addWebPage("One", "https://one.example/")
        addWebPage("Two", "https://two.example/")
        prefs().edit(commit = true) { putInt(KEY, PREVIOUS) }
    }

    private fun shownWhatsNew(versions: List<Int>): String = eventually {
        val root = dialogRoot()
        onMain {
            val context = resumedActivity()
            assertEquals(context.getString(R.string.whats_new_title), root.findViewById<TextView>(androidx.appcompat.R.id.alertTitle).text.toString())
            val message = root.findViewById<TextView>(android.R.id.message).text.toString()
            val notes = WHATS_NEW_NOTES.toMap()
            val expected = versions.joinToString("\n\n") { code ->
                context.getString(R.string.whats_new_version, versionNameOf(code)) + "\n" + context.getString(notes.getValue(code))
            }
            assertEquals(expected, message)
            assertFalse("시작 안내 버튼이 보임", root.findViewById<Button?>(android.R.id.button2)?.isShown == true)
            message
        }
    }

    // 다이얼로그의 context 는 앱 언어를 따른다 → 그 context 로 읽은 값과 같으면 그 언어의 문구다.
    private fun shownPage(title: Int, body: Int) = eventually {
        val root = dialogRoot()
        onMain {
            assertEquals(root.context.getString(title), root.findViewById<TextView>(androidx.appcompat.R.id.alertTitle).text.toString())
            assertEquals(root.context.getString(body), root.findViewById<TextView>(android.R.id.message).text.toString())
            for ((id, text) in listOf(android.R.id.button2 to R.string.intro_skip)) assertEquals(root.context.getString(text), root.findViewById<Button>(id).text.toString())
        }
    }

    private fun shownTitle(title: Int) = eventually {
        val root = dialogRoot()
        onMain { assertEquals(root.context.getString(title), root.findViewById<TextView>(androidx.appcompat.R.id.alertTitle).text.toString()) }
    }

    // Espresso 는 대화상자 창이 없으면 한참 기다린 뒤에야 실패한다 → 창이 없는 것은 화면 창이 포커스를 가졌는지로 본다.
    private fun assertNoDialog() {
        eventually { onView(withId(R.id.tab_layout)).check(matches(isDisplayed())) }
        android.os.SystemClock.sleep(1_000)
        Espresso.onIdle()
        assertTrue("대화상자가 떠 있음", onMain { resumedActivity().window.decorView.hasWindowFocus() })
    }

    private fun launchMain(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

    private fun freshInstall() {
        clearData()
        prefs().edit(commit = true) { clear() }
    }

    private fun prefs() = targetContext.getSharedPreferences("intro", Context.MODE_PRIVATE)

    private fun recorded(): Int? = prefs().let { if (it.contains(KEY)) it.getInt(KEY, 0) else null }

    private val currentCode: Int
        get() = PackageInfoCompat.getLongVersionCode(targetContext.packageManager.getPackageInfo(targetContext.packageName, 0)).toInt()

    private companion object {
        const val KEY = "last_seen_version_code"
        const val PREVIOUS = 10101
    }
}
