package com.hwanyu365.pagetune.util

import org.json.JSONObject

/** 로드마다 바뀌는 id 판정. 선택 스크립트도 [PATTERN] 을 주입받아 같은 규칙을 쓴다. */
object UnstableId {
    // Kotlin Regex 와 JS RegExp 가 같은 뜻으로 해석하는 문법만 쓴다.
    const val PATTERN = """\d{4}|(?=[0-9a-f]*\d)[0-9a-f]{8,}|^(?:google_ads_iframe_|div-gpt-ad|aswift_)"""

    private val regex = Regex(PATTERN, RegexOption.IGNORE_CASE)

    fun isUnstable(id: String): Boolean = regex.containsMatchIn(id)

    fun needsReselect(targetSelector: String): Boolean =
        runCatching { isUnstable(JSONObject(targetSelector).optString("id")) }.getOrDefault(false)
}
