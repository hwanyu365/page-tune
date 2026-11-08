package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.util.ElementLabel.Kind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElementLabelTest {

    private fun info(tag: String, text: String = "", attrs: Map<String, String> = emptyMap()) =
        JSONObject().put("selector", "x").put("tagName", tag).put("textContent", text).put("id", "")
            .put("attributes", JSONObject(attrs)).toString()

    private fun kind(tag: String, attrs: Map<String, String> = emptyMap()) = ElementLabel.of(info(tag, attrs = attrs))!!.kind

    @Test
    fun `TC#39 태그마다 종류를 정한다`() {
        assertEquals(Kind.LINK, kind("a"))
        listOf("img", "picture", "svg").forEach { assertEquals(it, Kind.IMAGE, kind(it)) }
        assertEquals(Kind.BUTTON, kind("button"))
        listOf("button", "submit", "reset", "SUBMIT").forEach { assertEquals(it, Kind.BUTTON, kind("input", mapOf("type" to it))) }
        assertEquals(Kind.INPUT, kind("input"))
        assertEquals(Kind.INPUT, kind("input", mapOf("type" to "text")))
        assertEquals(Kind.INPUT, kind("textarea"))
        assertEquals(Kind.INPUT, kind("select"))
        assertEquals(Kind.FRAME, kind("iframe"))
        assertEquals(Kind.MEDIA, kind("video"))
        assertEquals(Kind.MEDIA, kind("audio"))
        assertEquals(Kind.AREA, kind("div"))
        assertEquals(Kind.AREA, kind("span"))
    }

    @Test
    fun `TC#39 텍스트는 aria-label, alt, title, textContent 순서로 처음 비어 있지 않은 값이다`() {
        val all = mapOf("aria-label" to "다음 글", "alt" to "alt", "title" to "title")
        assertEquals(ElementLabel(Kind.LINK, "다음 글"), ElementLabel.of(info("a", "본문", all)))
        assertEquals("alt", ElementLabel.of(info("img", "본문", all - "aria-label"))!!.text)
        assertEquals("title", ElementLabel.of(info("img", "본문", mapOf("aria-label" to " ", "title" to "title")))!!.text)
        assertEquals("본문", ElementLabel.of(info("div", "본문"))!!.text)
    }

    @Test
    fun `TC#39 연속 공백과 개행은 공백 하나로 바꾸고 앞뒤를 자른다`() {
        assertEquals("a b c", ElementLabel.of(info("div", "  a \n\t b   c \n"))!!.text)
    }

    @Test
    fun `TC#39 30자를 넘으면 30자와 말줄임표`() {
        val thirty = "가".repeat(30)
        assertEquals(thirty, ElementLabel.of(info("div", attrs = mapOf("title" to thirty)))!!.text)
        assertEquals("$thirty…", ElementLabel.of(info("div", attrs = mapOf("title" to thirty + "나")))!!.text)
    }

    @Test
    fun `TC#39 텍스트가 없으면 종류만`() {
        assertEquals(ElementLabel(Kind.IMAGE, null), ElementLabel.of(info("img")))
        assertEquals(ElementLabel(Kind.AREA, null), ElementLabel.of(info("div", " \n ")))
    }

    @Test
    fun `TC#39 JSON 이 아니면 라벨 없음`() {
        assertNull(ElementLabel.of("not json"))
        assertNull(ElementLabel.of(""))
    }
}
