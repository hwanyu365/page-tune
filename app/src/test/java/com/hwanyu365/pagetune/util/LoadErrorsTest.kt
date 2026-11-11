package com.hwanyu365.pagetune.util

import android.webkit.WebViewClient
import com.hwanyu365.pagetune.util.LoadErrorKind.BAD_ADDRESS
import com.hwanyu365.pagetune.util.LoadErrorKind.CANNOT_CONNECT
import com.hwanyu365.pagetune.util.LoadErrorKind.NO_RESPONSE
import com.hwanyu365.pagetune.util.LoadErrorKind.OTHER
import com.hwanyu365.pagetune.util.LoadErrorKind.SECURE_CONNECTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadErrorsTest {

    @Test
    fun `TC#141 주 문서 오류 코드를 종류로 나눈다`() {
        val expected = mapOf(
            WebViewClient.ERROR_HOST_LOOKUP to CANNOT_CONNECT,
            WebViewClient.ERROR_CONNECT to NO_RESPONSE,
            WebViewClient.ERROR_TIMEOUT to NO_RESPONSE,
            WebViewClient.ERROR_IO to NO_RESPONSE,
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE to SECURE_CONNECTION,
            WebViewClient.ERROR_BAD_URL to BAD_ADDRESS,
            WebViewClient.ERROR_UNSUPPORTED_SCHEME to BAD_ADDRESS,
            WebViewClient.ERROR_UNKNOWN to OTHER,
            WebViewClient.ERROR_TOO_MANY_REQUESTS to OTHER,
        )
        for ((code, kind) in expected) assertEquals("$code", kind, classifyLoadError(code, isMainFrame = true))
    }

    @Test
    fun `TC#141 주 문서가 아니면 오류 화면이 없다`() {
        for (code in listOf(WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, WebViewClient.ERROR_UNKNOWN)) {
            assertNull(classifyLoadError(code, isMainFrame = false))
        }
    }

    @Test
    fun `TC#141 S1 리다이렉트로 거친 주소의 인증서 오류는 주 문서다`() {
        val t = MainDocumentTracker()
        t.start("https://a.example/")
        t.add("https://b.example/")
        t.commit()
        assertTrue(t.isMainDocument("https://b.example/"))
        assertTrue(t.isMainDocument("https://a.example/"))
    }

    @Test
    fun `TC#141 S2 커밋 전이면 집합에 없어도 주 문서다`() {
        val t = MainDocumentTracker()
        t.start("https://a.example/")
        assertTrue(t.isMainDocument("https://cdn.example/x.js"))
        assertTrue(MainDocumentTracker().isMainDocument(null))
    }

    @Test
    fun `TC#141 S3 커밋 후 집합 밖 주소는 하위 리소스다`() {
        val t = MainDocumentTracker()
        t.start("https://a.example/")
        t.commit()
        assertFalse(t.isMainDocument("https://cdn.example/x.js"))
        assertFalse(t.isMainDocument(null))
    }

    @Test
    fun `TC#141 새 로드가 시작되면 이전 로드의 집합을 버린다`() {
        val t = MainDocumentTracker()
        t.start("https://a.example/")
        t.add("https://b.example/")
        t.commit()
        t.start("https://c.example/")
        t.commit()
        assertFalse(t.isMainDocument("https://b.example/"))
        assertFalse(t.isMainDocument("https://a.example/"))
        assertTrue(t.isMainDocument("https://c.example/"))
    }
}
