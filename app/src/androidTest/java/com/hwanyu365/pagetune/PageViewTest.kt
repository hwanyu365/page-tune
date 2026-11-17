package com.hwanyu365.pagetune

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PageViewTest {

    @Before
    fun setUp() = resetApp()

    @After
    fun tearDown() = restoreDeviceSettings()

    @Test
    @JvmName("tc106")
    fun `TC#106 열람 화면은 상태바를 숨기고 이전·다음 버튼은 3버튼·제스처 내비게이션 바에 가리지 않는다`() = fullScreen()

    @Test
    @JvmName("tc107")
    fun `TC#107 연결된 Tune 이 삭제된 웹페이지는 Tune 없이 열린다`() = deletedTune()

    @Test
    @JvmName("tc108")
    fun `TC#108 숨기기·색·시작 위치가 적용되고 같은 호스트 링크로 이동해도 다시 적용된다`() = applyRules()

    @Test
    @JvmName("tc110")
    fun `TC#110 맨 아래에서 위 이전·아래 다음 버튼이 연결 요소를 누르고 한쪽 연결은 그 버튼만 보이며 버튼 위 링크가 열린다`() = navButtons()

    @Test
    @JvmName("tc111")
    fun `TC#111 위쪽 40% 두 번 탭은 위 연결을 누르고 가운데 두 번 탭은 아무것도 하지 않는다`() = doubleTap()

    @Test
    @JvmName("tc112")
    fun `TC#112 같은 호스트로 이동한 주소에서 다시 시작하고 다른 호스트로 이동하면 주소를 바꾸지 않는다`() = lastAddress()

    @Test
    @JvmName("tc113")
    fun `TC#113 페이지 로드가 끝나면 쿠키가 디스크에 내려가고 다시 열어도 남아 있다`() = cookies()

    @Test
    @JvmName("tc114")
    fun `TC#114 시스템 뒤로가기는 목록으로 돌아가고 빠른 메뉴 뒤로는 이전 페이지를 거쳐 목록으로 돌아간다`() = back()

    @Test
    @JvmName("tc115")
    fun `TC#115 핸들이 툴바를 열고 닫으며 4초 무조작이면 숨고 조작 중에는 남고 이전·다음 버튼·컷아웃과 겹치지 않는다`() = quickToolbar()

    @Test
    @JvmName("tc116")
    fun `TC#116 빠른 메뉴는 새로고침·현재 주소 공유를 하고 Tune 없는 페이지에서는 켜기·끄기가 비활성이고 만들기 항목이 된다`() = quickMenu()

    @Test
    @JvmName("tc117")
    fun `TC#117 Tune 끄기는 규칙을 멈추고 켜기는 다시 적용하며 끈 상태는 저장되지 않는다`() = toggleTune()

    @Test
    @JvmName("tc121")
    fun `TC#121 진입·켜기·끄기 때 Tune 상태를 보이고 상태 표시는 이전·다음 버튼을 가리지 않는다`() = tuneStatus()

    @Test
    @JvmName("tc140")
    fun `TC#140 이전·다음 버튼은 맨 아래에서만 보이고 회전·Tune 끄기에 따르며 흰 페이지 위에서 읽힌다`() = pageEndButtons()

    @Test
    @JvmName("tc162")
    fun `TC#162 맨 아래 이전·다음 버튼은 폭 전체이고 다음 글로 이동하며 당기기 안내는 버튼 위에 있다`() = stackedButtons()

    private fun fullScreen() {
        LocalPage(page(SHORT_BODY)).use { server ->
            val pageId = addWebPage("Page", server.url, addTune("Nav", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next")))))
            forEachNavMode { mode ->
                openViewer(pageId).use { scenario ->
                    eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
                    eventually {
                        scenario.onActivity { activity ->
                            assertFalse("$mode 상태바가 보임", ViewCompat.getRootWindowInsets(activity.window.decorView)!!.isVisible(Type.statusBars()))
                        }
                    }
                    assertAboveNavBar(scenario, mode)
                }
            }
        }
    }

    private fun forEachNavMode(block: (String) -> Unit) {
        val current = shell("cmd overlay list").lines().map { it.trim() }
            .firstOrNull { it.startsWith("[x] $NAV_PREFIX") }?.removePrefix("[x] ")
        assertNotNull("켜진 내비게이션 overlay 를 찾지 못해 되돌릴 수 없음", current)
        changeDeviceSetting("true", restore = "cmd overlay enable-exclusive --category $current")
        for (mode in listOf(NAV_THREE_BUTTON, NAV_GESTURAL)) {
            shell("cmd overlay enable-exclusive --category $mode")
            waitUntil(10_000) { shell("cmd overlay list").contains("[x] $mode") }
            block(mode)
        }
    }

    private fun assertAboveNavBar(scenario: ActivityScenario<WebBrowserActivity>, mode: String) = eventually {
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val nav = ViewCompat.getRootWindowInsets(decor)!!.getInsets(Type.navigationBars())
            assertTrue("$mode 내비게이션 바 높이 0", nav.bottom > 0)
            val stack = rectOf(activity.findViewById(R.id.layout_gnb_stack))
            assertTrue("$mode 버튼 $stack 이 내비게이션 바(${decor.height - nav.bottom} 아래)에 가림", stack.bottom <= decor.height - nav.bottom)
        }
    }

    private fun deletedTune() {
        LocalPage(page("<div id=\"ad\">AD</div>")).use { server ->
            val tune = addTune("Gone", listOf(hideRule("ad")))
            val pageId = addWebPage("Page", server.url, tune)
            runBlocking { database.pluginDao().deletePluginsByIds(listOf(tune)) }
            openViewer(pageId).use {
                val web = loaded()
                assertEquals("\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display"))
                onMain {
                    val activity = resumedActivity()
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.tv_tune_status).visibility)
                    assertFalse("Tune 없는 페이지의 켜기·끄기가 활성", activity.findViewById<View>(R.id.btn_quick_toggle).isEnabled)
                }
            }
        }
    }

    private fun applyRules() {
        val body = "<div id=\"ad\">AD</div><div id=\"box\">BOX</div><a id=\"go\" href=\"/second\">go</a><div style=\"height:5000px\"></div>"
        LocalPage(page(body)).use { server ->
            val tune = addTune(
                "Rules",
                listOf(
                    hideRule("ad"),
                    colorRule("box", "background-color:#D32F2F;color:#FFFFFF"),
                    com.hwanyu365.pagetune.db.PluginAction(pluginId = 0, actionType = "scroll", targetSelector = """{"y":$SCROLL_Y}"""),
                ),
            )
            openViewer(addWebPage("Page", server.url, tune)).use {
                val web = loaded()
                assertRulesApplied(web)
                web.js("document.getElementById('go').click()")
                eventually { assertEquals("\"/second\"", web.js("location.pathname")) }
                assertRulesApplied(web)
            }
        }
    }

    private fun assertRulesApplied(web: android.webkit.WebView) {
        eventually { assertEquals("\"none\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
        eventually { assertEquals("\"rgb(211, 47, 47)|rgb(255, 255, 255)\"", web.js("var s = getComputedStyle(document.getElementById('box')); s.backgroundColor + '|' + s.color")) }
        eventually { assertEquals("$SCROLL_Y", web.js("Math.round(window.pageYOffset)")) }
    }

    private fun navButtons() {
        val body = "<a id=\"link\" href=\"/linked\" style=\"display:block;padding:24px\">link</a>$NAV_LINKS"
        LocalPage(page(body)).use { server ->
            val both = addTune("Both", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next"))))
            openViewer(addWebPage("Both", server.url, both)).use {
                val web = loaded()
                eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
                onMain {
                    val activity = resumedActivity()
                    val prev = rectOf(activity.findViewById(R.id.btn_stack_prev))
                    val next = rectOf(activity.findViewById(R.id.btn_stack_next))
                    assertTrue("이전 $prev 이 다음 $next 위가 아님", prev.bottom <= next.top)
                }
                onView(withId(R.id.btn_stack_prev)).perform(click())
                eventually { assertEquals("\"/prev\"", web.js("location.pathname")) }
                web.js("history.back()")
                eventually { assertEquals("\"/\"", web.js("location.pathname")) }
                eventually { onView(withId(R.id.btn_stack_next)).check(matches(isDisplayed())) }
                onView(withId(R.id.btn_stack_next)).perform(click())
                eventually { assertEquals("\"/next\"", web.js("location.pathname")) }
            }
            val nextOnly = addTune("Next only", listOf(clickRule("gnb_next", sel("next"))))
            openViewer(addWebPage("Next only", server.url, nextOnly)).use {
                val web = loaded()
                eventually { onView(withId(R.id.btn_stack_next)).check(matches(isDisplayed())) }
                onView(withId(R.id.btn_stack_prev)).check(matches(not(isDisplayed())))
                val link = elementCenter(web, "link")
                onMain { assertTrue("링크가 버튼 영역에 있음", link.second < rectOf(resumedActivity().findViewById(R.id.layout_gnb_stack)).top) }
                onView(withId(R.id.wv_browser)).perform(tapOnScreen(link.first, link.second))
                eventually { assertEquals("\"/linked\"", web.js("location.pathname")) }
            }
        }
    }

    private fun doubleTap() {
        val body = "<a id=\"up\" href=\"/up\">up</a><a id=\"down\" href=\"/down\">down</a><div style=\"height:5000px\"></div>"
        LocalPage(page(body)).use { server ->
            val tune = addTune("Tap", listOf(clickRule("gesture_top", sel("up")), clickRule("gesture_bottom", sel("down"))))
            openViewer(addWebPage("Page", server.url, tune)).use {
                val web = loaded()
                web.js("window.scrollTo(0, 1000)")
                onView(withId(R.id.wv_browser)).perform(doubleTapAt(0.45f))
                SystemClock.sleep(1_000)
                assertEquals("\"/\"", web.js("location.pathname"))
                onView(withId(R.id.wv_browser)).perform(doubleTapAt(0.35f))
                eventually { assertEquals("\"/up\"", web.js("location.pathname")) }
            }
        }
    }

    private fun lastAddress() {
        val body = "<a id=\"same\" href=\"/moved\">same</a>"
        LocalPage(page(body)).use { server ->
            val pageId = addWebPage("Page", server.url)
            openViewer(pageId).use {
                val web = loaded()
                web.js("document.getElementById('same').click()")
                eventually { assertEquals("\"/moved\"", web.js("location.pathname")) }
            }
            val moved = "${server.url}moved"
            eventually { assertEquals(moved, storedUrl(pageId)) }
            openViewer(pageId).use {
                val web = loaded()
                assertEquals("\"$moved\"", web.js("location.href"))
                val other = moved.replace("127.0.0.1", "localhost") + "-other"
                web.js("location.href = '$other'")
                eventually { assertEquals("\"localhost\"", web.js("location.hostname")) }
                SystemClock.sleep(1_000)
            }
            assertEquals(moved, storedUrl(pageId))
            openViewer(pageId).use { assertEquals("\"$moved\"", loaded().js("location.href")) }
        }
    }

    private fun cookies() {
        val value = "tc113-${SystemClock.uptimeMillis()}"
        val body = "<script>if (!document.cookie.includes('login=')) document.cookie = 'login=$value; max-age=86400; path=/';</script>"
        LocalPage(page(body)).use { server ->
            val pageId = addWebPage("Page", server.url)
            openViewer(pageId).use {
                loaded()
                eventually(timeoutMs = 3_000) { assertTrue("쿠키가 디스크에 없음", cookieOnDisk(value)) }
            }
            openViewer(pageId).use {
                val web = loaded()
                assertTrue(web.js("document.cookie").contains("login=$value"))
                assertTrue(onMain { CookieManager.getInstance().getCookie(server.url) }.orEmpty().contains("login=$value"))
            }
        }
    }

    // 디스크에 내렸는지는 쿠키 저장 파일에서만 보인다 → 화면을 다시 열어 읽는 값은 메모리에 남은 쿠키여도 같다.
    private fun cookieOnDisk(value: String): Boolean {
        val dir = File(targetContext.dataDir, "app_webview/Default")
        val files = dir.listFiles().orEmpty().filter { it.name.startsWith("Cookies") }
        return files.any { file -> String(file.readBytes(), Charsets.ISO_8859_1).contains(value) }
    }

    private fun back() {
        val body = "<a id=\"go\" href=\"#\" style=\"display:block;padding:24px\" onclick=\"location.href = (location.pathname === '/' ? '' : location.pathname) + '/x'; return false\">go</a>"
        LocalPage(page(body)).use { server ->
            addWebPage("Back Page", server.url)
            ActivityScenario.launch(MainActivity::class.java).use {
                openFromList("Back Page")
                twoMoves()
                pressBack()
                eventually { onView(withText("Back Page")).check(matches(isDisplayed())) }

                openFromList("Back Page")
                val (web, paths) = twoMoves()
                quickBack()
                eventually { assertEquals("\"${paths[1]}\"", web.js("location.pathname")) }
                quickBack()
                eventually { assertEquals("\"${paths[0]}\"", web.js("location.pathname")) }
                quickBack()
                eventually { onView(withText("Back Page")).check(matches(isDisplayed())) }
            }
        }
    }

    private fun openFromList(title: String) {
        eventually { onView(withText(title)).perform(click()) }
        eventually { assertTrue(resumedActivity() is WebBrowserActivity) }
    }

    private fun twoMoves(): Pair<android.webkit.WebView, List<String>> {
        val web = loaded()
        val paths = mutableListOf(web.js("location.pathname").trim('"'))
        repeat(2) {
            val expected = paths.last().trimEnd('/') + "/x"
            // 사용자 조작 없이 만든 기록은 뒤로가기가 건너뛴다 → 실제 탭으로 누른다.
            val link = elementCenter(web, "go")
            onView(withId(R.id.wv_browser)).perform(tapOnScreen(link.first, link.second))
            eventually { assertEquals("\"$expected\"", web.js("location.pathname")) }
            eventually { assertEquals("\"complete\"", web.js("document.readyState")) }
            paths += expected
        }
        return web to paths
    }

    private fun quickBack() {
        openToolbar()
        onView(withId(R.id.btn_quick_back)).perform(click())
    }

    private fun quickToolbar() {
        changeDeviceSetting("cmd overlay enable $CUTOUT_CORNER", restore = "cmd overlay disable $CUTOUT_CORNER")
        waitUntil(10_000) { shell("cmd overlay list").contains("[x] $CUTOUT_CORNER") }
        LocalPage(page(SHORT_BODY)).use { server ->
            val pageId = addWebPage("Page", server.url, addTune("Nav", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next")))))
            openViewer(pageId).use { scenario ->
                loaded()
                eventually {
                    scenario.onActivity { activity ->
                        val cutout = ViewCompat.getRootWindowInsets(activity.window.decorView)!!.displayCutout
                        assertTrue("컷아웃 없음", cutout != null && cutout.boundingRects.isNotEmpty())
                        val handle = rectOf(activity.findViewById(R.id.btn_quick_handle))
                        for (r in cutout!!.boundingRects) assertFalse("핸들 $handle 이 컷아웃 $r 에 가림", Rect.intersects(handle, r))
                    }
                }

                onView(withId(R.id.btn_quick_handle)).perform(click())
                onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
                onView(withId(R.id.btn_quick_handle)).perform(click())
                onView(withId(R.id.layout_quick_toolbar)).check(matches(not(isDisplayed())))

                onView(withId(R.id.btn_quick_handle)).perform(click())
                eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
                onMain {
                    val activity = resumedActivity()
                    val toolbar = rectOf(activity.findViewById(R.id.layout_quick_toolbar))
                    val stack = rectOf(activity.findViewById(R.id.layout_gnb_stack))
                    assertFalse("툴바 $toolbar 와 이전·다음 버튼 $stack 이 겹침", Rect.intersects(toolbar, stack))
                }
                val hide = toolbarTimeoutMs()
                SystemClock.sleep(hide / 2)
                onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
                eventually(timeoutMs = hide) { onView(withId(R.id.layout_quick_toolbar)).check(matches(not(isDisplayed()))) }

                onView(withId(R.id.btn_quick_handle)).perform(click())
                SystemClock.sleep(hide * 5 / 8)
                onView(withId(R.id.btn_quick_refresh)).perform(click())
                SystemClock.sleep(hide * 5 / 8)
                onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
                eventually(timeoutMs = hide) { onView(withId(R.id.layout_quick_toolbar)).check(matches(not(isDisplayed()))) }
            }
        }
    }

    // API 29 이상은 4초를 접근성 "조치를 취할 시간" 권장값으로 늘린다 (FR#14).
    private fun toolbarTimeoutMs(): Long {
        val base = 4_000
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return base.toLong()
        val manager = targetContext.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        return manager.getRecommendedTimeoutMillis(base, android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong()
    }

    private fun quickMenu() {
        LocalPage(page("<p>page</p>")).use { server ->
            val pageId = addWebPage("Page", server.url)
            openViewer(pageId).use {
                val web = loaded()
                web.js("window.tc116 = 1")
                openToolbar()
                onView(withId(R.id.btn_quick_refresh)).perform(click())
                eventually { assertEquals("\"undefined|complete\"", web.js("typeof window.tc116 + '|' + document.readyState")) }

                onMain {
                    val activity = resumedActivity()
                    assertFalse("켜기·끄기가 활성", activity.findViewById<View>(R.id.btn_quick_toggle).isEnabled)
                    assertEquals(activity.getString(R.string.quick_create_tune), activity.findViewById<View>(R.id.btn_quick_edit).contentDescription)
                }

                // 다른 호스트로 옮기면 저장 주소는 그대로다 → 공유 주소가 저장 주소가 아니라 지금 주소인지 가릴 수 있다.
                val current = server.url.replace("127.0.0.1", "localhost") + "current"
                web.js("location.href = '$current'")
                eventually { assertEquals("\"$current\"", web.js("location.href")) }
                assertEquals(server.url, storedUrl(pageId))
                Intents.init()
                try {
                    intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
                    openToolbar()
                    onView(withId(R.id.btn_quick_share)).perform(click())
                    intended(allOf(hasAction(Intent.ACTION_CHOOSER), hasExtra(org.hamcrest.Matchers.equalTo(Intent.EXTRA_INTENT), allOf(hasAction(Intent.ACTION_SEND), hasExtra(Intent.EXTRA_TEXT, current)))))
                } finally {
                    Intents.release()
                }
            }
        }
    }

    private fun toggleTune() {
        val body = "<div id=\"ad\">AD</div><div id=\"box\">BOX</div><a id=\"up\" href=\"/up\">up</a>$NAV_LINKS"
        LocalPage(page(body)).use { server ->
            val tune = addTune(
                "Toggle",
                listOf(
                    hideRule("ad"),
                    colorRule("box", "background-color:#D32F2F;color:#FFFFFF"),
                    clickRule("gnb_next", sel("next")),
                    clickRule("gesture_top", sel("up")),
                ),
            )
            val pageId = addWebPage("Page", server.url, tune)
            var saved: List<Any?> = emptyList()
            openViewer(pageId).use {
                val web = loaded()
                assertTuneOn(web)
                saved = savedState(tune, pageId)

                openToolbar()
                onView(withId(R.id.btn_quick_toggle)).perform(click())
                eventually { assertEquals("\"block|rgba(0, 0, 0, 0)\"", web.js("getComputedStyle(document.getElementById('ad')).display + '|' + getComputedStyle(document.getElementById('box')).backgroundColor")) }
                eventually { assertEquals("\"complete\"", web.js("document.readyState")) }
                SystemClock.sleep(1_000)
                onView(withId(R.id.layout_gnb_stack)).check(matches(not(isDisplayed())))
                // 열린 툴바가 위쪽을 덮으면 두 번 탭이 페이지가 아니라 툴바 버튼에 간다.
                onView(withId(R.id.btn_quick_handle)).perform(click())
                onView(withId(R.id.layout_quick_toolbar)).check(matches(not(isDisplayed())))
                onView(withId(R.id.wv_browser)).perform(doubleTapAt(0.1f))
                SystemClock.sleep(1_000)
                assertEquals("\"/\"", web.js("location.pathname"))
                assertEquals(saved, savedState(tune, pageId))

                openToolbar()
                onView(withId(R.id.btn_quick_toggle)).perform(click())
                assertTuneOn(web)
                openToolbar()
                onView(withId(R.id.btn_quick_toggle)).perform(click())
                eventually { assertEquals("\"block\"", web.js("getComputedStyle(document.getElementById('ad')).display")) }
            }
            openViewer(pageId).use { assertTuneOn(loaded()) }
            assertEquals(saved, savedState(tune, pageId))
        }
    }

    private fun assertTuneOn(web: android.webkit.WebView) {
        eventually { assertEquals("\"none|rgb(211, 47, 47)\"", web.js("getComputedStyle(document.getElementById('ad')).display + '|' + getComputedStyle(document.getElementById('box')).backgroundColor")) }
        eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
    }

    // 끈 상태가 저장되면 Tune·규칙 행이나 앱 설정 파일 중 하나가 바뀐다.
    private fun savedState(tune: Long, pageId: Long): List<Any?> {
        // WebView 는 방문 기록 통계를 자기 설정 파일에 계속 쓴다 → 앱이 쓰는 파일만 비교한다.
        val prefs = File(targetContext.dataDir, "shared_prefs").listFiles().orEmpty()
            .filterNot { it.name.startsWith("Aw") || it.name.startsWith("WebView") }
            .sortedBy { it.name }.map { it.name to it.readText() }
        return listOf(runBlocking { database.pluginDao().getPluginWithActions(tune) }, runBlocking { database.webPageDao().getWebPageById(pageId) }, prefs)
    }

    private fun tuneStatus() {
        LocalPage(page(SHORT_BODY)).use { server ->
            val counted = addTune("Counted", listOf(hideRule("a"), hideRule("b"), colorRule("c", "color:#FFFFFF")))
            // 상태 표시는 진입 3초 뒤 사라진다 → 페이지 로드를 기다리지 않고 읽는다.
            openViewer(addWebPage("Counted", server.url, counted)).use {
                val expected = onMain {
                    val a = resumedActivity()
                    a.getString(R.string.tune_status_on, listOf(a.getString(R.string.tune_kind_hide, 2), a.getString(R.string.tune_kind_color, 1)).joinToString(", "))
                }
                eventually { assertEquals(expected, statusText()) }
                loaded()
                openToolbar()
                onView(withId(R.id.btn_quick_toggle)).perform(click())
                eventually { assertEquals(onMain { resumedActivity().getString(R.string.tune_status_off) }, statusText()) }
            }

            openViewer(addWebPage("Empty", server.url, addTune("Empty"))).use {
                eventually { assertEquals(onMain { resumedActivity().getString(R.string.tune_status_no_rules) }, statusText()) }
            }

            openViewer(addWebPage("None", server.url)).use {
                loaded()
                SystemClock.sleep(1_000)
                assertEquals(View.GONE, onMain { resumedActivity().findViewById<View>(R.id.tv_tune_status).visibility })
            }

            val nav = addTune("Nav", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next"))))
            openViewer(addWebPage("Nav", server.url, nav)).use {
                val web = loaded()
                eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
                // 상태 표시는 진입 3초 뒤 숨는다 → 페이지 로드가 늦어도 "표시 중" 배치를 보도록 다시 보이게 한다.
                onMain { resumedActivity().findViewById<View>(R.id.tv_tune_status).visibility = View.VISIBLE }
                onView(withId(R.id.tv_tune_status)).check(matches(isDisplayed()))
                onMain {
                    val activity = resumedActivity()
                    val status = rectOf(activity.findViewById(R.id.tv_tune_status))
                    val stack = rectOf(activity.findViewById(R.id.layout_gnb_stack))
                    assertFalse("상태 $status 가 이전·다음 버튼 $stack 을 가림", Rect.intersects(status, stack))
                }
                onView(withId(R.id.btn_stack_next)).perform(click())
                eventually { assertEquals("\"/next\"", web.js("location.pathname")) }
            }
        }
    }

    private fun statusText(): String = onMain {
        val view = resumedActivity().findViewById<TextView>(R.id.tv_tune_status)
        if (view.visibility == View.VISIBLE) view.text.toString() else ""
    }

    private fun pageEndButtons() {
        LocalPage(page("<div style=\"height:3000px\">long</div>$NAV_LINKS")).use { long ->
            val tune = addTune("Nav", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next"))))
            openViewer(addWebPage("Long", long.url, tune)).use { scenario ->
                val web = loaded()
                SystemClock.sleep(1_000)
                stackShown(false)
                web.js("window.scrollTo(0, document.body.scrollHeight)")
                stackShown(true)
                assertTwoRows()
                assertReadable()
                web.js("window.scrollBy(0, -400)")
                stackShown(false)

                lockRotation()
                web.js("window.scrollTo(0, document.body.scrollHeight)")
                stackShown(true)
                rotate(1)
                eventually { scenario.onActivity { assertTrue(it.window.decorView.width > it.window.decorView.height) } }
                stackShown(false)
                web.js("window.scrollTo(0, document.body.scrollHeight)")
                stackShown(true)
                rotate(0)
                eventually { scenario.onActivity { assertTrue(it.window.decorView.width < it.window.decorView.height) } }

                web.js("window.scrollTo(0, document.body.scrollHeight)")
                stackShown(true)
                openToolbar()
                onView(withId(R.id.btn_quick_toggle)).perform(click())
                eventually { assertEquals("\"complete\"", web.js("document.readyState")) }
                web.js("window.scrollTo(0, document.body.scrollHeight)")
                SystemClock.sleep(1_000)
                stackShown(false)
            }
            // 내비게이션 모드를 바꾸면 열린 화면이 다시 만들어진다 → 모드마다 새로 연다.
            forEachNavMode { mode ->
                openViewer(addWebPage("Long $mode", long.url, tune)).use { scenario ->
                    loaded().js("window.scrollTo(0, document.body.scrollHeight)")
                    stackShown(true)
                    assertAboveNavBar(scenario, mode)
                }
            }
        }
        LocalPage(page(SHORT_BODY)).use { short ->
            val tune = addTune("Short", listOf(clickRule("gnb_next", sel("next"))))
            openViewer(addWebPage("Short", short.url, tune)).use {
                loaded()
                stackShown(true)
            }
        }
    }

    private fun assertTwoRows() = onMain {
        val activity = resumedActivity()
        val prev = activity.findViewById<MaterialButton>(R.id.btn_stack_prev)
        val next = activity.findViewById<MaterialButton>(R.id.btn_stack_next)
        assertTrue("이전·다음이 함께 보이지 않음", prev.isShown && next.isShown)
        assertEquals("이전과 다음 사이 여백", rectOf(prev).bottom, rectOf(next).top)
    }

    private fun stackShown(shown: Boolean) = eventually {
        onView(withId(R.id.layout_gnb_stack)).check(matches(if (shown) isDisplayed() else not(isDisplayed())))
    }

    // 버튼 뒤 페이지 색은 앱이 정할 수 없다 → 가장 밝은 흰 페이지 위 합성색으로 대비를 잰다 (spec_design.md NFR#1).
    private fun assertReadable() = onMain {
        val activity = resumedActivity()
        for (id in listOf(R.id.btn_stack_prev, R.id.btn_stack_next)) {
            val button = activity.findViewById<MaterialButton>(id)
            val background = ColorUtils.compositeColors(button.backgroundTintList!!.defaultColor, Color.WHITE)
            val text = ColorUtils.calculateContrast(button.currentTextColor, background)
            val icon = ColorUtils.calculateContrast(button.iconTint!!.defaultColor, background)
            assertTrue("글자 대비 $text", text >= 4.5)
            assertTrue("아이콘 대비 $icon", icon >= 3.0)
        }
        assertEquals(ContextCompat.getColor(activity, R.color.scrim), activity.findViewById<MaterialButton>(R.id.btn_stack_next).backgroundTintList!!.defaultColor)
    }

    private fun stackedButtons() {
        val locales = "cmd locale set-app-locales ${targetContext.packageName} --user 0"
        changeDeviceSetting("$locales --locales en", restore = locales)
        lockRotation()
        LocalPage(page("<div style=\"height:3000px\">long</div>$NAV_LINKS")).use { server ->
            val tune = addTune("Stack", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next")), clickRule("pull_bottom", sel("next"))))
            val pageId = addWebPage("Stack", server.url, tune)
            val labels = mutableListOf<String>()
            for (tag in listOf("en", "ko")) {
                shell("$locales --locales $tag")
                waitUntil { targetContext.applicationContext.resources.configuration.locales[0].language == tag }
                rotate(0)
                openViewer(pageId).use { scenario ->
                    val web = loaded()
                    // 런처가 앞에 있으면 회전 설정이 반영되지 않는다 → 열람 화면을 연 채로 돌린다.
                    for (rotation in listOf(0, 1)) {
                        rotate(rotation)
                        eventually { scenario.onActivity { assertEquals(rotation == 1, it.window.decorView.width > it.window.decorView.height) } }
                        web.js("window.scrollTo(0, document.body.scrollHeight)")
                        stackShown(true)
                        labels += checkStack("$tag/$rotation")
                        checkPullHint(web, "$tag/$rotation")
                    }
                    rotate(0)
                    onView(withId(R.id.btn_stack_next)).perform(click())
                    eventually { assertEquals("\"/next\"", web.js("location.pathname")) }
                }
            }
            assertNotEquals("영어·한국어 버튼 글자가 같음", labels.take(4), labels.drop(4))
        }
    }

    // 회전 설정값을 쓰기만 하면 화면 회전에 반영되지 않을 때가 있다 → 화면 관리자에 직접 고정한다.
    private fun lockRotation() {
        val old = shell("cmd window user-rotation").trim()
        changeDeviceSetting("cmd window user-rotation lock 0", restore = "cmd window user-rotation $old")
    }

    private fun rotate(rotation: Int) {
        shell("cmd window user-rotation lock $rotation")
    }

    private fun checkStack(at: String): List<String> = onMain {
        val activity = resumedActivity()
        val root = activity.findViewById<View>(R.id.wv_browser).parent as View
        val content = Rect(root.paddingLeft, 0, root.width - root.paddingRight, 0).also { r -> IntArray(2).also(root::getLocationOnScreen).let { r.offset(it[0], 0) } }
        val prev = activity.findViewById<MaterialButton>(R.id.btn_stack_prev)
        val next = activity.findViewById<MaterialButton>(R.id.btn_stack_next)
        for (button in listOf(prev, next)) {
            val r = rectOf(button)
            assertEquals("$at 버튼 폭 $r", content.left to content.right, r.left to r.right)
            assertEquals("$at 버튼 모서리", 0, button.cornerRadius)
        }
        assertEquals("$at 이전이 다음 바로 위가 아님", rectOf(prev).bottom, rectOf(next).top)
        assertEquals(activity.getString(R.string.browser_prev), prev.text.toString())
        assertEquals(activity.getString(R.string.browser_next), next.text.toString())
        listOf(prev.text.toString(), next.text.toString())
    }

    // 손가락을 멈춘 채 안내를 확인하고 취소로 끝낸다 → 떼지 않으므로 이동하지 않는다.
    private fun checkPullHint(web: android.webkit.WebView, at: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val (x, startY) = onMain { val r = rectOf(web); r.centerX().toFloat() to (r.top + r.height() * 0.4f) }
        val density = targetContext.resources.displayMetrics.density
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) = instrumentation.sendPointerSync(MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN })
        send(MotionEvent.ACTION_DOWN, startY)
        for (step in 1..10) send(MotionEvent.ACTION_MOVE, startY - step * 12 * density)
        SystemClock.sleep(500)
        try {
            eventually(timeoutMs = 2_000) {
                onMain {
                    val activity = resumedActivity()
                    val hint = activity.findViewById<TextView>(R.id.tv_pull_bottom)
                    assertTrue("$at 당기기 안내가 안 보임", hint.isShown)
                    assertEquals(activity.getString(R.string.browser_pull_release), hint.text.toString())
                    val stack = rectOf(activity.findViewById(R.id.layout_gnb_stack))
                    assertTrue("$at 안내 ${rectOf(hint)} 가 버튼 영역 $stack 위가 아님", rectOf(hint).bottom <= stack.top)
                }
            }
        } finally {
            send(MotionEvent.ACTION_CANCEL, startY - 120 * density)
        }
    }

    private fun openViewer(pageId: Long): ActivityScenario<WebBrowserActivity> =
        ActivityScenario.launch(Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId))

    private fun loaded(): android.webkit.WebView {
        val web = webView(R.id.wv_browser)
        eventually { assertEquals("\"complete\"", web.js("document.body ? document.readyState : ''")) }
        return web
    }

    private fun openToolbar() {
        if (onMain { resumedActivity().findViewById<View>(R.id.layout_quick_toolbar).isShown }) return
        onView(withId(R.id.btn_quick_handle)).perform(click())
        onView(withId(R.id.layout_quick_toolbar)).check(matches(isDisplayed()))
    }

    private fun storedUrl(pageId: Long) = runBlocking { database.webPageDao().getWebPageById(pageId) }!!.url

    private fun elementCenter(web: android.webkit.WebView, id: String): Pair<Float, Float> {
        val raw = web.js("var r = document.getElementById('$id').getBoundingClientRect(); (r.left + r.width / 2) + ',' + (r.top + r.height / 2)").trim('"')
        val (cx, cy) = raw.split(",").map { it.toFloat() }
        return onMain {
            val at = IntArray(2).also(web::getLocationOnScreen)
            @Suppress("DEPRECATION")
            val scale = web.scale
            (at[0] + cx * scale) to (at[1] + cy * scale)
        }
    }

    private fun tapOnScreen(x: Float, y: Float) = GeneralClickAction(Tap.SINGLE, { floatArrayOf(x, y) }, Press.FINGER, InputDevice.SOURCE_UNKNOWN, MotionEvent.BUTTON_PRIMARY)

    private fun doubleTapAt(fractionY: Float) = GeneralClickAction(
        Tap.DOUBLE,
        { v -> IntArray(2).also(v::getLocationOnScreen).let { floatArrayOf(it[0] + v.width / 2f, it[1] + v.height * fractionY) } },
        Press.FINGER,
        InputDevice.SOURCE_UNKNOWN,
        MotionEvent.BUTTON_PRIMARY,
    )

    private fun rectOf(view: View): Rect {
        val at = IntArray(2).also(view::getLocationOnScreen)
        return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    private fun sel(id: String) = """{"selector":"#$id","tagName":"a","id":"$id","attributes":{}}"""

    private fun page(body: String) =
        "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body style=\"margin:0;background:#fff;color:#000\">$body</body></html>"

    private companion object {
        const val SCROLL_Y = 600
        const val NAV_LINKS = "<a id=\"prev\" href=\"/prev\">prev</a> <a id=\"next\" href=\"/next\">next</a>"
        const val SHORT_BODY = "<p>short</p>$NAV_LINKS"
        const val NAV_PREFIX = "com.android.internal.systemui.navbar."
        const val NAV_THREE_BUTTON = "${NAV_PREFIX}threebutton"
        const val NAV_GESTURAL = "${NAV_PREFIX}gestural"
        const val CUTOUT_CORNER = "com.android.internal.display.cutout.emulation.corner"
    }
}
