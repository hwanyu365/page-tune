package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorInputPathTest {

    private fun source(name: String) = File("src/main/java").walk().single { it.name == name }.readText()

    private fun args(src: String, call: String) =
        Regex("""(?<![\w.]|fun )${Regex.escape(call)}\((.*)\)""").findAll(src).map { it.groupValues[1] }.toSet()

    private val dialog = source("ColorPickerDialog.kt")

    @Test
    fun `TC#51 다이얼로그 결과는 현재 색 또는 색 없음뿐이다`() {
        assertEquals(setOf("current", "null"), args(dialog, "onPick"))
    }

    @Test
    fun `TC#51 현재 색은 해석 결과·프리셋·슬라이더 값으로만 바뀐다`() {
        val assigned = Regex("""\bcurrent\s*=\s*([^\n]+)""").findAll(dialog).map { it.groupValues[1].trim() }.toSet()
        assertEquals(setOf("initial?.let(HexColor::parse) ?: ColorPresets.ALL.first()", "hex"), assigned)

        assertEquals(
            setOf("hex", "parsed, fromHexInput = true", "String.format(\"#%06X\", 0xFFFFFF and rgb)", "current"),
            args(dialog, "select"),
        )
        assertEquals(setOf("text?.toString().orEmpty()"), args(dialog, "HexColor.parse"))
    }

    @Test
    fun `TC#51 편집 화면의 색 값은 다이얼로그 결과로만 정해진다`() {
        val edit = source("PluginEditActivity.kt")
        val assigned = Regex("""\bcolor(Bg|Text)\s*=\s*([^\n]+)""").findAll(edit).map { it.groupValues[2].trim() }.toSet()

        assertEquals(setOf("picked", "null"), assigned)
    }
}
