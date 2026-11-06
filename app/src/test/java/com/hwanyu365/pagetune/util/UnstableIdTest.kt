package com.hwanyu365.pagetune.util

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnstableIdTest {

    @Test
    fun `TC#15 숫자 4개 이상 연속, 숫자가 든 16진 8자 이상, 광고 슬롯 접두어는 불안정이다`() {
        listOf(
            "x123456", "post-20261106", "ad_8f3a9c2e1b", "AD_8F3A9C2E1B",
            "google_ads_iframe_/123/x_0", "div-gpt-ad-1712345678-0", "div-gpt-ad-top", "aswift_0", "ASWIFT_1",
        ).forEach { assertTrue(it, UnstableId.isUnstable(it)) }
    }

    @Test
    fun `TC#15 일반 이름, 숫자 3개 이하, 숫자 없는 16진 문자열, 빈 값은 안정이다`() {
        listOf("header", "nav-top", "content1", "deadbeef", "DEADBEEFCAFE", "item123", "a1b2c3d", "", "my-google_ads_iframe_x")
            .forEach { assertFalse(it, UnstableId.isUnstable(it)) }
    }

    private fun details(id: String, selector: String) =
        JSONObject().put("selector", selector).put("tagName", "div").put("id", id).put("attributes", JSONObject()).toString()

    @Test
    fun `TC#19 id 필드가 불안정이면 다시 선택이 필요하다`() {
        assertTrue(UnstableId.needsReselect(details("div-gpt-ad-1712345678-0", "#div-gpt-ad-1712345678-0")))
    }

    @Test
    fun `TC#19 id 필드가 안정이거나 비어 있으면 셀렉터와 무관하게 다시 선택하지 않는다`() {
        assertFalse(UnstableId.needsReselect(details("header", "#header")))
        assertFalse(UnstableId.needsReselect(details("", "#\\31 abc > div:nth-of-type(20261106)")))
        assertFalse(UnstableId.needsReselect(details("", "#main > div.ad")))
    }

    @Test
    fun `TC#19 요소 정보가 JSON 이 아니면 판정하지 않는다`() {
        assertFalse(UnstableId.needsReselect("not json"))
        assertFalse(UnstableId.needsReselect("""{"y": 120}"""))
    }
}
