package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditStateTest {

    private val rules = listOf(
        PluginAction(id = 7, pluginId = 3, actionType = "hidden", targetSelector = """{"selector":"#a","tagName":"div","textContent":"줄\n바꿈 \"따옴표\"","id":"a","attributes":{}}"""),
        PluginAction(pluginId = 3, actionType = "click", targetSelector = "{}", triggerType = "gesture_bottom", gestureAreaPercent = 25),
        PluginAction(pluginId = 3, actionType = "color", targetSelector = "not json", styleCss = "background-color:#000000;color:#FFFFFF"),
        PluginAction(pluginId = 3, actionType = "scroll", targetSelector = """{"y":120}"""),
    )

    private val initial = EditSnapshot.of("원래 이름", "https://a.com", rules.take(1))

    private val state = EditState(name = "바꾼 이름", testUrl = "https://b.com/x?q=1", rules = rules, initial = initial, pageUrl = "https://b.com/x?q=1#f")

    @Test
    fun `TC#57 편집 상태는 직렬화 왕복 후 같다`() {
        assertEquals(state, EditState.decode(state.encode()))
    }

    @Test
    fun `TC#57 규칙의 모든 필드와 순서가 유지된다`() {
        assertEquals(rules, EditState.decode(state.encode())!!.rules)
    }

    @Test
    fun `TC#57 복원 후에도 변경 판정과 단계 판정 입력이 같다`() {
        val restored = EditState.decode(state.encode())!!
        val before = EditSnapshot.of(state.name, state.testUrl, state.rules)
        val after = EditSnapshot.of(restored.name, restored.testUrl, restored.rules)

        assertTrue(before.differsFrom(state.initial))
        assertEquals(before.differsFrom(state.initial), after.differsFrom(restored.initial))
        assertEquals(before.rulesDifferFrom(state.initial), after.rulesDifferFrom(restored.initial))
    }

    @Test
    fun `TC#57 변경이 없던 상태는 복원 후에도 변경 없음이다`() {
        val clean = EditState("원래 이름", "https://a.com", rules.take(1), initial, null)
        val restored = EditState.decode(clean.encode())!!

        assertFalse(EditSnapshot.of(restored.name, restored.testUrl, restored.rules).differsFrom(restored.initial))
        assertNull(restored.pageUrl)
    }

    @Test
    fun `TC#57 해석할 수 없는 값은 복원하지 않는다`() {
        assertNull(EditState.decode("not json"))
        assertNull(EditState.decode("{}"))
    }

    @Test
    fun `TC#57 로드 중 재생성되면 WebView 주소 대신 마지막으로 연 주소를 복원한다`() {
        assertEquals("https://b.com/", EditState.pageToRestore(loadingUrl = null, loadFailed = false, failedUrl = null, webViewUrl = null, lastLoadedUrl = "https://b.com/"))
        val restored = EditState.decode(state.copy(pageUrl = EditState.pageToRestore(null, false, null, null, "https://b.com/")).encode())!!
        assertEquals("https://b.com/", restored.pageUrl)
    }

    @Test
    fun `TC#57 로드가 끝난 페이지는 WebView 주소, 실패한 페이지는 실패한 주소를 복원한다`() {
        assertEquals("https://b.com/next", EditState.pageToRestore(null, false, null, "https://b.com/next", "https://b.com/"))
        assertEquals("https://down.test/", EditState.pageToRestore(null, true, "https://down.test/", "https://down.test/", "https://b.com/"))
        assertEquals("https://b.com/", EditState.pageToRestore(null, true, null, null, "https://b.com/"))
        assertNull(EditState.pageToRestore(null, false, null, null, null))
    }

    @Test
    fun `TC#57 A 가 그려진 뒤 B 를 로드하는 중이면 진행 중인 B 를 복원한다`() {
        assertEquals("https://b.com/", EditState.pageToRestore(loadingUrl = "https://b.com/", loadFailed = false, failedUrl = null, webViewUrl = "https://a.com/", lastLoadedUrl = "https://b.com/"))
    }

    @Test
    fun `TC#57 실패 뒤 다시 시도하는 중이면 진행 중인 주소가 실패한 주소보다 앞선다`() {
        assertEquals("https://c.com/", EditState.pageToRestore("https://c.com/", true, "https://a.com/", "https://a.com/", "https://a.com/"))
    }

    @Test
    fun `TC#57 직렬화 크기가 인스턴스 상태 상한을 넘으면 저장하지 않는다`() {
        val big = state.copy(rules = List(2000) { rules[0].copy(id = it.toLong()) })

        assertNull(big.encodeForInstanceState())
        assertEquals(state.encode(), state.encodeForInstanceState())
        assertNull(state.encodeForInstanceState(limitBytes = state.encode().length * 2 - 1))
        assertEquals(state.encode(), state.encodeForInstanceState(limitBytes = state.encode().length * 2))
    }

    @Test
    fun `TC#60 끝에서 당겨 이동 값과 처음 상태의 값이 왕복 후 유지된다`() {
        val on = state.copy(pullNavigation = true, initial = EditSnapshot.of("원래 이름", "https://a.com", rules.take(1), pullNavigation = true))

        assertEquals(on, EditState.decode(on.encode()))
    }

    @Test
    fun `TC#60 값이 없는 직렬화는 꺼짐으로 읽는다`() {
        val old = """{"name":"n","testUrl":"","rules":[],"initial":{"name":"n","testUrl":"","rules":[]}}"""
        val decoded = EditState.decode(old)!!

        assertFalse(decoded.pullNavigation)
        assertFalse(decoded.initial.pullNavigation)
    }
}
