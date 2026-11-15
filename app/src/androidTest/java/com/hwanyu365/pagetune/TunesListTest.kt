package com.hwanyu365.pagetune

import android.content.Intent
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TunesListTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc1")
    fun `TC#1 Tune 목록은 최근 만든 순 카드이고 카드·오버플로 편집이 그 Tune 을 연다`() = listAndOpen()

    @Test
    @JvmName("tc2")
    fun `TC#2 복제하면 규칙까지 같은 새 Tune 이 생기고 원본은 그대로다`() = duplicate()

    @Test
    @JvmName("tc3")
    fun `TC#3 선택 없이 삭제하면 지우지 않고 하나를 골라 지우면 규칙까지 사라진다`() = deleteSelected()

    @Test
    @JvmName("tc4")
    fun `TC#4 Tune 이 없으면 설명과 Tune 만들기 버튼이 보이고 버튼이 새 편집 화면을 연다`() = emptyState()

    @Test
    @JvmName("tc36a")
    fun `TC#36 Tunes 탭 삭제 모드에서 체크박스를 직접 눌러도 카드를 누른 것과 같다`() = checkboxSelects(onTunes = true)

    @Test
    @JvmName("tc36b")
    fun `TC#36 Pages 탭 삭제 모드에서 체크박스를 직접 눌러도 카드를 누른 것과 같다`() = checkboxSelects(onTunes = false)

    @Test
    @JvmName("tc49")
    fun `TC#49 Tune 삭제를 되돌리면 규칙과 웹페이지 연결까지 돌아온다`() = undoRestoresLinks()

    private fun listAndOpen() {
        addTune("Alpha", testUrl = "https://www.alpha.example/path")
        addTune("Beta")
        addTune("Gamma", testUrl = "https://gamma.example/")
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(tunesTab).perform(click())
            eventually { assertEquals(listOf("Gamma", "Beta", "Alpha"), shownNames(R.id.rv_plugins, R.id.tv_plugin_name)) }
            onView(allOf(withId(R.id.tv_host), inRowWith("Alpha"))).check(matches(withText("alpha.example")))
            onView(allOf(withId(R.id.tv_avatar), inRowWith("Alpha"))).check(matches(withText("A")))
            onView(allOf(withId(R.id.tv_host), inRowWith("Beta"))).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(allOf(withId(R.id.tv_avatar), inRowWith("Beta"))).check(matches(withText("B")))

            onView(allOf(withId(R.id.tv_plugin_name), withText("Beta"))).perform(click())
            assertEditorShows("Beta")
            onView(tunesTab).perform(click())

            openItemMenu(withText("Gamma"))
            for (item in listOf(R.string.list_edit, R.string.tune_duplicate, R.string.delete)) {
                onView(withText(item)).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
            }
            onView(withText(R.string.list_edit)).inRoot(isPlatformPopup()).perform(click())
            assertEditorShows("Gamma")
        }
    }

    private fun assertEditorShows(name: String) {
        eventually { assertTrue(resumedActivity() is PluginEditActivity) }
        eventually { assertEquals(name, onMain { resumedActivity().findViewById<EditText>(R.id.et_inp_pname).text.toString() }) }
        onMain { resumedActivity().finish() }
        eventually { assertTrue(resumedActivity() is MainActivity) }
    }

    private fun duplicate() {
        val source = addTune("Source", listOf(hideRule("ad"), colorRule("box", "color:#FFFFFF")), testUrl = "https://source.example/")
        val rules = rulesOf(source)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(tunesTab).perform(click())
            eventually { openItemMenu(withText("Source")) }
            onView(withText(R.string.tune_duplicate)).inRoot(isPlatformPopup()).perform(click())
            val copyName = targetContext.getString(R.string.tune_copy_name, "Source")
            eventually { onView(allOf(withId(R.id.tv_plugin_name), withText(copyName))).check(matches(isDisplayed())) }
            val tunes = runBlocking { database.pluginDao().getAllPlugins() }
            assertEquals(2, tunes.size)
            val copy = tunes.single { it.id != source }
            assertEquals(listOf(copyName, "https://source.example/"), listOf(copy.name, copy.testUrl))
            assertEquals(rules, rulesOf(copy.id))
            val original = tunes.single { it.id == source }
            assertEquals(listOf("Source", "https://source.example/"), listOf(original.name, original.testUrl))
            assertEquals(rules, rulesOf(source))
        }
    }

    private fun deleteSelected() {
        val keep = addTune("Keep", listOf(hideRule("a")))
        val gone = addTune("Gone", listOf(hideRule("b"), hideRule("c")))
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(tunesTab).perform(click())
            eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Gone"))).check(matches(isDisplayed())) }
            onView(withId(R.id.action_delete_mode)).perform(click())
            onView(withId(R.id.action_delete_selected)).perform(click())
            // 선택 없음 안내는 토스트다 → 확인 창 없이 아무것도 지우지 않는 결과로 판정한다.
            // 대화상자 창을 직접 찾는다 → Espresso 가 창이 뜨기를 기다렸다가 없을 때만 통과한다.
            val dialog = runCatching { onView(withText(R.string.tune_delete_title)).inRoot(isDialog()).check(matches(isDisplayed())) }
            assertTrue("선택 없이 확인 창이 뜸", dialog.exceptionOrNull() is androidx.test.espresso.NoMatchingRootException)
            assertEquals(2, runBlocking { database.pluginDao().getAllPlugins() }.size)

            androidx.test.espresso.Espresso.pressBack()
            eventually { onView(withId(R.id.action_delete_selected)).check(doesNotExist()) }
            onView(allOf(withId(R.id.tv_plugin_name), withText("Gone"))).perform(longClick())
            eventually { assertSelectedCount(1) }
            onView(withId(R.id.action_delete_selected)).perform(click())
            onView(withText(targetContext.resources.getQuantityString(R.plurals.tune_delete_message, 1, 1))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(listOf("Keep"), shownNames(R.id.rv_plugins, R.id.tv_plugin_name)) }
            assertEquals(listOf(keep), runBlocking { database.pluginDao().getAllPlugins() }.map { it.id })
            assertEquals(emptyList<Any>(), rulesOf(gone))
            assertEquals(1, rulesOf(keep).size)
        }
    }

    private fun emptyState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(tunesTab).perform(click())
            eventually { onView(allOf(withId(R.id.layout_empty), isDisplayed())).check(matches(isDisplayed())) }
            onView(allOf(withText(R.string.empty_tunes_message), isDisplayed())).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.btn_empty_action), isDisplayed())).check(matches(withText(R.string.empty_tunes_action))).perform(click())
            eventually { assertTrue(resumedActivity() is PluginEditActivity) }
            assertEquals("", onMain { resumedActivity().findViewById<EditText>(R.id.et_inp_pname).text.toString() })
            assertEquals(-1L, onMain { resumedActivity().intent.getLongExtra("plugin_id", -1) })
        }
    }

    private fun checkboxSelects(onTunes: Boolean) {
        val list = if (onTunes) R.id.rv_plugins else R.id.rv_webpages
        val names = if (onTunes) listOf("One", "Two") else listOf("Page One", "Page Two")
        if (onTunes) names.forEach { addTune(it) } else names.forEach { addWebPage(it, "https://${it.replace(" ", "").lowercase()}.example/") }
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(if (onTunes) tunesTab else pagesTab).perform(click())
            eventually { onView(allOf(withText(names[0]), isDescendantOfA(withId(list)))).check(matches(isDisplayed())) }
            onView(withId(R.id.action_delete_mode)).perform(click())
            val box = { name: String -> allOf(withId(R.id.chk_delete), inRowWith(name), isDisplayed()) }
            tapOnce(box(names[0]))
            tapOnce(box(names[1]))
            eventually { assertSelectedCount(2) }
            tapOnce(box(names[1]))
            eventually { assertSelectedCount(1) }
            tapOnce(allOf(withText(names[1]), isDescendantOfA(withId(list))))
            eventually { assertSelectedCount(2) }
            tapOnce(allOf(withText(names[0]), isDescendantOfA(withId(list))))
            eventually { assertSelectedCount(1) }
            tapOnce(box(names[0]))
            onView(withId(R.id.action_delete_selected)).perform(click())
            val message = if (onTunes) R.plurals.tune_delete_message else R.plurals.webpage_delete_message
            onView(withText(targetContext.resources.getQuantityString(message, 2, 2))).inRoot(isDialog()).check(matches(isDisplayed()))
        }
    }

    // 줄을 바꾸는 동안에는 변경 애니메이션 때문에 같은 줄이 둘 있다 → 하나로 정해질 때 누른다.
    private fun tapOnce(view: org.hamcrest.Matcher<android.view.View>) = eventually { onView(view).perform(click()) }

    private fun assertSelectedCount(count: Int) {
        assertEquals(count.toString(), onMain { selectionTitle() })
    }

    private fun selectionTitle(): String {
        // 선택 수는 ActionMode 상단 바의 제목이다.
        val bar = resumedActivity().window.decorView.shownViews().first { it.javaClass.simpleName == "ActionBarContextView" }
        return bar.shownViews().filterIsInstance<android.widget.TextView>().first { it.id == androidx.appcompat.R.id.action_bar_title }.text.toString()
    }

    private fun undoRestoresLinks() {
        val rules = listOf(hideRule("ad"), colorRule("box", "background-color:#D32F2F"), clickRule("gnb_next", "#next"))
        LocalPage("""<html><body><div id="ad">AD</div><div id="box">BOX</div><a id="next" href="#">next</a></body></html>""").use { page ->
            val tune = addTune("Linked", rules)
            val expected = rulesOf(tune)
            val first = addWebPage("Page One", page.url, tune)
            addWebPage("Page Two", page.url, tune)
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(tunesTab).perform(click())
                eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Linked"))).check(matches(isDisplayed())) }
                deleteFromItemMenu(withText("Linked"))
                eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
                assertEquals(emptyList<Any>(), rulesOf(tune))
                onView(withText(R.string.list_undo)).perform(click())
                eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Linked"))).check(matches(isDisplayed())) }
                assertEquals(expected, rulesOf(tune))
                onView(pagesTab).perform(click())
                for (page in listOf("Page One", "Page Two")) {
                    eventually { onView(allOf(withId(R.id.tv_plugin), inRowWith(page))).check(matches(withText("Linked"))) }
                }
            }

            val viewer = Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", first)
            ActivityScenario.launch<WebBrowserActivity>(viewer).use {
                val web = eventually { onMain { resumedActivity().findViewById<android.webkit.WebView>(R.id.wv_browser) } }
                eventually { assertEquals("\"none\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
                eventually { assertEquals("\"rgb(211, 47, 47)\"", web.js("getComputedStyle(document.getElementById('box')).backgroundColor")) }
            }
            val editor = Intent(targetContext, PluginEditActivity::class.java).putExtra("plugin_id", tune)
            ActivityScenario.launch<PluginEditActivity>(editor).use {
                eventually { assertEquals(3, onMain { resumedActivity().findViewById<android.view.ViewGroup>(R.id.layout_actions_list).childCount }) }
            }

            ActivityScenario.launch(MainActivity::class.java).use {
                onView(pagesTab).perform(click())
                eventually { onView(allOf(withId(R.id.tv_plugin), inRowWith("Page One"))).check(matches(withText("Linked"))) }
                onView(tunesTab).perform(click())
                eventually { onView(allOf(withId(R.id.tv_plugin_name), withText("Linked"))).check(matches(isDisplayed())) }
                deleteFromItemMenu(withText("Linked"))
                eventually { onView(withText(R.string.list_undo)).check(matches(isDisplayed())) }
                eventually(timeoutMs = 15_000) { assertTrue(snackbarGone()) }
                onView(pagesTab).perform(click())
                for (page in listOf("Page One", "Page Two")) {
                    eventually { onView(allOf(withId(R.id.tv_plugin), inRowWith(page))).check(matches(withText(R.string.webpage_item_tune_none))) }
                }
            }
        }
    }
}
