package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRulesTest {

    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.toList()
    private fun code(f: File) = f.readText().lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    private fun occurrences(token: String) = sources.flatMap { f -> Regex(Regex.escape(token)).findAll(code(f)).map { f.name }.toList() }

    @Test
    fun `TC#43 앱 소스에 proceed 호출이 없고 인증서 오류 구현마다 cancel 을 부른다`() {
        assertEquals(emptyList<String>(), occurrences(".proceed("))
        val impls = sources.filter { "override fun onReceivedSslError" in code(it) }
        assertTrue("onReceivedSslError 구현 없음", impls.isNotEmpty())
        for (f in impls) {
            val body = Regex("""override fun onReceivedSslError\([^)]*\)\s*\{(.*?)\n    }""", RegexOption.DOT_MATCHES_ALL).find(code(f))
            assertTrue("${f.name} onReceivedSslError 본문", body != null)
            assertTrue("${f.name} cancel 없음", ".cancel()" in body!!.groupValues[1])
        }
    }

    @Test
    fun `TC#45 N1 N2 새 창을 만들지 않고 자동 팝업을 막는다`() {
        assertEquals(emptyList<String>(), occurrences("setSupportMultipleWindows(true)"))
        assertEquals(emptyList<String>(), occurrences("onCreateWindow"))
        assertEquals(emptyList<String>(), occurrences("javaScriptCanOpenWindowsAutomatically = true"))
        assertEquals(1, occurrences("javaScriptCanOpenWindowsAutomatically = false").size)
    }

    @Test
    fun `TC#56 loadUrl 은 래퍼 한 곳에서 주소 입력 분류 뒤에만 부른다`() {
        assertEquals(listOf("SafeWebView.kt"), occurrences(".loadUrl("))
        val src = code(sources.single { it.name == "SafeWebView.kt" })
        val wrapper = Regex("""fun loadChecked\((.*?)\n    }""", RegexOption.DOT_MATCHES_ALL).find(src)!!.groupValues[1]
        val classify = wrapper.indexOf("UrlPolicy.normalizeInput(")
        assertTrue("분류 호출 없음", classify >= 0)
        assertTrue("분류가 loadUrl 뒤", classify < wrapper.indexOf(".loadUrl("))
        assertEquals("래퍼 밖 loadUrl", 1, Regex(Regex.escape(".loadUrl(")).findAll(src).count())
    }
}
