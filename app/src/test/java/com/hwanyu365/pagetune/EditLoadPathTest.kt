package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditLoadPathTest {

    private val edit = File("src/main/java").walk().single { it.name == "PluginEditActivity.kt" }.readText()

    private fun handlers() = Regex("""loadChecked\(binding\.wvInspector, (\w+), ::(\w+)\)""").findAll(edit)
        .map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test
    fun `TC#52 사용자가 입력한 주소만 주소 칸 오류, 앱이 고른 주소(복원한 페이지 주소 포함)는 다시 시도 없는 열 수 없는 주소로 거부한다`() {
        assertEquals(
            listOf("it" to "showUnopenableAddress", "it" to "showUnopenableAddress", "url" to "showAddressError", "it" to "showUnopenableAddress", "it" to "showUnopenableAddress"),
            handlers(),
        )
    }

    @Test
    fun `TC#52 다시 시도는 실패한 주소를 같은 거부 처리로 연다`() {
        assertEquals(1, Regex("""failedUrl\?\.let \{ webClient\.loadChecked\(binding\.wvInspector, it, ::showUnopenableAddress\) \}""").findAll(edit).count())
    }

    @Test
    fun `TC#58 로드 실패 표시는 펼친 시트를 접어 인라인 오류를 가리지 않는다`() {
        val body = edit.substringAfter("private fun showLoadError(").substringBefore("\n    }\n")

        val collapse = "sheet.state = BottomSheetBehavior.STATE_COLLAPSED"
        val lines = body.lines().drop(1)
        val at = lines.indexOfFirst { it.trim() == collapse }
        assertTrue("시트 접기가 한 문장으로 있어야 한다", at >= 0)
        val depth = lines.take(at).sumOf { line -> line.count { it == '{' } - line.count { it == '}' } }
        assertEquals("시트 접기는 분기 밖(함수 본문 최상위)에서 조건 없이 실행돼야 한다", 0, depth)
        assertEquals(1, Regex(Regex.escape(collapse)).findAll(body).count())
    }
}
