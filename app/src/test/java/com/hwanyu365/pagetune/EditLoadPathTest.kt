package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class EditLoadPathTest {

    private val edit = File("src/main/java").walk().single { it.name == "PluginEditActivity.kt" }.readText()

    private fun handlers() = Regex("""loadChecked\(binding\.wvInspector, (\w+), ::(\w+)\)""").findAll(edit)
        .map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test
    fun `TC#52 사용자가 입력한 주소만 주소 칸 오류, 앱이 고른 주소는 다시 시도 없는 열 수 없는 주소로 거부한다`() {
        assertEquals(
            listOf("it" to "showUnopenableAddress", "url" to "showAddressError", "it" to "showUnopenableAddress", "it" to "showUnopenableAddress"),
            handlers(),
        )
    }

    @Test
    fun `TC#52 다시 시도는 실패한 주소를 같은 거부 처리로 연다`() {
        assertEquals(1, Regex("""failedUrl\?\.let \{ webClient\.loadChecked\(binding\.wvInspector, it, ::showUnopenableAddress\) \}""").findAll(edit).count())
    }
}
