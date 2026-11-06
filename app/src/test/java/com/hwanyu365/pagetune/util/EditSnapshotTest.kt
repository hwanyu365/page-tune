package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSnapshotTest {

    private fun rule(id: Long = 0, selector: String = "{\"selector\":\"#a\"}", type: String = "hidden") =
        PluginAction(id = id, pluginId = 1, actionType = type, targetSelector = selector)

    private fun snap(
        name: String = "n",
        url: String = "https://a.com",
        rules: List<PluginAction> = listOf(rule(id = 5), rule(id = 6, selector = "{\"selector\":\"#b\"}"))
    ) = EditSnapshot.of(name, url, rules)

    @Test
    fun `TC#13 변경 없음이면 false`() {
        assertFalse(snap().differsFrom(snap()))
    }

    @Test
    fun `TC#13 규칙 id 만 다르면 false`() {
        val other = snap(rules = listOf(rule(id = 0), rule(id = 0, selector = "{\"selector\":\"#b\"}")))

        assertFalse(snap().differsFrom(other))
    }

    @Test
    fun `TC#13 이름 변경이면 true`() {
        assertTrue(snap().differsFrom(snap(name = "m")))
    }

    @Test
    fun `TC#13 주소 변경이면 true`() {
        assertTrue(snap().differsFrom(snap(url = "https://b.com")))
    }

    @Test
    fun `TC#13 규칙 추가이면 true`() {
        assertTrue(snap().differsFrom(snap(rules = snap().rules + rule())))
    }

    @Test
    fun `TC#13 규칙 삭제이면 true`() {
        assertTrue(snap().differsFrom(snap(rules = listOf(rule(id = 5)))))
    }

    @Test
    fun `TC#13 규칙 순서 변경이면 true`() {
        val reversed = listOf(rule(id = 6, selector = "{\"selector\":\"#b\"}"), rule(id = 5))

        assertTrue(snap().differsFrom(snap(rules = reversed)))
    }

    @Test
    fun `TC#13 규칙 내용 변경이면 true`() {
        assertTrue(snap().differsFrom(snap(rules = listOf(rule(id = 5, type = "color"), rule(id = 6)))))
    }

    @Test
    fun `TC#13 이름 앞뒤 공백만 다르면 false`() {
        assertFalse(snap().differsFrom(snap(name = " n ")))
    }

    @Test
    fun `TC#13 새 Tune 은 빈 값에서 시작해 입력하면 true`() {
        val empty = EditSnapshot.of("", "", emptyList())

        assertFalse(empty.differsFrom(EditSnapshot.of("", "", emptyList())))
        assertTrue(empty.differsFrom(EditSnapshot.of("x", "", emptyList())))
    }
}
