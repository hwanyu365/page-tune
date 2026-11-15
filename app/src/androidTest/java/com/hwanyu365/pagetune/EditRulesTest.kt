package com.hwanyu365.pagetune

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hwanyu365.pagetune.db.PluginAction
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditRulesTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc21")
    fun `TC#21 영어·한국어 각각 Tunes 탭·편집 화면·다이얼로그 문구가 그 언어다`() = languages()

    @Test
    @JvmName("tc28")
    fun `TC#28 잘못된 규칙이 섞여도 편집 미리보기는 정상 규칙을 적용하고 스크립트 예외가 없다`() = brokenRules()

    @Test
    @JvmName("tc40")
    fun `TC#40 선택한 요소와 규칙 목록은 읽기 쉬운 라벨을 영어·한국어로 보인다`() = labels()

    private val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"

    private fun useLocale(tag: String) {
        shell("$locales --locales $tag")
        waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
    }

    private fun texts(root: View): List<String> = onMain { root.shownViews().filterIsInstance<TextView>().map { it.text.toString().trim() }.filter { it.isNotEmpty() }.toList() }

    private fun languages() {
        changeDeviceSetting("$locales --locales en", restore = locales)
        val hangul = Regex("[가-힣]")
        val letter = Regex("\\p{L}")
        // 두 언어에서 같은 원어(앱 이름·Tune·Pages·Tunes)와 사용자가 넣은 이름이다.
        val sameInBoth = setOf(targetContext.getString(R.string.app_name), "Pages", "Tunes", "Tune", "Tune A", "T")
        for (tag in listOf("en", "ko")) {
            useLocale(tag)
            clearData()
            addTune("Tune A")
            val shown = mutableListOf<String>()
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(tunesTab).perform(click())
                eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Tune A"))).check(matches(isDisplayed())) }
                shown += texts(onMain { resumedActivity().window.decorView })
                openItemMenu(withText("Tune A"))
                onView(withText(R.string.delete)).inRoot(isPlatformPopup()).perform(click())
                onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(isDisplayed()))
                shown += texts(dialogRoot())
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            }
            clearData()
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(tunesTab).perform(click())
                eventually { onView(allOf(withId(R.id.layout_empty), isDisplayed())).check(matches(isDisplayed())) }
                shown += texts(onMain { resumedActivity().window.decorView })
            }
            ActivityScenario.launch(PluginEditActivity::class.java).use {
                eventually { assertTrue(textOf(R.id.tv_edit_step).isNotEmpty()) }
                shown += texts(onMain { resumedActivity().window.decorView })
            }
            val wrong = shown.distinct().filter { it !in sameInBoth && letter.containsMatchIn(it) && hangul.containsMatchIn(it) != (tag == "ko") }
            assertEquals("$tag 화면에 다른 언어 문구", emptyList<String>(), wrong)
            assertTrue("$tag 확인한 문구가 적음: $shown", shown.distinct().size >= 10)
        }
    }

    private fun rule(type: String, details: String, css: String? = null) = PluginAction(pluginId = 0, actionType = type, targetSelector = details, styleCss = css)

    private val brokenPage = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="background:#fff">
        <div id="ad">AD</div><div id="q" data-k="it's &quot;q&quot;">Q</div><p id="c" data-c="x">C</p><div id="box">BOX</div><div id="keep">KEEP</div>
        </body></html>
    """.trimIndent()

    private fun brokenRules() = LocalPage(brokenPage).use { page ->
        val tune = addTune(
            "Mixed",
            listOf(
                hideRule("ad"),
                rule("hidden", """{"selector":"div[[","tagName":"div","attributes":{"data-k":"it's \"q\""}}"""),
                rule("hidden", "not json"),
                rule("color", """{"selector":"p[[","tagName":"p","attributes":{"data-c":"x"}}""", "background-color:#D32F2F"),
                colorRule("box", "background-color:#1976D2"),
            ),
            testUrl = page.url,
        )
        shell("logcat -c")
        ActivityScenario.launch<PluginEditActivity>(Intent(targetContext, PluginEditActivity::class.java).putExtra("plugin_id", tune)).use {
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals("\"complete\"", web.js("document.getElementById('keep') ? document.readyState : ''")) }
            fun style(id: String, prop: String) = web.js("getComputedStyle(document.getElementById('$id')).$prop")
            eventually { assertEquals("\"none\"", style("ad", "display")) }
            eventually { assertEquals("\"none\"", style("q", "display")) }
            eventually { assertEquals("\"rgb(211, 47, 47)\"", style("c", "backgroundColor")) }
            eventually { assertEquals("\"rgb(25, 118, 210)\"", style("box", "backgroundColor")) }
            assertEquals("\"block\"", style("keep", "display"))

            inspectOn(web)
            web.js("AndroidBridge.onElementSelected(JSON.stringify({selector: 'div[[', tagName: 'div', attributes: {}}), window.webPluginCountMatches('div[['))")
            val warning = targetContext.getString(R.string.edit_match_none, 0)
            eventually { assertTrue(textOf(R.id.tv_selected_selector), textOf(R.id.tv_selected_selector).startsWith(warning)) }
        }
        val uncaught = shell("logcat -d -s chromium:*").lines().filter { "Uncaught" in it }
        assertEquals(emptyList<String>(), uncaught)
    }

    private val labelPage = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="background:#fff">
        <a id="icon" href="#" aria-label="Search"><svg width="40" height="40"></svg></a><div id="box">BOX</div>
        </body></html>
    """.trimIndent()

    private fun labels() = LocalPage(labelPage).use { page ->
        changeDeviceSetting("$locales --locales en", restore = locales)
        for (tag in listOf("en", "ko")) {
            useLocale(tag)
            clearData()
            val tune = addTune(
                "Labels",
                listOf(
                    rule("hidden", """{"selector":"#icon","tagName":"a","id":"icon","attributes":{"aria-label":"Search"}}"""),
                    rule("hidden", """{"selector":"#ad-1a2b3c4d5e","tagName":"div","id":"ad-1a2b3c4d5e","textContent":"Banner","attributes":{}}"""),
                    rule("hidden", "not json"),
                ),
                testUrl = page.url,
            )
            val link = targetContext.getString(R.string.edit_label_with_text, targetContext.getString(R.string.element_kind_link), "Search")
            val area = targetContext.getString(R.string.edit_label_with_text, targetContext.getString(R.string.element_kind_area), "Banner")
            ActivityScenario.launch<PluginEditActivity>(Intent(targetContext, PluginEditActivity::class.java).putExtra("plugin_id", tune)).use {
                val web = webView(R.id.wv_inspector)
                eventually { assertEquals("\"complete\"", web.js("document.getElementById('box') ? document.readyState : ''")) }
                eventually {
                    assertEquals(
                        listOf(link, targetContext.getString(R.string.edit_rule_select_again, area), targetContext.getString(R.string.edit_rule_broken)),
                        onMain { ruleLabels() },
                    )
                }
                inspectOn(web)
                pick(web, "icon")
                assertEquals(listOf(targetContext.getString(R.string.edit_match_count, 1), link).joinToString("\n"), textOf(R.id.tv_selected_selector))
                onMain { assertEquals(View.GONE, resumedActivity().findViewById<View>(R.id.tv_selector_detail).visibility) }
                tap(R.id.btn_selector_details)
                eventually { onView(withId(R.id.tv_selector_detail)).check(matches(isDisplayed())) }
                assertEquals("#icon", textOf(R.id.tv_selector_detail))
            }
        }
    }

    private fun ruleLabels(): List<String> {
        val list = resumedActivity().findViewById<android.view.ViewGroup>(R.id.layout_actions_list)
        return (0 until list.childCount).mapNotNull { list.getChildAt(it).findViewById<TextView?>(R.id.tv_rule_label)?.text?.toString() }
    }
}
