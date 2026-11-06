package com.hwanyu365.pagetune.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {

    @Test
    fun `TC#3 체크박스가 체크 상태로 알리면 선택된다`() {
        val s = Selection()

        s.set(1, true)

        assertTrue(s.contains(1))
        assertEquals(listOf(1L), s.ids())
    }

    @Test
    fun `TC#3 체크박스가 해제 상태로 알리면 선택에서 빠진다`() {
        val s = Selection().apply { set(1, true) }

        s.set(1, false)

        assertFalse(s.contains(1))
    }

    @Test
    fun `TC#3 같은 상태를 여러 번 알려도 한 번만 들어간다`() {
        val s = Selection()

        s.set(1, true)
        s.set(1, true)

        assertEquals(listOf(1L), s.ids())
    }

    @Test
    fun `TC#3 카드 탭은 선택 상태를 뒤집는다`() {
        val s = Selection()

        s.toggle(2)
        assertTrue(s.contains(2))
        s.toggle(2)
        assertFalse(s.contains(2))
    }

    @Test
    fun `TC#3 카드 탭 뒤 체크박스 상태 통지가 같은 상태면 선택이 유지된다`() {
        val s = Selection()

        s.toggle(3)
        s.set(3, true)

        assertEquals(listOf(3L), s.ids())
    }

    @Test
    fun `TC#3 전체 선택과 해제`() {
        val s = Selection()

        s.selectAll(listOf(1, 2, 3))
        assertEquals(listOf(1L, 2L, 3L), s.ids())
        s.clear()
        assertTrue(s.ids().isEmpty())
    }
}
