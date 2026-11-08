package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexColorTest {

    @Test
    fun `TC#43 H1 H2 H3 앞뒤 공백과 맨 앞 샵 하나를 떼고 6자리는 대문자로`() {
        assertEquals("#1A2B3C", HexColor.parse(" #1a2b3c "))
        assertEquals("#1A2B3C", HexColor.parse("1a2b3c"))
    }

    @Test
    fun `TC#43 H4 3자리는 자리마다 두 번`() {
        assertEquals("#11AA22", HexColor.parse("1a2"))
        assertEquals("#FFFFFF", HexColor.parse("#fff"))
    }

    @Test
    fun `TC#43 H2 H5 그 밖은 없음`() {
        listOf("##123456", "", "  ", "#", "#12", "#1234", "#12345", "#1234567", "#12345678", "#GGGGGG", "red", "#fff;}", "# fff", "1a 2")
            .forEach { assertNull(it, HexColor.parse(it)) }
    }

    @Test
    fun `TC#43 경계 전체 일치 16진 아닌 글자가 섞이면 길이가 맞아도 없음`() {
        listOf("#123456;color:red", "#12345;", "a;}", "#ab;", "1a2;}3", "#12 456").forEach { assertNull(it, HexColor.parse(it)) }
    }

    @Test
    fun `TC#43 경계 길이 3자리·6자리 밖은 없음`() {
        listOf("#1", "#12", "#1234", "#12345", "#1234567", "#12345678", "#123456789").forEach { assertNull(it, HexColor.parse(it)) }
    }

    @Test
    fun `TC#43 경계 맨 앞 샵은 하나만 뗀다`() {
        listOf("##123456", "##abc", "###fff").forEach { assertNull(it, HexColor.parse(it)) }
    }

    @Test
    fun `TC#43 경계 앞뒤 공백·탭·개행은 자른다`() {
        assertEquals("#AABBCC", HexColor.parse("\t#abc\n"))
        assertEquals("#123456", HexColor.parse("  123456  "))
    }

    @Test
    fun `TC#43 프리셋은 12개 이하의 해석 가능한 대문자 RRGGBB 이고 필수 색을 포함한다`() {
        val presets = ColorPresets.ALL
        assertTrue(presets.size <= 12)
        presets.forEach { assertEquals(it, it, HexColor.parse(it)) }
        assertEquals(presets.distinct(), presets)
        assertTrue(presets.containsAll(listOf("#000000", "#FFFFFF", "#171717", "#242424", "#E0E0E0", "#9E9E9E")))
    }
}
