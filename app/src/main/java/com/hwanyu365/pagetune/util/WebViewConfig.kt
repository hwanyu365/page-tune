package com.hwanyu365.pagetune.util

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewConfig {

    /** Common WebView setup shared by the inspector and the browser (minSdk 24, so no version guards needed). */
    @SuppressLint("SetJavaScriptEnabled")
    fun applyCommon(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            // UA 는 건드리지 않는다. WebView 는 Sec-CH-UA 클라이언트 힌트에 "Android WebView" 브랜드를 항상 실어 보내므로,
            // UA 문자열만 Chrome 처럼 위장하면 힌트와 어긋나 봇 점수가 오히려 나빠진다.
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
    }
}
