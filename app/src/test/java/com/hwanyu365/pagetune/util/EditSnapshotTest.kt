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

    @Test
    fun `TC#37 단계 판정의 규칙 비교는 이름·주소만 바뀌면 false`() {
        assertFalse(snap(name = "x", url = "https://b.com").rulesDifferFrom(snap()))
    }

    @Test
    fun `TC#37 단계 판정의 규칙 비교는 규칙 추가·순서 변경이면 true, id 만 다르면 false`() {
        val two = snap().rules
        assertTrue(snap(rules = two + rule()).rulesDifferFrom(snap()))
        assertTrue(snap(rules = two.reversed()).rulesDifferFrom(snap()))
        assertFalse(snap(rules = two.map { it.copy(id = 99) }).rulesDifferFrom(snap()))
    }

    @Test
    fun `TC#60 끝에서 당겨 이동 값만 달라도 변경 있음이다`() {
        val rules = listOf(rule())

        assertTrue(EditSnapshot.of("n", "u", rules, pullNavigation = true).differsFrom(EditSnapshot.of("n", "u", rules)))
    }
}
