package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.util.LinkDecision.Block
import com.hwanyu365.pagetune.util.LinkDecision.External
import com.hwanyu365.pagetune.util.LinkDecision.InWebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    private val appId = "com.hwanyu365.pagetune"
    private val l4 = listOf(
        "javascript:x", "data:text/html,x", "file:///sdcard/a", "content://x", "blob:https://a/1",
        "filesystem:https://a/t/x", "about:config", "chrome://settings", "view-source:https://a",
    )

    private fun link(url: String, mainFrame: Boolean = true, gesture: Boolean = true, redirect: Boolean = false) =
        UrlPolicy.classifyLink(url, mainFrame, gesture, redirect, appId)

    private fun view(url: String) = External(ExternalTarget.View(url))

    @Test
    fun `TC#45 L1 scheme 을 구할 수 없으면 막는다`() {
        for (url in listOf("", ":abc", "abc", "1a:b")) assertEquals(url, Block, link(url))
    }

    @Test
    fun `TC#45 L2 http(s) 는 대소문자와 무관하게 WebView 에 연다`() {
        for (url in listOf("https://a", "HTTPS://a", "Http://a", "http://a")) assertEquals(url, InWebView, link(url))
    }

    @Test
    fun `TC#45 L3 about blank 와 srcdoc 은 WebView 에 연다`() {
        for (url in listOf("about:blank", "about:srcdoc")) assertEquals(url, InWebView, link(url))
    }

    @Test
    fun `TC#45 L4 목록 scheme 은 주 프레임 제스처여도 막는다`() {
        for (url in l4 + listOf("FILE:///a", "JavaScript:x", "Content://x")) assertEquals(url, Block, link(url))
    }

    @Test
    fun `TC#45 L5 주 프레임이 아닌 외부 scheme 은 막는다`() {
        for (url in listOf("mailto:a@b", "intent://x#Intent;scheme=https;end")) {
            assertEquals(url, Block, link(url, mainFrame = false))
            assertEquals(url, Block, link(url, mainFrame = false, redirect = true))
            assertEquals(url, Block, link(url, mainFrame = false, gesture = false, redirect = true))
        }
    }

    @Test
    fun `TC#45 L6 사용자 조작도 리다이렉트도 없는 외부 scheme 은 막는다`() {
        for (url in listOf("mailto:a@b", "market://details?id=x", "intent://x#Intent;scheme=https;end")) {
            assertEquals(url, Block, link(url, gesture = false, redirect = false))
        }
    }

    @Test
    fun `TC#45 L7 사용자 조작이나 리다이렉트로 온 외부 scheme 은 같은 대상을 확인 후 연다`() {
        for (url in listOf("mailto:a@b", "market://details?id=x")) {
            assertEquals(url, view(url), link(url, gesture = true, redirect = false))
            assertEquals(url, view(url), link(url, gesture = false, redirect = true))
            assertEquals(url, view(url), link(url, gesture = true, redirect = true))
        }
        val intent = External(ExternalTarget.IntentTarget("https://x", null, null))
        assertEquals(intent, link("intent://x#Intent;scheme=https;end", gesture = false, redirect = true))
        assertEquals(Block, link("intent://x#Intent;scheme=file;end", gesture = false, redirect = true))
    }

    @Test
    fun `TC#45 L8 intent 주소는 intent 구성 결과를 쓴다`() {
        assertEquals(External(ExternalTarget.IntentTarget("https://x", "com.x", null)), link("intent://x#Intent;scheme=https;package=com.x;end"))
        assertEquals(External(ExternalTarget.IntentTarget("mailto:a@b", null, null)), link("intent:a@b#Intent;scheme=mailto;end"))
        assertEquals(External(ExternalTarget.IntentTarget("https://x", null, null)), link("INTENT://x#Intent;scheme=https;end"))
        assertEquals(Block, link("intent://x#Intent;scheme=https"))
    }

    @Test
    fun `TC#45 L9 그 밖 scheme 은 외부 앱 보기로 넘긴다`() {
        for (url in listOf("mailto:a@b", "tel:1", "market://details?id=x", "myapp://x")) assertEquals(url, view(url), link(url))
    }

    @Test
    fun `TC#46 I1 Intent 블록이 없거나 end 로 끝나지 않으면 막는다`() {
        assertNull(UrlPolicy.parseIntent("intent://x#Intent;scheme=https", appId))
        assertNull(UrlPolicy.parseIntent("intent://x", appId))
        assertNull(UrlPolicy.parseIntent("intent://x;scheme=https;end", appId))
        assertNull(UrlPolicy.parseIntent("intent://x#Intent;scheme=https;end;extra", appId))
        assertEquals(ExternalTarget.IntentTarget(null, null, null), UrlPolicy.parseIntent("intent://x#Intent;end", appId))
    }

    @Test
    fun `TC#46 I4 data 는 intent 뒤 나머지를 그대로 이어 붙인다`() {
        assertEquals("https://a/b", UrlPolicy.parseIntent("intent://a/b#Intent;scheme=https;end", appId)?.data)
        assertEquals("geo:1,2", UrlPolicy.parseIntent("intent:1,2#Intent;scheme=geo;end", appId)?.data)
        assertEquals("mailto:a@b", UrlPolicy.parseIntent("INTENT:a@b#Intent;scheme=mailto;end", appId)?.data)
    }

    @Test
    fun `TC#46 앞뒤 공백은 링크 분류와 같이 잘라 읽는다`() {
        val padded = "  intent://x#Intent;scheme=https;package=com.x;end \n"
        assertEquals(ExternalTarget.IntentTarget("https://x", "com.x", null), UrlPolicy.parseIntent(padded, appId))
        assertEquals(External(ExternalTarget.IntentTarget("https://x", "com.x", null)), link(padded))
    }

    @Test
    fun `TC#46 I2 I3 허용 키 밖의 필드는 결과에 들어가지 않는다`() {
        val url = "intent://x#Intent;scheme=https;package=com.x;component=com.evil/.Secret;SEL;action=android.intent.action.DELETE;" +
            "category=android.intent.category.LAUNCHER;launchFlags=0x10000000;S.extra=1;i.n=2;B.b=true;S.browser_fallback_url=https%3A%2F%2Ff;end"
        assertEquals(ExternalTarget.IntentTarget("https://x", "com.x", "https://f"), UrlPolicy.parseIntent(url, appId))
    }

    @Test
    fun `TC#46 I4 data 는 scheme 값과 나머지로 만들고 scheme 이 없으면 없다`() {
        assertEquals("https://a/b?c", UrlPolicy.parseIntent("intent://a/b?c#Intent;scheme=https;package=com.x;end", appId)?.data)
        val noScheme = UrlPolicy.parseIntent("intent://a#Intent;package=com.x;end", appId)
        assertNull(noScheme?.data)
        assertEquals("com.x", noScheme?.pkg)
    }

    @Test
    fun `TC#46 I5 L4 scheme 이나 이 앱 package 는 막는다`() {
        val schemes = listOf("javascript", "data", "file", "content", "blob", "filesystem", "about", "chrome", "view-source", "FILE", "JavaScript")
        for (s in schemes) assertNull(s, UrlPolicy.parseIntent("intent://x#Intent;scheme=$s;end", appId))
        assertNull(UrlPolicy.parseIntent("intent://x#Intent;scheme=https;package=$appId;end", appId))
    }

    @Test
    fun `TC#46 I6 대체 주소는 디코딩 후 http(s) 일 때만 쓴다`() {
        fun fallback(v: String) = UrlPolicy.parseIntent("intent://x#Intent;scheme=myapp;S.browser_fallback_url=$v;end", appId)?.fallbackUrl
        assertEquals("https://a", fallback("https%3A%2F%2Fa"))
        assertEquals("HTTP://a", fallback("HTTP%3A%2F%2Fa"))
        assertNull(fallback("javascript%3Ax"))
        assertNull(fallback("file%3A%2F%2F%2Fa"))
        assertNull(fallback("intent%3A%2F%2Fx"))
    }

    @Test
    fun `TC#46 I7 값은 퍼센트 디코딩하고 잘못된 인코딩의 키는 없는 것으로 본다`() {
        assertEquals("com.x", UrlPolicy.parseIntent("intent://x#Intent;scheme=https;package=com%2Ex;end", appId)?.pkg)
        assertNull(UrlPolicy.parseIntent("intent://x#Intent;scheme=myapp;S.browser_fallback_url=https%3A%2F%2Fa%ZZ;end", appId)?.fallbackUrl)
        assertNull(UrlPolicy.parseIntent("intent://x#Intent;scheme=https;package=com%E0x;end", appId)?.pkg)
        assertNull(UrlPolicy.parseIntent("intent://x#Intent;scheme=ht%tps;end", appId)?.data)
    }

    @Test
    fun `TC#48 다운로드는 http(s) 주소만 브라우저로 넘긴다`() {
        for (url in listOf("https://a/f.zip", "HTTP://a/f")) assertTrue(url, UrlPolicy.isBrowserDownload(url))
        val rejected = listOf("blob:https://a/1", "data:application/zip;base64,x", "file:///sdcard/a.apk", "content://x", "ftp://a/f", "FILE:///a", "", "httpx://a/f", "https-x://a/f")
        for (url in rejected) assertFalse(url, UrlPolicy.isBrowserDownload(url))
    }

    @Test
    fun `TC#54 U1 빈 값과 공백만은 입력 오류다`() {
        for (input in listOf("", "   ", "\t\n")) assertNull(input, UrlPolicy.normalizeInput(input))
    }

    @Test
    fun `TC#54 U2 http(s) 는 허용하고 scheme 을 소문자로 바꾼다`() {
        assertEquals("https://a", UrlPolicy.normalizeInput("https://a"))
        assertEquals("http://a", UrlPolicy.normalizeInput("http://a"))
        assertEquals("https://a", UrlPolicy.normalizeInput("HTTPS://a"))
        assertEquals("http://a", UrlPolicy.normalizeInput(" Http://a "))
        assertEquals("https://Example.com", UrlPolicy.normalizeInput("HTTPS://Example.com"))
    }

    @Test
    fun `TC#54 U3 그 밖 scheme 은 입력 오류다`() {
        val inputs = listOf(
            "javascript:x", "data:x", "file:///a", "content://x", "blob:https://a/1", "filesystem:https://a/t", "about:blank",
            "chrome://x", "view-source:https://a", "FILE:///a", "JavaScript:x", "mailto:a@b", "intent://x#Intent;end", "ftp://a",
        )
        for (input in inputs) assertNull(input, UrlPolicy.normalizeInput(input))
    }

    @Test
    fun `TC#54 U4 scheme 이 없으면 https 를 붙인다`() {
        assertEquals("https://example.com", UrlPolicy.normalizeInput("example.com"))
        assertEquals("https://localhost:8080/x", UrlPolicy.normalizeInput("localhost:8080/x"))
        assertEquals("https://example.com:443", UrlPolicy.normalizeInput("example.com:443"))
    }

    @Test
    fun `TC#54 U5 호스트가 비면 입력 오류다`() {
        for (input in listOf("https://", "https:///a", "http://?x", "https://user@")) assertNull(input, UrlPolicy.normalizeInput(input))
    }

    @Test
    fun `TC#54 허용 결과를 다시 넣으면 같은 값이다`() {
        for (input in listOf("example.com", "HTTPS://Example.com/a?b#c", "localhost:8080/x", "http://a")) {
            val once = UrlPolicy.normalizeInput(input)!!
            assertEquals(input, once, UrlPolicy.normalizeInput(once))
        }
    }
}
