package com.hwanyu365.pagetune.util

import android.webkit.WebViewClient

enum class LoadErrorKind { CANNOT_CONNECT, NO_RESPONSE, SECURE_CONNECTION, BAD_ADDRESS, OTHER }

fun classifyLoadError(errorCode: Int, isMainFrame: Boolean): LoadErrorKind? {
    if (!isMainFrame) return null
    return when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP -> LoadErrorKind.CANNOT_CONNECT
        WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO -> LoadErrorKind.NO_RESPONSE
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> LoadErrorKind.SECURE_CONNECTION
        WebViewClient.ERROR_BAD_URL, WebViewClient.ERROR_UNSUPPORTED_SCHEME -> LoadErrorKind.BAD_ADDRESS
        else -> LoadErrorKind.OTHER
    }
}

/** `onReceivedSslError` 는 요청 정보가 없어, 이번 로드의 주 문서 주소와 커밋 여부로 주 문서인지 가린다. */
class MainDocumentTracker {
    private val urls = mutableSetOf<String>()
    private var committed = false

    fun start(url: String) {
        urls.clear()
        urls += url
        committed = false
    }

    fun add(url: String) {
        urls += url
    }

    fun commit() {
        committed = true
    }

    fun isMainDocument(errorUrl: String?): Boolean = errorUrl in urls || !committed
}
