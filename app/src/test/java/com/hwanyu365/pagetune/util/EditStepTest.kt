package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EditStepTest {

    private fun step(loadFailed: Boolean = false, selected: Boolean = false, loaded: Boolean = false, rulesChanged: Boolean = false) =
        EditStep.of(loadFailed = loadFailed, hasSelection = selected, pageLoaded = loaded, rulesChanged = rulesChanged)

    @Test
    fun `TC#37 로드 전이면 1 주소`() {
        assertEquals(EditStep.ADDRESS, step())
        assertEquals(EditStep.ADDRESS, step(rulesChanged = true))
    }

    @Test
    fun `TC#37 로드 완료이고 선택이 없고 규칙이 그대로면 2 요소 선택`() {
        assertEquals(EditStep.PICK, step(loaded = true))
    }

    @Test
    fun `TC#37 로드 완료이고 선택이 있으면 3 규칙`() {
        assertEquals(EditStep.RULE, step(loaded = true, selected = true))
        assertEquals(EditStep.RULE, step(loaded = true, selected = true, rulesChanged = true))
    }

    @Test
    fun `TC#37 로드 완료이고 규칙이 바뀌었고 선택이 없으면 4 저장`() {
        assertEquals(EditStep.SAVE, step(loaded = true, rulesChanged = true))
    }

    @Test
    fun `TC#37 로드 오류면 규칙 변경이나 선택이 있어도 1 주소`() {
        assertEquals(EditStep.ADDRESS, step(loadFailed = true, rulesChanged = true))
        assertEquals(EditStep.ADDRESS, step(loadFailed = true, selected = true))
        assertEquals(EditStep.ADDRESS, step(loadFailed = true, selected = true, loaded = true, rulesChanged = true))
    }

    @Test
    fun `TC#37 단계 번호는 1부터 4`() {
        assertEquals(listOf(1, 2, 3, 4), EditStep.entries.map { it.number })
    }
}
