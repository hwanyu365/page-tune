package com.hwanyu365.pagetune

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasPackage
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLEncoder

@RunWith(AndroidJUnit4::class)
class PageLinksTest {

    @Before
    fun setUp() {
        resetApp()
        Intents.init()
        // 설치되지 않은 앱은 실제로 실행을 시도해야 ActivityNotFoundException 경로를 탄다.
        // ActivityScenario 가 화면을 닫을 때 쓰는 테스트 APK 의 화면까지 막으면 닫기가 시간 초과까지 기다린다 → 외부로 나가는 보기 요청만 막는다.
        intending(allOf(hasAction(Intent.ACTION_VIEW), not(hasPackage(MISSING)))).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        restoreDeviceSettings()
    }

    @Test
    @JvmName("tc147")
    fun `TC#147 외부 앱 링크는 확인 뒤에만 허용된 intent 로 열리고 막을 주소와 사용자 조작 없는 이동은 아무것도 열지 않는다`() = externalLinks()

    @Test
    @JvmName("tc153")
    fun `TC#153 첨부 응답은 스낵바를 눌렀을 때만 브라우저로 넘기고 http 가 아닌 다운로드는 넘기지 않는다`() = downloads()

    @Test
    @JvmName("tc158")
    fun `TC#158 새 창으로 열리는 연결 대상은 같은 WebView 에서 열리고 외부 주소는 같은 분류를 거친다`() = connectedNewWindows()

    @Test
    @JvmName("tc46")
    fun `TC#46 편집 화면도 같은 링크 분류를 따르고 요소 선택 중 링크 탭은 이동하지 않는다`() = editorLinks()

    private fun externalLinks() = fixture().use { server ->
        viewer(server.url) { web ->
            tapIn(web, "mail")
            prompt("a@example.com")
            pressDialog(R.string.cancel)
            nothingOpened(web, server.url)

            tapIn(web, "mail")
            prompt("a@example.com")
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|mailto:a@example.com|null|[android.intent.category.BROWSABLE]"), sentSinceLast())

            tapIn(web, "tel")
            prompt("0123456789")
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|tel:0123456789|null|[android.intent.category.BROWSABLE]"), sentSinceLast())

            val settings = onMain { resumedActivity().packageManager.run { getApplicationLabel(getApplicationInfo("com.android.settings", 0)).toString() } }
            tapIn(web, "app")
            prompt(settings)
            prompt("https://settings/x")
            prompt(onMain { resumedActivity().getString(R.string.external_no_fallback) })
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|https://settings/x|com.android.settings|[android.intent.category.BROWSABLE]"), sentSinceLast())

            tapIn(web, "missing")
            prompt(MISSING)
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|pagetunetest://x|$MISSING|[android.intent.category.BROWSABLE]"), sentSinceLast())
            nothingOpened(web, server.url)

            tapIn(web, "file")
            nothingOpened(web, server.url)
            tapIn(web, "framed", frame = "frame")
            nothingOpened(web, server.url)

            for ((id, target) in listOf("redirectIntent" to "com.android.settings", "redirectMarket" to "market://details?id=com.example")) {
                tapIn(web, id)
                prompt(if (id == "redirectIntent") settings else target)
                pressDialog(R.string.cancel)
                nothingOpened(web, server.url)
            }
            tapIn(web, "redirectIntent")
            prompt(settings)
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|https://settings/x|com.android.settings|[android.intent.category.BROWSABLE]"), sentSinceLast())
            tapIn(web, "redirectMarket")
            prompt("market://details?id=com.example")
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|market://details?id=com.example|null|[android.intent.category.BROWSABLE]"), sentSinceLast())

            tapIn(web, "scriptIntent")
            prompt(settings)
            pressDialog(R.string.cancel)

            server.requested.clear()
            tapIn(web, "twice")
            prompt("first@example.com")
            eventually { assertTrue("두 번째 이동이 일어나지 않음", "/r-market" in server.requested) }
            Thread.sleep(500)
            onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(not(withText(containsString("market:")))))
            pressDialog(R.string.cancel)
            nothingOpened(web, server.url)

            tapIn(web, "openContent")
            nothingOpened(web, server.url)
            tapIn(web, "openFile")
            nothingOpened(web, server.url)

            tapIn(web, "framedOpen", frame = "frame")
            prompt(settings)
            pressDialog(R.string.cancel)
            nothingOpened(web, server.url)

            tapIn(web, "blankMail")
            prompt("b@example.com")
            pressDialog(R.string.cancel)

            tapIn(web, "blankHttps")
            eventually { assertEquals("\"/blank\"", web.js("location.pathname")) }
            assertTrue(sentSinceLast().isEmpty())
        }

