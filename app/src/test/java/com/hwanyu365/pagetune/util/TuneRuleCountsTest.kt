package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneRuleCountsTest {

    private fun a(type: String) = PluginAction(pluginId = 1, actionType = type, targetSelector = "{}")

    @Test
    fun `TC#21 종류별 개수를 센다`() {
        val counts = TuneRuleCounts.of(listOf(a("hidden"), a("hidden"), a("color"), a("click"), a("scroll")))

        assertEquals(TuneRuleCounts(hide = 2, color = 1, click = 1, start = 1), counts)
    }

    @Test
    fun `TC#21 0개 종류는 표시 목록에서 빠진다`() {
        val counts = TuneRuleCounts.of(listOf(a("hidden"), a("hidden"), a("color")))

        assertEquals(listOf(TuneRuleCounts.Kind.HIDE to 2, TuneRuleCounts.Kind.COLOR to 1), counts.nonZero())
    }

    @Test
    fun `TC#21 규칙이 없으면 비어 있다`() {
        assertTrue(TuneRuleCounts.of(emptyList()).nonZero().isEmpty())
    }
}
