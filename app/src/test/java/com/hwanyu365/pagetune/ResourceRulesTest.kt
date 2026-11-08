package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceRulesTest {

    private val res = File("src/main/res")

    @Test
    fun `TC#25 레이아웃과 메뉴는 시스템 기본 아이콘을 참조하지 않는다`() {
        val offenders = listOf("layout", "menu").flatMap { dir ->
            File(res, dir).listFiles().orEmpty().filter { "@android:drawable/" in it.readText() }
        }

        assertEquals(emptyList<File>(), offenders)
    }

    @Test
    fun `TC#25 오픈소스 라이선스 원문에 Material Symbols 저작권과 Apache 2_0 이 있다`() {
        val text = File(res, "raw/open_source_licenses.txt").readText()

        assertTrue(text.contains("Material Symbols"))
        assertTrue(text.contains("Apache License"))
        assertTrue(text.contains("Version 2.0"))
    }

    @Test
    fun `TC#22 브랜드 색 위 글자색은 171717 이다`() {
        val colors = File(res, "values/colors.xml").readText()

        assertTrue(Regex("""name="onPrimary">#171717<""").containsMatchIn(colors))
        assertTrue(Regex("""name="brandOnSurface">#ff7347<""").containsMatchIn(colors))
    }

    @Test
    fun `TC#4 처리방침 주소는 번역하지 않는 문자열 하나다`() {
        val strings = File(res, "values/strings.xml").readText()
        val entry = Regex("""<string name="privacy_policy_url"([^>]*)>([^<]*)</string>""").find(strings)
        assertTrue("privacy_policy_url 없음", entry != null)
        assertEquals("https://hwanyu365.github.io/page-tune/privacy", entry!!.groupValues[2])
        assertTrue("""translatable="false"""" in entry.groupValues[1])
        assertTrue("privacy_policy_url" !in File(res, "values-ko/strings.xml").readText())
    }
}
