package com.hwanyu365.pagetune

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHighlightSheetTest {

    private val edit = File("src/main/java").walk().single { it.name == "PluginEditActivity.kt" }.readText()

    @Test
    fun `TC#54 강조할 요소를 찾으면 펼친 시트를 접는다`() {
        val callback = edit.substringAfter("getHighlightScript(").substringBefore("\n    }\n")
        val afterFound = callback.substringAfter("R.string.edit_rule_not_found").substringAfter("return@evaluateJavascript")

        assertTrue(callback.indexOf("highlightRequests.isCurrent(token)") in 0 until callback.indexOf("STATE_COLLAPSED").coerceAtLeast(0))
        assertTrue(afterFound.contains("sheet.state = BottomSheetBehavior.STATE_COLLAPSED"))
    }
}
