package com.hwanyu365.pagetune.save

import com.hwanyu365.pagetune.db.PluginAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun rule(sel: String) = PluginAction(pluginId = 0, actionType = "hidden", targetSelector = sel)

/** 트랜잭션 블록 안의 쓰기를 모았다가 블록이 정상 종료될 때만 반영하고, 블록이 끝난 뒤의 쓰기를 따로 센다. */
private class FakeStore(private val failOn: String? = null) : TuneStore {
    val committed = mutableListOf<String>()
    var transactions = 0
    var outsideWrites = 0

    override suspend fun <T> inTransaction(block: suspend TuneWrites.() -> T): T {
        transactions++
        val pending = mutableListOf<String>()
        val tx = object : TuneWrites {
            var open = true
            private fun write(op: String) {
                if (!open) outsideWrites++
                if (op.substringBefore(":") == failOn) throw IllegalStateException(op)
                pending += op
            }
            override suspend fun insertTune(name: String, testUrl: String, pullNavigation: Boolean): Long { write("insert:$name,$testUrl" + pull(pullNavigation)); return 7 }
            override suspend fun updateTune(id: Long, name: String, testUrl: String, pullNavigation: Boolean) = write("update:$id,$name,$testUrl" + pull(pullNavigation))
            private fun pull(on: Boolean) = if (on) ",pull" else ""
            override suspend fun replaceRules(pluginId: Long, rules: List<PluginAction>) = write("rules:$pluginId,${rules.map { it.targetSelector }}")
            override suspend fun linkWebPage(webPageId: Long, pluginId: Long) = write("link:$webPageId,$pluginId")
            fun close() { open = false }
        }
        try {
            val result = tx.block()
            committed += pending
            return result
        } finally {
            tx.close()
        }
    }
}

class SaveTest {

    private val rules = listOf(rule("#b"), rule("#a"))

    @Test
    fun `TC#32 새 Tune 과 연결 id 가 있으면 연결을 포함한다`() {
        val plan = planSave("n", "https://a", rules, pluginId = null, linkWebPageId = 3)!!

        assertEquals(3L, plan.linkWebPageId)
        assertNull(plan.pluginId)
    }

    @Test
    fun `TC#32 새 Tune 이고 연결 id 가 없으면 연결이 없다`() {
        assertNull(planSave("n", "", rules, null, null)!!.linkWebPageId)
    }

    @Test
    fun `TC#32 기존 Tune 이면 연결 id 가 있어도 연결하지 않는다`() {
        assertNull(planSave("n", "", rules, pluginId = 5, linkWebPageId = 3)!!.linkWebPageId)
    }

    @Test
    fun `TC#32 이름이 비면 계획이 없다`() {
        assertNull(planSave("   ", "", rules, null, null))
    }

    @Test
    fun `TC#32 규칙은 편집 상태 순서 그대로이고 이름·주소는 앞뒤 공백을 뺀다`() {
        val plan = planSave(" n ", " https://a ", rules, null, null)!!

        assertEquals(listOf("#b", "#a"), plan.rules.map { it.targetSelector })
        assertEquals("n", plan.name)
        assertEquals("https://a", plan.testUrl)
    }

    @Test
    fun `TC#33 새 Tune 저장은 한 트랜잭션 안에서 Tune 규칙 연결 순으로 쓴다`() = runBlocking {
        val store = FakeStore()

        val id = executeSave(planSave("n", "u", rules, null, 3)!!, store)

        assertEquals(7L, id)
        assertEquals(listOf("insert:n,u", "rules:7,[#b, #a]", "link:3,7"), store.committed)
        assertEquals(1, store.transactions)
        assertEquals(0, store.outsideWrites)
    }

    @Test
    fun `TC#33 기존 Tune 저장은 갱신과 규칙 교체만 하고 같은 id 를 돌려준다`() = runBlocking {
        val store = FakeStore()

        val id = executeSave(planSave("n", "u", rules, 5, 3)!!, store)

        assertEquals(5L, id)
        assertEquals(listOf("update:5,n,u", "rules:5,[#b, #a]"), store.committed)
    }

    @Test
    fun `TC#33 규칙 교체에서 예외가 나면 반영이 없고 예외가 전달된다`() = runBlocking {
        val store = FakeStore(failOn = "rules")

        try {
            executeSave(planSave("n", "u", rules, null, 3)!!, store)
            fail()
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.startsWith("rules"))
        }
        assertEquals(emptyList<String>(), store.committed)
    }

    @Test
    fun `TC#33 연결에서 예외가 나면 반영이 없다`() = runBlocking {
        val store = FakeStore(failOn = "link")

        try {
            executeSave(planSave("n", "u", rules, null, 3)!!, store)
            fail()
        } catch (e: IllegalStateException) {
        }
        assertEquals(emptyList<String>(), store.committed)
    }

    @Test
    fun `TC#60 끝에서 당겨 이동 값은 새 Tune 과 기존 Tune 저장에 그대로 전달된다`() = runBlocking {
        val store = FakeStore()

        executeSave(planSave("n", "u", rules, null, null, pullNavigation = true)!!, store)
        executeSave(planSave("n", "u", rules, 5, null, pullNavigation = true)!!, store)

        assertEquals(listOf("insert:n,u,pull", "rules:7,[#b, #a]", "update:5,n,u,pull", "rules:5,[#b, #a]"), store.committed)
    }
}
