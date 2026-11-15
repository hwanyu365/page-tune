package com.hwanyu365.pagetune

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import com.hwanyu365.pagetune.util.TriggerOptions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditScreenTest {

    private val html = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
        <body style="margin:0;background:#fff">
        <div id="wrap"><a id="lnk" href="/next">link</a></div><div id="ad">AD</div><div id="box">BOX</div><div style="height:5000px"></div>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc5")
    fun `TC#5 주소를 불러오면 정규화해 열고 같은 주소는 다시 읽고 http·https 밖은 열지 않는다`() = loadAddress()

    @Test
    @JvmName("tc6")
    fun `TC#6 요소 선택 모드에서 링크를 눌러도 이동하지 않고 강조되며 부모·자식으로 옮기고 끄면 풀린다`() = pickMode()

    @Test
    @JvmName("tc7")
    fun `TC#7 영역 비율 0·51·a 와 색을 고르지 않은 색상은 거부하고 빈 비율은 40 이다`() = ruleInputs()

    @Test
    @JvmName("tc8")
    fun `TC#8 시작 위치를 두 번 지정하면 마지막 값 하나만 남는다`() = startPosition()

    @Test
    @JvmName("tc9")
    fun `TC#9 숨기기는 추가 즉시와 다시 읽은 뒤에도 적용된다`() = hideApplies()

    @Test
    @JvmName("tc10")
    fun `TC#10 규칙 삭제 버튼은 규칙을 지우고 요소를 되돌리며 항목을 누르면 남는다`() = ruleDelete()

    @Test
    @JvmName("tc11")
    fun `TC#11 이름 없이 저장하면 거부하고 이름을 넣어 저장하면 닫히며 다시 열면 같은 규칙이다`() = saveName()

    @Test
    @JvmName("tc14")
    fun `TC#14 바꾼 뒤 나가면 확인을 묻고 바꾸지 않았으면 바로 닫힌다`() = leaveConfirm()

    @Test
    @JvmName("tc26")
    fun `TC#26 로드 주소 인자는 기존 Tune 의 테스트 주소를 바꾸지 않고 새 Tune 은 그 주소를 쓴다`() = loadArgument()

    @Test
    @JvmName("tc27")
    fun `TC#27 새 Tune 저장은 넘긴 웹페이지를 연결하고 결과로 id 를 돌려준다`() = saveResult()

    @Test
    @JvmName("tc38")
    fun `TC#38 단계 표시는 주소·요소 선택·규칙·저장 순서로 바뀐다`() = steps()

    @Test
    @JvmName("tc50")
    fun `TC#50 테스트 주소는 저장 전에 분류하고 열 수 없는 저장 주소는 인라인 오류로 보인다`() = testUrlRules()

    @Test
    @JvmName("tc61")
    fun `TC#61 클릭 연결 동작 방식에 당기기 두 개가 있고 고르면 규칙으로 저장된다`() = pullTriggers()

    private fun editor(intent: Intent = Intent(targetContext, PluginEditActivity::class.java)) = ActivityScenario.launchActivityForResult<PluginEditActivity>(intent)

    private fun editorFor(tune: Long, loadUrl: String? = null) = editor(
        Intent(targetContext, PluginEditActivity::class.java).putExtra("plugin_id", tune).apply { loadUrl?.let { putExtra(PluginEditActivity.EXTRA_LOAD_URL, it) } }
    )

    private fun addressError(): CharSequence? = onMain { resumedActivity().findViewById<TextInputLayout>(R.id.til_inp_taddr).error }

    private fun loadAddress() = LocalPage(html).use { page ->
        editor().use {
            // https 로 정규화한 주소를 기기 안 소켓으로 받는다 → 외부 네트워크 없이 그 주소로 열었는지 본다.
            java.net.ServerSocket(0).use { server ->
                val connected = java.util.concurrent.CountDownLatch(1)
                kotlin.concurrent.thread(isDaemon = true) { runCatching { server.accept().use { connected.countDown() } } }
                onView(withId(R.id.et_inp_taddr)).perform(replaceText("localhost:${server.localPort}/"))
                onView(withId(R.id.btn_load_url)).perform(click())
                assertEquals("https://localhost:${server.localPort}/", textOf(R.id.et_inp_taddr))
                assertTrue("정규화한 주소로 연결하지 않음", connected.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
            val web = webView(R.id.wv_inspector)

            loadInEditor(page.url, "document.getElementById('box')")
            web.js("window.loadMark = 1")
            onView(withId(R.id.btn_load_url)).perform(click())
            eventually { assertEquals("\"again\"", web.js("document.getElementById('box') && window.loadMark === undefined ? 'again' : 'same'")) }

            for (bad in listOf("content://x", "file:///a")) {
                onView(withId(R.id.et_inp_taddr)).perform(replaceText(bad))
                onView(withId(R.id.btn_load_url)).perform(click())
                eventually { assertNotNull(addressError()) }
                assertEquals(page.url, onMain { web.url })
                onView(withId(R.id.et_inp_taddr)).perform(replaceText(page.url))
                onView(withId(R.id.btn_load_url)).perform(click())
                eventually { assertNull(addressError()) }
            }
        }
    }

    private fun pickMode() = LocalPage(html).use { page ->
        editor().use {
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            pick(web, "lnk")
            assertEquals("\"/\"", web.js("location.pathname"))
            assertEquals("true", web.js("document.getElementById('lnk').style.outline !== ''"))
            tap(R.id.btn_select_parent)
            eventually { assertEquals("#wrap", textOf(R.id.tv_selector_detail)) }
            assertEquals("true", web.js("document.getElementById('wrap').style.outline !== '' && document.getElementById('lnk').style.outline === ''"))
            tap(R.id.btn_select_child)
            eventually { assertEquals("#lnk", textOf(R.id.tv_selector_detail)) }
            tap(R.id.btn_toggle_inspect)
            eventually { assertEquals("false", web.js("window.webPluginInspectEnabled === true")) }
            eventually { onMain { assertTrue(resumedActivity().findViewById<android.view.View>(R.id.layout_action_config).visibility != android.view.View.VISIBLE) } }
            assertEquals("true", web.js("document.getElementById('lnk').style.outline === '' && document.getElementById('wrap').style.outline === ''"))
        }
    }

    private fun chooseTrigger(type: String) {
        tap(R.id.sp_trigger_type)
        onView(withText(TriggerOptions.labelResOf(type)!!)).inRoot(isPlatformPopup()).perform(click())
    }

    private fun ruleInputs() = LocalPage(html).use { page ->
        editor().use {
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            pick(web, "lnk")
            tap(R.id.rb_action_click)
            chooseTrigger("gesture_top")
            for (bad in listOf("0", "51", "a")) {
                onView(withId(R.id.et_gesture_area)).perform(replaceText(bad))
                tap(R.id.btn_add_action)
                assertEquals("영역 $bad 가 추가됨", 0, ruleCount())
            }
            onView(withId(R.id.et_gesture_area)).perform(replaceText(""))
            tap(R.id.btn_add_action)
            eventually { assertEquals(1, ruleCount()) }

            pick(web, "box")
            tap(R.id.rb_action_color)
            tap(R.id.btn_add_action)
            assertEquals("색 없이 색상 규칙이 추가됨", 1, ruleCount())

            onView(withId(R.id.et_inp_pname)).perform(replaceText("Area"))
            tap(R.id.btn_plugin_save)
            val tune = eventually { runBlocking { database.pluginDao().getAllPlugins() }.single() }
            val rule = runBlocking { database.pluginDao().getPluginWithActions(tune.id)!!.actions }.single()
            assertEquals(listOf("click", "gesture_top", 40), listOf(rule.actionType, rule.triggerType, rule.gestureAreaPercent))
        }
    }

    private fun startPosition() = LocalPage(html).use { page ->
        editor().use {
            val web = loadInEditor(page.url, "document.getElementById('box')")
            for (y in listOf(300, 700)) {
                assertEquals("$y", web.js("window.scrollTo(0, $y); Math.round(window.pageYOffset)"))
                tap(R.id.btn_set_scroll)
                eventually { assertTrue(allText(R.id.layout_actions_list).contains("$y")) }
            }
            assertEquals(1, ruleCount())
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Start"))
            tap(R.id.btn_plugin_save)
            val tune = eventually { runBlocking { database.pluginDao().getAllPlugins() }.single() }
            val rules = rulesOf(tune.id)
            assertEquals(listOf(Triple("scroll", JSONObject().put("y", 700).toString(), null as String?)), rules)
        }
    }

    private fun addHide(web: android.webkit.WebView, id: String) {
        pick(web, id)
        tap(R.id.rb_action_hidden)
        tap(R.id.btn_add_action)
        eventually { assertEquals("\"none\"", web.js("getComputedStyle(document.getElementById('$id')).display")) }
    }

    private fun hideApplies() = LocalPage(html).use { page ->
        editor().use {
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            addHide(web, "ad")
            web.js("location.reload()")
            eventually { assertEquals("\"none\"", web.js("document.getElementById('box') ? getComputedStyle(document.getElementById('ad')).display : 'loading'")) }
        }
    }

    private fun ruleDelete() = LocalPage(html).use { page ->
        editor().use {
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            addHide(web, "ad")
            onMain { resumedActivity().findViewById<android.view.View>(R.id.layout_rule_body).performClick() }
            assertEquals(1, ruleCount())
            onMain { resumedActivity().findViewById<android.view.View>(R.id.btn_rule_delete).performClick() }
            eventually { assertEquals(0, ruleCount()) }
            eventually { assertEquals("\"block\"", web.js("document.getElementById('box') ? getComputedStyle(document.getElementById('ad')).display : 'loading'")) }
        }
    }

    private fun saveName() = LocalPage(html).use { page ->
        editor().use { scenario ->
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            addHide(web, "ad")
            tap(R.id.btn_plugin_save)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertTrue(runBlocking { database.pluginDao().getAllPlugins() }.isEmpty())
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Named"))
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        val tune = runBlocking { database.pluginDao().getAllPlugins() }.single()
        val saved = rulesOf(tune.id)
        assertEquals(listOf("hidden"), saved.map { it.first })
        editorFor(tune.id).use {
            eventually { assertEquals("Named", textOf(R.id.et_inp_pname)) }
            eventually { assertEquals(1, ruleCount()) }
            assertEquals(saved, rulesOf(tune.id))
        }
    }

    private fun leaveConfirm() {
        editor().use { scenario ->
            pressBackUnconditionally()
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        editor().use { scenario ->
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Changed"))
            tap(R.id.btn_plugin_cancel)
            onView(withText(R.string.edit_leave_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.edit_leave_keep)).inRoot(isDialog()).perform(click())
            assertEquals("Changed", textOf(R.id.et_inp_pname))
            pressBackUnconditionally()
            onView(withText(R.string.edit_leave_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.edit_leave_discard)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        assertTrue(runBlocking { database.pluginDao().getAllPlugins() }.isEmpty())
    }

    private fun loadArgument() = LocalPage(html).use { page ->
        val testUrl = page.url + "?a"
        val loadUrl = page.url + "?b"
        val tune = addTune("Existing", testUrl = testUrl)
        editorFor(tune, loadUrl).use { scenario ->
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals(testUrl, textOf(R.id.et_inp_taddr)) }
            eventually { assertEquals(loadUrl, onMain { web.url }) }
            pressBackUnconditionally()
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        editorFor(tune, loadUrl).use { scenario ->
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals("\"complete\"", web.js("document.getElementById('box') ? document.readyState : ''")) }
            tap(R.id.btn_set_scroll)
            eventually { assertEquals(1, ruleCount()) }
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        assertEquals(1, rulesOf(tune).size)
        assertEquals(testUrl, runBlocking { database.pluginDao().getAllPlugins() }.single().testUrl)

        clearData()
        editor(Intent(targetContext, PluginEditActivity::class.java).putExtra(PluginEditActivity.EXTRA_LOAD_URL, loadUrl)).use { scenario ->
            eventually { assertEquals(loadUrl, textOf(R.id.et_inp_taddr)) }
            onView(withId(R.id.et_inp_pname)).perform(replaceText("New"))
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        assertEquals(loadUrl, runBlocking { database.pluginDao().getAllPlugins() }.single().testUrl)
    }

    private fun linking(webPage: Long) = Intent(targetContext, PluginEditActivity::class.java).putExtra(PluginEditActivity.EXTRA_LINK_WEBPAGE_ID, webPage)

    private fun page(id: Long) = runBlocking { database.webPageDao().getWebPageById(id) }

    private fun saveResult() {
        val linked = addWebPage("Linked", "https://linked.example/")
        editor(linking(linked)).use { scenario ->
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Fresh"))
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
            val tune = runBlocking { database.pluginDao().getAllPlugins() }.single { it.name == "Fresh" }
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(tune.id, scenario.result.resultData.getLongExtra(PluginEditActivity.EXTRA_SAVED_PLUGIN_ID, -1))
            assertEquals(tune.id, page(linked)!!.pluginId)
        }

        val other = addWebPage("Other", "https://other.example/")
        val existing = addTune("Existing")
        editorFor(existing).use { scenario ->
            eventually { assertEquals("Existing", textOf(R.id.et_inp_pname)) }
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Existing 2"))
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(existing, scenario.result.resultData.getLongExtra(PluginEditActivity.EXTRA_SAVED_PLUGIN_ID, -1))
            assertNull(page(other)!!.pluginId)
        }

        editor(linking(other)).use { scenario ->
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Dropped"))
            tap(R.id.btn_plugin_cancel)
            onView(withText(R.string.edit_leave_discard)).inRoot(isDialog()).perform(click())
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        assertNull(page(other)!!.pluginId)
        assertTrue(runBlocking { database.pluginDao().getAllPlugins() }.none { it.name == "Dropped" })

        editor(linking(other)).use { scenario ->
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Orphan"))
            runBlocking { database.webPageDao().deleteWebPagesByIds(listOf(other)) }
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        assertTrue(runBlocking { database.pluginDao().getAllPlugins() }.any { it.name == "Orphan" })
        assertNull(page(other))
    }

    private fun step(n: Int, name: Int) = targetContext.getString(R.string.edit_step, n, targetContext.getString(name))

    private fun steps() = LocalPage(html).use { page ->
        editor().use {
            eventually { assertEquals(step(1, R.string.edit_step_address), textOf(R.id.tv_edit_step)) }
            val web = loadInEditor(page.url, "document.getElementById('box')")
            eventually { assertEquals(step(2, R.string.edit_step_pick), textOf(R.id.tv_edit_step)) }
            inspectOn(web)
            pick(web, "ad")
            eventually { assertEquals(step(3, R.string.edit_step_rule), textOf(R.id.tv_edit_step)) }
            tap(R.id.rb_action_hidden)
            tap(R.id.btn_add_action)
            eventually { assertEquals(step(4, R.string.edit_step_save), textOf(R.id.tv_edit_step)) }
            pick(web, "box")
            eventually { assertEquals(step(3, R.string.edit_step_rule), textOf(R.id.tv_edit_step)) }
        }
        val tune = addTune("Existing", testUrl = page.url)
        editorFor(tune).use {
            val web = webView(R.id.wv_inspector)
            eventually { assertEquals("\"complete\"", web.js("document.getElementById('box') ? document.readyState : ''")) }
            eventually { assertEquals(step(2, R.string.edit_step_pick), textOf(R.id.tv_edit_step)) }
        }
    }

    private fun saveWithAddress(address: String): Boolean {
        clearData()
        var closed = false
        editor().use { scenario ->
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Addr"))
            onView(withId(R.id.et_inp_taddr)).perform(replaceText(address))
            tap(R.id.btn_plugin_save)
            closed = runCatching { eventually(timeoutMs = 3_000) { assertEquals(Lifecycle.State.DESTROYED, scenario.state) } }.isSuccess
            if (!closed) assertNotNull("저장 거부인데 주소 칸 오류 없음", addressError())
        }
        return closed
    }

    private fun testUrlRules() {
        assertTrue("file 주소가 저장됨", !saveWithAddress("file:///sdcard/a"))
        assertTrue(runBlocking { database.pluginDao().getAllPlugins() }.isEmpty())
        assertTrue(saveWithAddress("HTTPS://Example.com"))
        assertEquals("https://Example.com", runBlocking { database.pluginDao().getAllPlugins() }.single().testUrl)
        assertTrue(saveWithAddress(""))
        assertEquals("", runBlocking { database.pluginDao().getAllPlugins() }.single().testUrl.orEmpty())

        clearData()
        val tune = addTune("Stored", testUrl = "content://x")
        editorFor(tune).use {
            eventually { onView(withId(R.id.layout_load_error)).check(matches(isDisplayed())) }
            assertEquals(targetContext.getString(R.string.load_error_bad_address_title), textOf(R.id.tv_load_error_title))
            assertEquals("content://x", textOf(R.id.et_inp_taddr))
            val web = webView(R.id.wv_inspector)
            assertTrue(onMain { web.url.orEmpty() }.let { it.isEmpty() || it == "about:blank" })
        }
    }

    private fun pullTriggers() = LocalPage(html).use { page ->
        editor().use { scenario ->
            val web = loadInEditor(page.url, "document.getElementById('box')")
            inspectOn(web)
            pick(web, "lnk")
            tap(R.id.rb_action_click)
            tap(R.id.sp_trigger_type)
            for (type in listOf("pull_top", "pull_bottom")) {
                onView(withText(TriggerOptions.labelResOf(type)!!)).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
            }
            onView(withText(TriggerOptions.labelResOf("pull_bottom")!!)).inRoot(isPlatformPopup()).perform(click())
            tap(R.id.btn_add_action)
            val label = targetContext.getString(R.string.edit_rule_type_click, targetContext.getString(R.string.edit_trigger_pull_bottom))
            eventually { assertTrue(allText(R.id.layout_actions_list).contains(label)) }
            assertTrue("편집 시트에 스위치가 있음", onMain { resumedActivity().window.decorView.shownViews().none { it is android.widget.Switch || it is androidx.appcompat.widget.SwitchCompat } })
            onView(withId(R.id.et_inp_pname)).perform(replaceText("Pull"))
            tap(R.id.btn_plugin_save)
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
        val tune = runBlocking { database.pluginDao().getAllPlugins() }.single()
        assertEquals(listOf("click" to "pull_bottom"), runBlocking { database.pluginDao().getPluginWithActions(tune.id)!!.actions }.map { it.actionType to it.triggerType })
        editorFor(tune.id).use {
            val label = targetContext.getString(R.string.edit_rule_type_click, targetContext.getString(R.string.edit_trigger_pull_bottom))
            eventually { assertTrue(allText(R.id.layout_actions_list).contains(label)) }
        }
    }
}
