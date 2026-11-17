package com.hwanyu365.pagetune

import android.content.Intent
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageViewEditTest {

    @Before
    fun setUp() = resetApp()

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc118")
    fun `TC#118 열람 중 Tune 편집은 현재 주소를 열고 테스트 주소는 저장해도 바뀌지 않는다`() = editAtCurrentAddress()

    @Test
    @JvmName("tc119")
    fun `TC#119 편집에서 숨기기를 저장하고 돌아오면 Tune 을 켜고 적용하며 취소하면 그대로다`() = saveOrCancelEdit()

    @Test
    @JvmName("tc120")
    fun `TC#120 Tune 없는 웹페이지에서 만든 Tune 은 현재 주소로 시작해 저장 후 적용·연결되고 취소하면 아무것도 생기지 않는다`() = createFromViewer()

    private fun editAtCurrentAddress() {
        LocalPage(page("<a id=\"go\" href=\"/moved\">go</a>")).use { server ->
            val tune = addTune("Edit", listOf(hideRule("none")), testUrl = TEST_URL)
            openViewer(addWebPage("Page", server.url, tune)).use {
                val web = loaded()
                web.js("document.getElementById('go').click()")
                eventually { assertEquals("\"/moved\"", web.js("location.pathname")) }
                val current = "${server.url}moved"

                quickEdit()
                val inspector = webView(R.id.wv_inspector)
                eventually { assertEquals(current, onMain { inspector.url }) }
                eventually { assertEquals(TEST_URL, textOf(R.id.et_inp_taddr)) }
                tap(R.id.btn_plugin_save)
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                assertEquals(TEST_URL, testUrlOf(tune))

                quickEdit()
                eventually { assertEquals(TEST_URL, textOf(R.id.et_inp_taddr)) }
                val again = webView(R.id.wv_inspector)
                eventually { assertEquals(current, onMain { again.url }) }
                leaveEditor()
                assertEquals(TEST_URL, testUrlOf(tune))
            }
        }
    }

    private fun saveOrCancelEdit() {
        LocalPage(page(BODY)).use { server ->
            val tune = addTune("Edit")
            openViewer(addWebPage("Page", server.url, tune)).use {
                val web = loaded()
                onView(withId(R.id.btn_quick_handle)).perform(click())
                onView(withId(R.id.btn_quick_toggle)).perform(click())
                eventually { assertEquals(onMain { resumedActivity().getString(R.string.quick_tune_on) }, onMain { resumedActivity().findViewById<View>(R.id.btn_quick_toggle).contentDescription.toString() }) }
                quickEdit()
                hideInEditor("ad")
                tap(R.id.btn_plugin_save)
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                eventually { assertEquals("\"none\"", display(web, "ad")) }
                assertEquals(onMain { resumedActivity().getString(R.string.quick_tune_off) }, onMain { resumedActivity().findViewById<View>(R.id.btn_quick_toggle).contentDescription.toString() })
                val saved = rulesOf(tune)
                assertEquals(1, saved.size)

                quickEdit()
                hideInEditor("box")
                tap(R.id.btn_plugin_cancel)
                onView(withText(R.string.edit_leave_discard)).inRoot(isDialog()).perform(click())
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                eventually { assertEquals("\"complete\"", web.js("document.readyState")) }
                assertEquals("\"block\"", display(web, "box"))
                assertEquals("\"none\"", display(web, "ad"))
                assertEquals(saved, rulesOf(tune))
            }
        }
    }

    private fun createFromViewer() {
        LocalPage(page(BODY)).use { server ->
            val pageId = addWebPage("Page", server.url)
            openViewer(pageId).use {
                val web = loaded()
                web.js("document.getElementById('go').click()")
                eventually { assertEquals("\"/moved\"", web.js("location.pathname")) }
                eventually { assertEquals("\"complete\"", web.js("document.readyState")) }
                val current = "${server.url}moved"

                quickEdit()
                eventually { assertEquals(current, textOf(R.id.et_inp_taddr)) }
                leaveEditor()
                quickEdit()
                hideInEditor("ad")
                tap(R.id.btn_plugin_cancel)
                onView(withText(R.string.edit_leave_discard)).inRoot(isDialog()).perform(click())
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                assertTrue(runBlocking { database.pluginDao().getAllPlugins() }.isEmpty())
                assertNull(pageOf(pageId).pluginId)

                quickEdit()
                eventually { assertEquals(current, textOf(R.id.et_inp_taddr)) }
                hideInEditor("ad")
                onView(withId(R.id.et_inp_pname)).perform(replaceText("Made"))
                tap(R.id.btn_plugin_save)
                eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
                eventually { assertEquals("\"none\"", display(web, "ad")) }
                val made = runBlocking { database.pluginDao().getAllPlugins() }.single()
                assertEquals("Made", made.name)
                assertEquals(made.id, pageOf(pageId).pluginId)
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                eventually { onView(allOf(withId(R.id.tv_plugin), withText("Made"), inRowWith("Page"))).check(matches(isDisplayed())) }
            }
        }
    }

    private fun quickEdit() {
        if (!onMain { resumedActivity().findViewById<View>(R.id.layout_quick_toolbar).isShown }) {
            onView(withId(R.id.btn_quick_handle)).perform(click())
        }
        onView(withId(R.id.btn_quick_edit)).perform(click())
        eventually { assertTrue(resumedActivity() is PluginEditActivity) }
    }

    private fun hideInEditor(id: String) {
        val inspector = webView(R.id.wv_inspector)
        eventually { assertEquals("\"complete\"", inspector.js("document.getElementById('$id') ? document.readyState : ''")) }
        inspectOn(inspector)
        pick(inspector, id)
        tap(R.id.rb_action_hidden)
        tap(R.id.btn_add_action)
    }

    // 바꾼 것이 없으면 확인 없이 닫히고, 있으면 버린다 → 어느 쪽이든 저장하지 않고 나간다.
    private fun leaveEditor() {
        tap(R.id.btn_plugin_cancel)
        runCatching { onView(withText(R.string.edit_leave_discard)).inRoot(isDialog()).perform(click()) }
        eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
    }

    private fun display(web: WebView, id: String) = web.js("getComputedStyle(document.getElementById('$id')).display")

    private fun testUrlOf(tune: Long) = runBlocking { database.pluginDao().getPluginWithActions(tune) }!!.plugin.testUrl

    private fun pageOf(id: Long) = runBlocking { database.webPageDao().getWebPageById(id) }!!

    private fun openViewer(pageId: Long): ActivityScenario<WebBrowserActivity> =
        ActivityScenario.launch(Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId))

    private fun loaded(): WebView {
        val web = webView(R.id.wv_browser)
        eventually { assertEquals("\"complete\"", web.js("document.body ? document.readyState : ''")) }
        return web
    }

    private fun page(body: String) =
        "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body style=\"margin:0;background:#fff;color:#000\">$body</body></html>"

    private companion object {
        const val TEST_URL = "https://test.example/"
        const val BODY = "<div id=\"ad\" style=\"padding:24px\">AD</div><div id=\"box\" style=\"padding:24px\">BOX</div><a id=\"go\" href=\"/moved\">go</a>"
    }
}
