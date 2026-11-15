package com.hwanyu365.pagetune

import android.app.Instrumentation
import android.content.Intent
import android.content.res.Resources
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val privacyUrl get() = targetContext.getString(R.string.privacy_policy_url)
    private val component = Regex("""^\s*([\w.]+)/[\w.$]+$""")

    @Before
    fun setUp() {
        resetApp()
    }

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc301")
    fun `TC#301 오버플로의 설정으로 들어가고 위로·뒤로가기로 메인에 돌아온다`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            for (leave in listOf({ onView(withContentDescription(R.string.quick_back)).perform(click()) }, { pressBack() })) {
                openActionBarOverflowOrOptionsMenu(targetContext)
                onView(withText(R.string.menu_licenses)).check(doesNotExist())
                onView(withText(R.string.settings)).perform(click())
                onView(withId(R.id.item_privacy)).check(matches(isDisplayed()))
                leave()
                onView(withId(R.id.tab_layout)).check(matches(isDisplayed()))
            }
        }
    }

    @Test
    @JvmName("tc302")
    fun `TC#302 앱 정보는 설치된 버전을 보이고 라이선스는 원문을 보인다`() {
        val version = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).versionName
        assertFalse(version.isNullOrBlank())
        val licenses = targetContext.resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use { it.readText() }
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.tv_app_version)).check(matches(withText(allOf(containsString("PageTune"), containsString(version!!)))))
            onView(withId(R.id.item_licenses)).perform(click())
            onView(withId(android.R.id.message)).check(matches(withText(licenses)))
        }
    }

    @Test
    @JvmName("tc303a")
    fun `TC#303 처리방침은 처리방침 주소를 브라우저 intent 로 보낸다`() {
        val monitor = ViewIntentMonitor()
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withId(R.id.item_privacy)).perform(click())
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        val sent = monitor.sent.toList()
        assertEquals(1, sent.size)
        assertEquals(privacyUrl, sent[0].dataString)
        assertTrue(sent[0].categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    @JvmName("tc303b")
    fun `TC#303 처리방침을 열 앱이 없으면 안내와 주소를 보인다`() {
        val query = "cmd package query-activities --brief -a ${Intent.ACTION_VIEW} -c ${Intent.CATEGORY_BROWSABLE} -d $privacyUrl"
        val handlers = handlers(query)
        assertTrue("처리방침 주소를 받는 앱이 없는 기기는 비활성화 전후를 비교할 수 없다", handlers.isNotEmpty())
        assertFalse(targetContext.packageName in handlers)
        handlers.forEach { changeDeviceSetting("pm disable-user --user 0 $it", restore = "pm enable --user 0 $it") }
        assertEquals(emptyList<String>(), handlers(query))
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.item_privacy)).perform(click())
            onView(withText(targetContext.getString(R.string.settings_no_browser, privacyUrl))).check(matches(isDisplayed()))
        }
    }

    @Test
    @JvmName("tc305")
    fun `TC#305 영어·한국어 글꼴 200% 에서 세 항목이 잘리지 않고 48dp 이상이다`() {
        val oldScale = shell("settings get system font_scale").trim()
        changeDeviceSetting(
            "settings put system font_scale 2.0",
            restore = if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale",
        )
        waitUntil { Resources.getSystem().configuration.fontScale == 2.0f }
        for (tag in listOf("en", "ko")) {
            val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
            changeDeviceSetting("$locales --locales $tag", restore = locales)
            waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                onView(withId(R.id.item_privacy)).check(matches(isDisplayed()))
                scenario.onActivity { activity ->
                    val config = activity.resources.configuration
                    assertEquals(2.0f, config.fontScale)
                    assertEquals(tag, config.locales[0].language)
                    val minHeight = 48 * activity.resources.displayMetrics.density
                    for (id in listOf(R.id.item_app_info, R.id.item_licenses, R.id.item_privacy)) {
                        val item = activity.findViewById<ViewGroup>(id)
                        val name = "$tag ${activity.resources.getResourceEntryName(id)}"
                        assertTrue("$name 높이 ${item.height}", item.height >= minHeight)
                        for (text in item.children.filterIsInstance<TextView>()) {
                            val layout = text.layout
                            assertTrue("$name 글자 없음", text.text.isNotEmpty() && layout != null)
                            for (line in 0 until layout.lineCount) assertEquals("$name 말줄임", 0, layout.getEllipsisCount(line))
                            assertTrue("$name 세로 잘림", layout.height <= text.height - text.totalPaddingTop - text.totalPaddingBottom)
                            assertTrue("$name 항목 밖", text.top >= 0 && text.bottom <= item.height && text.right <= item.width)
                        }
                    }
                }
            }
        }
    }

    private fun handlers(query: String) = shell(query).lines().mapNotNull { component.find(it)?.groupValues?.get(1) }.distinct()
}

// 인자 없는 monitor 만 onStartActivity 로 모든 intent 를 받는다 → 다른 앱을 띄우지 않고 보낸 intent 를 본다.
private class ViewIntentMonitor : Instrumentation.ActivityMonitor() {
    val sent: MutableList<Intent> = Collections.synchronizedList(mutableListOf())

    override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
        if (intent.action != Intent.ACTION_VIEW) return null
        sent += intent
        return Instrumentation.ActivityResult(0, null)
    }
}
