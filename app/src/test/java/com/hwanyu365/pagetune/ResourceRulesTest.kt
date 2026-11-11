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
    fun `TC#304 처리방침 주소는 번역하지 않는 문자열 하나다`() {
        val strings = File(res, "values/strings.xml").readText()
        val entry = Regex("""<string name="privacy_policy_url"([^>]*)>([^<]*)</string>""").find(strings)
        assertTrue("privacy_policy_url 없음", entry != null)
        assertEquals("https://hwanyu365.github.io/page-tune/privacy", entry!!.groupValues[2])
        assertTrue("""translatable="false"""" in entry.groupValues[1])
        assertTrue("privacy_policy_url" !in File(res, "values-ko/strings.xml").readText())
    }

    @Test
    fun `TC#152 새 소식 목록은 오름차순·중복 없음이고 항목마다 두 언어 문구가 있다`() {
        val codes = com.hwanyu365.pagetune.ui.WHATS_NEW_NOTES.map { it.first }
        assertEquals(codes.sorted().distinct(), codes)

        val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        for ((code, id) in com.hwanyu365.pagetune.ui.WHATS_NEW_NOTES) {
            assertEquals("whats_new_$code", names[id])
            for (dir in listOf("values", "values-ko")) {
                assertTrue("$dir 에 whats_new_$code 없음", Regex("""<string name="whats_new_$code">""").containsMatchIn(File(res, "$dir/strings.xml").readText()))
            }
        }
    }

    @Test
    fun `TC#214 한국어 문구는 영문 뒤 조사를 띄우지 않는다`() {
        val spaced = Regex("""[A-Za-z0-9] (을|를|이|가|은|는|으로|로|의|에서|에게|에|와|과|도|만|부터|까지|처럼|보다|이나|나|랑)(?=[\s.,!?"<]|$)""")
        val bad = File(res, "values-ko/strings.xml").readLines().filter { spaced.containsMatchIn(it) }
        assertEquals(emptyList<String>(), bad)
    }
}
