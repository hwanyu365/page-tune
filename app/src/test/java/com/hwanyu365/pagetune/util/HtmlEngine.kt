package com.hwanyu365.pagetune.util

import org.htmlunit.BrowserVersion
import org.htmlunit.MockWebConnection
import org.htmlunit.WebClient
import org.htmlunit.html.HtmlElement
import org.htmlunit.html.HtmlPage
import java.net.URL
import java.nio.charset.StandardCharsets

/** 픽스처 HTML 을 HtmlUnit 에 올리고 앱이 만든 스크립트 문자열을 그대로 실행한다. */
class HtmlEngine(html: String) : AutoCloseable {
    private val client = WebClient(BrowserVersion.CHROME)
    val page: HtmlPage

    init {
        val url = URL("http://fixture.test/")
        client.webConnection = MockWebConnection().apply {
            setResponse(url, html, 200, "OK", "text/html", StandardCharsets.UTF_8, emptyList())
        }
        page = client.getPage(url)
        eval(
            """
            window.AndroidBridge = {
                calls: [],
                onElementSelected: function(json, count) { this.calls.push({ json: json, count: count }); }
            };
            """
        )
    }

    fun eval(js: String): Any? = page.executeJavaScript(js).javaScriptResult

    fun string(js: String): String = eval(js).toString()

    fun int(js: String): Int = (eval(js) as Number).toInt()

    fun bool(js: String): Boolean = eval(js) as Boolean

    fun click(css: String) {
        page.querySelector<HtmlElement>(css).click<HtmlPage>()
    }

    /** 선택 스크립트가 마지막으로 보낸 요소 정보 JSON 과 매칭 수. */
    fun lastSelection(): Pair<String, Int> =
        string("AndroidBridge.calls[AndroidBridge.calls.length - 1].json") to
            int("AndroidBridge.calls[AndroidBridge.calls.length - 1].count")

    fun isHidden(css: String): Boolean =
        bool("document.querySelector(${JsScripts.jsString(css)}).style.display === 'none'")

    override fun close() = client.close()
}
