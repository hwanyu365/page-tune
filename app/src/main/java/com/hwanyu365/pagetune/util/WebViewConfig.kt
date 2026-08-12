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
            // Anti-blocking: Cloudflare 는 UA 문자열의 Chrome 버전을 Sec-CH-UA 클라이언트 힌트와 대조한다.
            // UA 를 통째로 하드코딩하면 힌트는 실제 엔진 버전을 그대로 보내므로 불일치가 나고 챌린지가 무한 반복된다.
            // 그래서 기기 기본 UA 에서 WebView 표식(`; wv`, `Version/4.0`)만 떼어내 버전 정합성을 유지한다.
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace(Regex("""Version/\d+\.\d+ """), "")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
    }
}