        viewer(server.url) { web ->
            tapIn(web, "openHttps")
            eventually { assertEquals("\"/opened\"", web.js("location.pathname")) }
            assertTrue(sentSinceLast().isEmpty())
        }

        viewer(server.url) { web ->
            tapIn(web, "missingFallback")
            prompt(server.url + "fallback")
            pressDialog(R.string.external_confirm_open)
            eventually { assertEquals("\"/fallback\"", web.js("location.pathname")) }
            assertEquals(listOf("android.intent.action.VIEW|pagetunetest://x|$MISSING|[android.intent.category.BROWSABLE]"), sentSinceLast())
        }

        for (path in listOf("auto", "frame-auto")) {
            viewer(server.url + path, keepLoadIntents = true) { web ->
                Thread.sleep(2_000)
                nothingOpened(web, server.url + path)
            }
        }
    }

    private fun downloads() = fixture().use { server ->
        viewer(server.url) { web ->
            tapIn(web, "attach")
            eventually { onView(withText(R.string.download_prompt)).check(matches(isDisplayed())) }
            onView(withText(R.string.download_open_in_browser)).perform(click())
            assertEquals(listOf("android.intent.action.VIEW|${server.url}file.bin|null|[android.intent.category.BROWSABLE]"), sentSinceLast())
            assertEquals("\"/\"", web.js("location.pathname"))

            tapIn(web, "attach")
            eventually { onView(withText(R.string.download_prompt)).check(matches(isDisplayed())) }
            eventually(timeoutMs = 20_000) { onView(withText(R.string.download_prompt)).check(doesNotExist()) }
            assertTrue(sentSinceLast().isEmpty())

            tapIn(web, "blob")
            Thread.sleep(1_000)
            assertTrue("blob 다운로드에 브라우저 스낵바", onMain { resumedActivity().findViewById<android.view.View>(com.google.android.material.R.id.snackbar_text) == null })
            assertTrue(sentSinceLast().isEmpty())
        }

        viewer(server.url + "frame-download", keepLoadIntents = true) { web ->
            Thread.sleep(2_000)
            assertTrue(sentSinceLast().isEmpty())
            assertEquals("\"/frame-download\"", web.js("location.pathname"))
        }

        Intents.release()
        Intents.init()
        changeDeviceSetting("pm disable-user --user 0 $BROWSER", restore = "pm enable --user 0 $BROWSER")
        viewer(server.url) { web ->
            tapIn(web, "attach")
            eventually { onView(withText(R.string.download_prompt)).check(matches(isDisplayed())) }
            onView(withText(R.string.download_open_in_browser)).perform(click())
            Thread.sleep(1_000)
            assertEquals("열기를 시도하지 않음", listOf("android.intent.action.VIEW|${server.url}file.bin|null|[android.intent.category.BROWSABLE]"), sentSinceLast())
            assertTrue("브라우저 없이 열기 뒤 열람 화면을 떠남", onMain { resumedActivity() is WebBrowserActivity })
            assertEquals("\"/\"", web.js("location.pathname"))
        }
    }

    private fun connectedNewWindows() = fixture().use { server ->
        val rules = listOf(
            clickRule("gnb_next", target("a", "blankHttps")),
            clickRule("gnb_prev", target("button", "openHttps")),
            clickRule("gesture_top", target("a", "blankTop")),
        )
        viewer(server.url, rules) { web ->
            web.js("window.scrollTo(0, document.body.scrollHeight)")
            eventually { onView(withId(R.id.btn_stack_next)).check(matches(isDisplayed())) }
            onView(withId(R.id.btn_stack_next)).perform(click())
            eventually { assertEquals("\"/blank\"", web.js("location.pathname")) }
        }
        viewer(server.url, rules) { web ->
            web.js("window.scrollTo(0, document.body.scrollHeight)")
            eventually { onView(withId(R.id.btn_stack_prev)).check(matches(isDisplayed())) }
            onView(withId(R.id.btn_stack_prev)).perform(click())
            eventually { assertEquals("\"/opened\"", web.js("location.pathname")) }
        }
        viewer(server.url, rules) { web ->
            onView(withId(R.id.wv_browser)).perform(doubleTapAt(0.1f))
            eventually { assertEquals("\"/top\"", web.js("location.pathname")) }
            assertTrue(sentSinceLast().isEmpty())
        }
        for ((id, expectPrompt) in listOf("blankMail" to true, "blankFile" to false)) {
            viewer(server.url, listOf(clickRule("gnb_next", target("a", id)))) { web ->
                web.js("window.scrollTo(0, document.body.scrollHeight)")
                eventually { onView(withId(R.id.btn_stack_next)).check(matches(isDisplayed())) }
                onView(withId(R.id.btn_stack_next)).perform(click())
                if (expectPrompt) {
                    prompt("b@example.com")
                    pressDialog(R.string.cancel)
                }
                nothingOpened(web, server.url)
            }
        }
    }

    private fun editorLinks() = fixture().use { server ->
        ActivityScenario.launch(PluginEditActivity::class.java).use {
            val web = loadInEditor(server.url)
            tapIn(web, "mail")
            prompt("a@example.com")
            pressDialog(R.string.external_confirm_open)
            assertEquals(listOf("android.intent.action.VIEW|mailto:a@example.com|null|[android.intent.category.BROWSABLE]"), sentSinceLast())
            tapIn(web, "file")
            nothingOpened(web, server.url)
            tapIn(web, "openContent")
            nothingOpened(web, server.url)

            inspectOn(web)
            tapIn(web, "mail")
            eventually { assertEquals("#mail", textOf(R.id.tv_selector_detail)) }
            nothingOpened(web, server.url)
        }
    }

    private var seen = 0

    private fun sentSinceLast(): List<String> {
        val all = Intents.getIntents().filter { it.action == Intent.ACTION_VIEW }
        val new = all.drop(seen)
        seen = all.size
        return new.map { "${it.action}|${it.dataString}|${it.`package`}|${it.categories?.sorted()}" }
    }

    private fun nothingOpened(web: WebView, url: String) {
        Thread.sleep(1_000)
        assertTrue("확인 창이 떠 있음", onMain { resumedActivity().window.decorView.hasWindowFocus() })
        assertEquals("다른 앱으로 넘김", emptyList<String>(), sentSinceLast())
        assertEquals("페이지가 바뀜", "\"$url\"", web.js("location.href"))
    }

    private fun prompt(subject: String) {
        eventually { onView(withText(R.string.external_confirm_title)).inRoot(isDialog()).check(matches(isDisplayed())) }
        onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText(containsString(subject))))
    }

    private fun pressDialog(button: Int) {
        onView(withText(button)).inRoot(isDialog()).perform(click())
        eventually { assertTrue(onMain { resumedActivity().window.decorView.hasWindowFocus() }) }
    }

    // 링크는 화면 왼쪽 60% 에만 둔다 → 오른쪽 빈 곳을 두 번 탭해야 첫 탭이 링크를 누르지 않는다.
    private fun doubleTapAt(fractionY: Float) = GeneralClickAction(
        Tap.DOUBLE,
        { v -> IntArray(2).also(v::getLocationOnScreen).let { floatArrayOf(it[0] + v.width * 0.85f, it[1] + v.height * fractionY) } },
        Press.FINGER,
        InputDevice.SOURCE_UNKNOWN,
        MotionEvent.BUTTON_PRIMARY,
    )

    // 사용자 탭이어야 WebView 가 이동에 제스처를 붙인다 → 스크립트 click() 이 아니라 그 요소 위치에 터치를 보낸다.
    private fun tapIn(web: WebView, id: String, frame: String? = null) {
        val doc = if (frame == null) "document" else "document.getElementById('$frame').contentDocument"
        web.js("$doc.getElementById('$id').scrollIntoView({block: 'center'})")
        Thread.sleep(300)
        val rect = JSONObject(JSONObject("{\"v\":${web.js(
            """(function() {
                var r = $doc.getElementById('$id').getBoundingClientRect();
                var f = ${if (frame == null) "null" else "document.getElementById('$frame').getBoundingClientRect()"};
                return JSON.stringify({x: r.left + r.width / 2 + (f ? f.left : 0), y: r.top + r.height / 2 + (f ? f.top : 0), d: window.devicePixelRatio});
            })()""",
        )}}").getString("v"))
        val tap = GeneralClickAction(
            Tap.SINGLE,
            { v ->
                val at = IntArray(2).also(v::getLocationOnScreen)
                val d = rect.getDouble("d")
                floatArrayOf((at[0] + rect.getDouble("x") * d).toFloat(), (at[1] + rect.getDouble("y") * d).toFloat())
            },
            Press.FINGER,
            InputDevice.SOURCE_UNKNOWN,
            MotionEvent.BUTTON_PRIMARY,
        )
        onView(withId(web.id)).perform(tap)
    }

    // 로드 중에 저절로 보낸 요청을 봐야 하는 사례는 로드 전 기록을 버리지 않는다.
    private fun viewer(url: String, rules: List<com.hwanyu365.pagetune.db.PluginAction> = emptyList(), keepLoadIntents: Boolean = false, check: (WebView) -> Unit) {
        val pageId = addWebPage(url, url, if (rules.isEmpty()) null else addTune("Links ${rules.size}", rules))
        ActivityScenario.launch<WebBrowserActivity>(Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId)).use {
            val web = webView(R.id.wv_browser)
            eventually { assertEquals("\"complete\"", web.js("document.body ? document.readyState : ''")) }
            if (!keepLoadIntents) sentSinceLast()
            check(web)
        }
    }

    private fun target(tag: String, id: String) = """{"selector":"#$id","tagName":"$tag","id":"$id","attributes":{}}"""

    private fun fixture(): LocalPage {
        val port = java.net.ServerSocket(0).use { it.localPort }
        val base = "http://127.0.0.1:$port/"
        val fallback = URLEncoder.encode(base + "fallback", "UTF-8")
        val settings = "intent://settings/x#Intent;scheme=https;package=com.android.settings;end"
        val page = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{margin:0;background:#fff;font:16px sans-serif} a,button{display:block;width:60%;height:40px;margin:4px 0;text-align:left} iframe{width:60%;height:120px;border:0}</style></head><body>
            <a id="blankTop" target="_blank" href="/top">top</a>
            <a id="plain" href="/plain">plain</a>
            <a id="mail" href="mailto:a@example.com">mail</a>
            <a id="tel" href="tel:0123456789">tel</a>
            <a id="app" href="$settings">app</a>
            <a id="missing" href="intent://x#Intent;scheme=pagetunetest;package=$MISSING;end">missing</a>
            <a id="missingFallback" href="intent://x#Intent;scheme=pagetunetest;package=$MISSING;S.browser_fallback_url=$fallback;end">missing fallback</a>
            <a id="file" href="file:///sdcard/a.txt">file</a>
            <a id="redirectIntent" href="/r-intent">redirect intent</a>
            <a id="redirectMarket" href="/r-market">redirect market</a>
            <button id="scriptIntent" onclick="location = '$settings'">script intent</button>
            <button id="twice" onclick="location = 'mailto:first@example.com'; setTimeout(function() { location = '/r-market' }, 300)">twice</button>
            <a id="blankHttps" target="_blank" href="/blank">blank https</a>
            <a id="blankMail" target="_blank" href="mailto:b@example.com">blank mail</a>
            <a id="blankFile" target="_blank" href="file:///sdcard/b.txt">blank file</a>
            <button id="openHttps" onclick="window.open('/opened')">open https</button>
            <button id="openContent" onclick="window.open('content://x')">open content</button>
            <button id="openFile" onclick="window.open('file:///a')">open file</button>
            <a id="attach" href="/file.bin">attachment</a>
            <a id="blob" download="b.txt">blob</a>
            <iframe id="frame" srcdoc="&lt;a id='framed' href='mailto:framed@example.com' style='display:block;height:40px'&gt;framed&lt;/a&gt;&lt;button id='framedOpen' style='display:block;height:40px' onclick=&quot;window.open('$settings')&quot;&gt;framed open&lt;/button&gt;"></iframe>
            <div style="height:1200px"></div>
            <script>document.getElementById('blob').href = URL.createObjectURL(new Blob(['x'], {type: 'text/plain'}));</script>
            </body></html>
        """.trimIndent()
        fun html(body: String) = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body>$body</body></html>"
        val routes = mapOf(
            "/r-intent" to Reply(302, "", mapOf("Location" to settings)),
            "/r-market" to Reply(302, "", mapOf("Location" to "market://details?id=com.example")),
            "/file.bin" to Reply(200, "binary", mapOf("Content-Type" to "application/octet-stream", "Content-Disposition" to "attachment; filename=\"file.bin\"")),
            "/frame-download" to Reply(body = html("<iframe src=\"/file.bin\"></iframe>")),
            "/auto" to Reply(body = html("<p>auto</p><script>setTimeout(function(){ location = '$settings' }, 300); setTimeout(function(){ window.open('/timer') }, 600);</script>")),
            "/frame-auto" to Reply(body = html("<iframe srcdoc=\"&lt;script&gt;setTimeout(function(){ window.open('$settings') }, 300)&lt;/script&gt;\"></iframe>")),
            "/blank" to Reply(body = html("<p>blank</p>")),
            "/opened" to Reply(body = html("<p>opened</p>")),
            "/top" to Reply(body = html("<p>top</p>")),
            "/fallback" to Reply(body = html("<p>fallback</p>")),
            "/plain" to Reply(body = html("<p>plain</p>")),
        )
        return LocalPage(page, port, routes)
    }

    private companion object {
        const val MISSING = "com.example.missing"
        const val BROWSER = "com.android.chrome"
    }
}
