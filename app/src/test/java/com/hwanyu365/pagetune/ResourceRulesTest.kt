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
    fun `TC#212 TC#22 테스트는 브랜드 색 값 단언을 그대로 둔다`() {
        val source = File("src/test/java/com/hwanyu365/pagetune/ResourceRulesTest.kt").readText()
        val body = source.substringAfter("fun `TC#22 ").substringBefore("@Test").lines().map { it.trim() }
        for (value in listOf("onPrimary\">#171717<", "brandOnSurface\">#ff7347<")) {
            val assertion = "assertTrue(Regex(\"\"\"name=\"$value\"\"\").containsMatchIn(colors))"
            assertTrue("TC#22 에 값 단언이 없다: $assertion", assertion in body)
        }
    }

    private fun userStrings(dir: String): Map<String, String> {
        val text = File(res, "$dir/strings.xml").readText()
        val strings = Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(text)
            .filter { "translatable=\"false\"" !in it.groupValues[2] }.associate { it.groupValues[1] to it.groupValues[3] }
        val plurals = Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(text)
            .associate { "plurals/" + it.groupValues[1] to Regex("""<item[^>]*>(.*?)</item>""").findAll(it.groupValues[2]).joinToString(" ") { m -> m.groupValues[1] } }
        return strings + plurals
    }

    @Test
    fun `TC#21 영어·한국어 문구는 같은 이름을 갖고 각 언어로 쓰이며 쓰지 않는 말이 없다`() {
        val en = userStrings("values")
        val ko = userStrings("values-ko")
        assertEquals(en.keys, ko.keys)
        val hangul = Regex("[가-힣]")
        // 두 언어에서 같은 원어를 쓰기로 한 문구(Pages·Tunes 탭, Tune)와 글자 없는 형식 문자열이다.
        val sameInBoth = setOf("tab_webpages", "tab_tunes", "webpage_dialog_tune_label", "edit_label_with_text")
        assertEquals(emptyList<String>(), ko.filter { (k, v) -> k !in sameInBoth && !hangul.containsMatchIn(v) }.keys.toList())
        assertEquals(emptyList<String>(), en.filter { hangul.containsMatchIn(it.value) }.keys.toList())
        val avoidEn = listOf("Plugin", "Webpage", "Web Page", "Setup", "Action", "Hidden", "Click Event", "Floating GNB", "Gesture", "Pick Mode", "Inspector", "Broken Selector", "Revert", "Onboarding", "Tutorial", "Release notes", "Retry")
        val developer = listOf("selector", "inspector", "GNB", "gesture", "setup", "plugin")
        val bad = en.flatMap { (k, v) ->
            avoidEn.filter { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(v) }.map { "$k: $it" } +
                developer.filter { Regex("""\b$it\b""", RegexOption.IGNORE_CASE).containsMatchIn(v) }.map { "$k: $it" }
        } + ko.flatMap { (k, v) -> (listOf("튜토리얼", "업데이트 내역", "재시도") + developer).filter { v.contains(it, ignoreCase = true) }.map { "$k: $it" } }
        assertEquals(emptyList<String>(), bad)
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
