package com.hwanyu365.pagetune

import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.WebView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

@RunWith(AndroidJUnit4::class)
class PageErrorTest {

    @Before
    fun setUp() {
        resetApp()
    }

    @Test
    @JvmName("tc142")
    fun `TC#142 주 문서 로드 실패는 종류별 오류 화면이고 다시 시도로 회복하며 하위 리소스와 HTTP 404 는 오류 화면이 아니다`() = loadErrors()

    @Test
    @JvmName("tc155")
    fun `TC#155 저장된 주소가 열 수 없는 주소면 로드하지 않고 오류 화면을 보이며 행은 남아 고칠 수 있다`() = badStoredAddress()

    @Test
    @JvmName("tc521")
    fun `TC#521 다른 호스트로 리다이렉트된 주 문서의 인증서 오류는 빈 화면이 아니라 보안 연결 오류 화면이다`() = redirectedCertificateError()

    private fun loadErrors() {
        val port = ServerSocket(0).use { it.localPort }
        val tune = addTune("Nav", listOf(clickRule("gnb_prev", sel("prev")), clickRule("gnb_next", sel("next"))))
        openViewer(addWebPage("Closed", "http://127.0.0.1:$port/", tune)).use {
            errorShown(R.string.load_error_no_response_title)
            onView(withId(R.id.layout_gnb_stack)).check(matches(not(isDisplayed())))
            onView(withId(R.id.btn_load_error_retry)).perform(click())
            errorShown(R.string.load_error_no_response_title)

            LocalPage(page("<a id=\"prev\" href=\"#\">p</a><a id=\"next\" href=\"#\">n</a><p id=\"ok\">ok</p>"), port).use {
                tapQuickHandle()
                onView(withId(R.id.btn_quick_refresh)).perform(click())
                val web = webView(R.id.wv_browser)
                eventually { assertEquals("\"y\"", web.js("document.getElementById('ok') ? 'y' : 'n'")) }
                eventually { onView(withId(R.id.layout_load_error)).check(matches(not(isDisplayed()))) }
                eventually { onView(withId(R.id.layout_gnb_stack)).check(matches(isDisplayed())) }
            }
        }

        openViewer(addWebPage("Invalid", "http://nonexistent.invalid/")).use {
            errorShown(R.string.load_error_cannot_connect_title)
        }

        val routes = mapOf(
            "/missing.png" to Reply(404, "missing", mapOf("Content-Type" to "image/png")),
            "/gone" to Reply(404, page("<p id=\"site\">site 404 body</p>")),
        )
        val closed = ServerSocket(0).use { it.localPort }
        val images = "<img src=\"/missing.png\"><img src=\"http://127.0.0.1:$closed/refused.png\">"
        LocalPage(page("<p id=\"site\">with broken images</p>$images"), routes = routes).use { server ->
            for ((url, text) in listOf(server.url to "with broken images", server.url + "gone" to "site 404 body")) {
                openViewer(addWebPage(url, url)).use {
                    val web = webView(R.id.wv_browser)
                    eventually { assertEquals("\"complete|$text\"", web.js("document.readyState + '|' + (document.getElementById('site') || {}).textContent")) }
                    Thread.sleep(500)
                    onView(withId(R.id.layout_load_error)).check(matches(not(isDisplayed())))
                }
            }
        }
    }

    private fun badStoredAddress() {
        val ids = listOf("content://x", "javascript:alert(1)").associateWith { addWebPage("Bad $it", it) }
        for (id in ids.values) {
            openViewer(id).use {
                errorShown(R.string.load_error_bad_address_title)
                onView(withId(R.id.btn_load_error_retry)).check(matches(not(isDisplayed())))
                val web = webView(R.id.wv_browser)
                assertTrue("WebView 가 주소를 로드함: ${onMain { web.url }}", onMain { web.url } == null)
            }
        }

        LocalPage(page("<p id=\"ok\">fixed</p>")).use { server ->
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(pagesTab).perform(click())
                for (url in ids.keys) eventually { onView(withText("Bad $url")).check(matches(isDisplayed())) }
                openItemMenu(withText("Bad javascript:alert(1)"))
                onView(withText(R.string.list_edit)).inRoot(isPlatformPopup()).perform(click())
                onView(withId(R.id.et_url)).inRoot(isDialog()).check(matches(withText("javascript:alert(1)")))
                onView(withId(R.id.btn_dialog_cancel)).inRoot(isDialog()).perform(click())
                openItemMenu(withText("Bad content://x"))
                onView(withText(R.string.list_edit)).inRoot(isPlatformPopup()).perform(click())
                onView(withId(R.id.et_url)).inRoot(isDialog()).check(matches(withText("content://x")))
                onView(withId(R.id.et_url)).inRoot(isDialog()).perform(replaceText(server.url))
                onView(withId(R.id.btn_dialog_save)).inRoot(isDialog()).perform(click())
            }
            openViewer(ids.getValue("content://x")).use {
                val web = webView(R.id.wv_browser)
                eventually { assertEquals("\"y\"", web.js("document.getElementById('ok') ? 'y' : 'n'")) }
                onView(withId(R.id.layout_load_error)).check(matches(not(isDisplayed())))
            }
        }
    }

    private fun redirectedCertificateError() {
        val tls = selfSignedTls()
        try {
            LocalPage(page("<p id=\"secret\">secret</p>"), tls = tls, host = "localhost").use { secure ->
                LocalPage("", routes = mapOf("/" to Reply(302, "", mapOf("Location" to secure.url)))).use { start ->
                    openViewer(addWebPage("Redirect", start.url)).use {
                        errorShown(R.string.load_error_secure_title)
                        val web = webView(R.id.wv_browser)
                        Thread.sleep(1_000)
                        assertEquals("\"n\"", web.js("document.getElementById('secret') ? 'y' : 'n'"))
                        val buttons = onMain {
                            val error = resumedActivity().findViewById<android.view.ViewGroup>(R.id.layout_load_error)
                            buttonsIn(error).filter { it.isShown }.map { it.id }
                        }
                        assertEquals(listOf(R.id.btn_load_error_retry), buttons)
                    }
                }
            }
        } finally {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(TLS_ALIAS)
        }
    }

    private fun buttonsIn(group: android.view.ViewGroup): List<Button> = (0 until group.childCount).flatMap {
        when (val child = group.getChildAt(it)) {
            is Button -> listOf(child)
            is android.view.ViewGroup -> buttonsIn(child)
            else -> emptyList()
        }
    }

    // 인증서는 실행 중 기기 키 저장소에서 만든다 → 저장소에 비밀 키 파일을 두지 않는다.
    private fun selfSignedTls(): SSLContext {
        val keys = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keys.containsAlias(TLS_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                initialize(
                    KeyGenParameterSpec.Builder(TLS_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                        .setCertificateSubject(X500Principal("CN=localhost"))
                        .build(),
                )
            }.generateKeyPair()
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, null) }
        return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
    }

    private fun errorShown(title: Int) = eventually {
        onView(withId(R.id.layout_load_error)).check(matches(isDisplayed()))
        onView(withId(R.id.tv_load_error_title)).check(matches(withText(title)))
    }

    private fun openViewer(pageId: Long): ActivityScenario<WebBrowserActivity> =
        ActivityScenario.launch(Intent(targetContext, WebBrowserActivity::class.java).putExtra("webpage_id", pageId))

    private fun sel(id: String) = """{"selector":"#$id","tagName":"a","id":"$id","attributes":{}}"""

    private fun page(body: String) =
        "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head><body style=\"margin:0;background:#fff\">$body</body></html>"

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TLS_ALIAS = "page-error-test-tls"
    }
}
