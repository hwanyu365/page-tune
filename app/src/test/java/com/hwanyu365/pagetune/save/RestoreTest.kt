package com.hwanyu365.pagetune.save

import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.db.PluginWithActions
import com.hwanyu365.pagetune.db.WebPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 트랜잭션 블록 안의 쓰기는 복사본에 하고 블록이 정상 종료될 때만 반영한다. 블록이 끝난 뒤의 호출을 센다.
 * 삽입은 Room 의 REPLACE 처럼 같은 id 를 덮어쓰고, Tune 을 덮어쓰면 그 Tune 의 규칙이 CASCADE 로 지워진다 → 덮어쓰기 방지는 실행 함수가 해야 한다.
 */
private class FakeListStore(
    pages: List<WebPage> = emptyList(),
    tunes: List<Plugin> = emptyList(),
    rules: List<PluginAction> = emptyList(),
    private val failOnLastRule: Boolean = false,
    private val failOnPageInsert: Boolean = false,
) : ListStore {
    var pages = pages.associateBy { it.id }
    var tunes = tunes.associateBy { it.id }
    var rules = rules.associateBy { it.id }
    var transactions = 0
    var outsideCalls = 0
    val idCallSizes = mutableListOf<Int>()

    override suspend fun <T> inTransaction(block: suspend ListWrites.() -> T): T {
        transactions++
        val p = pages.toMutableMap()
        val t = tunes.toMutableMap()
        val r = rules.toMutableMap()
        var open = true
        fun touch() {
            if (!open) outsideCalls++
        }
        fun touch(ids: List<Long>) {
            touch()
            idCallSizes += ids.size
        }
        val tx = object : ListWrites {
            override suspend fun webPagesByIds(ids: List<Long>): List<WebPage> { touch(ids); return ids.mapNotNull { p[it] } }
            override suspend fun deleteWebPages(ids: List<Long>) { touch(ids); ids.forEach { p.remove(it) } }
            override suspend fun existingWebPageIds(ids: List<Long>): List<Long> { touch(ids); return ids.filter { it in p } }
            override suspend fun insertWebPages(pages: List<WebPage>) {
                touch()
                if (failOnPageInsert) throw IllegalStateException("disk full")
                pages.forEach { p[it.id] = it }
            }
            override suspend fun tunesByIds(ids: List<Long>): List<PluginWithActions> {
                touch(ids)
                return ids.mapNotNull { id -> t[id]?.let { PluginWithActions(it, r.values.filter { a -> a.pluginId == id }) } }
            }
            override suspend fun deleteTunes(ids: List<Long>) {
                touch(ids)
                ids.forEach { id -> t.remove(id); r.values.filter { it.pluginId == id }.forEach { r.remove(it.id) } }
            }
            override suspend fun existingTuneIds(ids: List<Long>): List<Long> { touch(ids); return ids.filter { it in t } }
            override suspend fun existingRuleIds(ids: List<Long>): List<Long> { touch(ids); return ids.filter { it in r } }
            override suspend fun insertTunes(tunes: List<Plugin>) {
                touch()
                tunes.forEach { tune ->
                    if (tune.id in t) r.values.filter { it.pluginId == tune.id }.forEach { r.remove(it.id) }
                    t[tune.id] = tune
                }
            }
            override suspend fun insertRules(rules: List<PluginAction>) {
                touch()
                rules.forEach {
                    if (failOnLastRule && it == rules.last()) throw IllegalStateException("disk full")
                    r[it.id] = it
                }
            }
        }
        try {
            val result = tx.block()
            pages = p
            tunes = t
            rules = r
            return result
        } finally {
            open = false
        }
    }
}

private fun page(id: Long, pluginId: Long? = null) = WebPage(id = id, title = "t$id", url = "https://s$id.example", pluginId = pluginId)

private fun rule(id: Long, pluginId: Long) =
    PluginAction(id = id, pluginId = pluginId, actionType = "hidden", targetSelector = "#r$id", triggerType = "gnb_next", gestureAreaPercent = 30, styleCss = "x$id")

class RestoreTest {

    @Test
    fun `TC#33 웹페이지 복원 계획은 스냅샷 행을 같은 id·값으로 넣는다`() {
        val snapshot = listOf(page(5, 2), page(3))

        assertEquals(snapshot, planWebPageRestore(snapshot))
    }

    @Test
    fun `TC#33 빈 스냅샷은 계획이 없다`() {
        assertNull(planWebPageRestore(emptyList()))
    }

    @Test
    fun `TC#34 웹페이지 삭제는 스냅샷 읽기와 삭제를 블록 하나에서 한다`() = runBlocking {
        val store = FakeListStore(pages = listOf(page(1), page(2, 9), page(3)))

        val snapshot = deleteWebPagesWithSnapshot(listOf(2, 3), store)

        assertEquals(listOf(page(2, 9), page(3)), snapshot)
        assertEquals(setOf(1L), store.pages.keys)
        assertEquals(1, store.transactions)
        assertEquals(0, store.outsideCalls)
    }

    @Test
    fun `TC#34 웹페이지 복원은 블록 하나에서 모두 넣는다`() = runBlocking {
        val store = FakeListStore(pages = listOf(page(1)))

        restoreWebPages(listOf(page(2, 9), page(3)), store)

        assertEquals(mapOf(1L to page(1), 2L to page(2, 9), 3L to page(3)), store.pages)
        assertEquals(1, store.transactions)
        assertEquals(0, store.outsideCalls)
    }

    @Test
    fun `TC#34 같은 id 가 있으면 반영 0, 기존 행 불변`() = runBlocking {
        val existing = WebPage(id = 3, title = "new", url = "https://other", pluginId = null)
        val store = FakeListStore(pages = listOf(existing))

        try {
            restoreWebPages(listOf(page(2), page(3)), store)
            fail("conflict expected")
        } catch (e: RestoreConflictException) {
        }

        assertEquals(mapOf(3L to existing), store.pages)
    }

    @Test
    fun `TC#34 삽입 중 예외 → 반영 0, 예외 전달`() = runBlocking {
        val store = FakeListStore(pages = listOf(page(1)), failOnPageInsert = true)

        try {
            restoreWebPages(listOf(page(2)), store)
            fail("exception expected")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }

        assertEquals(setOf(1L), store.pages.keys)
    }

    private val tune = Plugin(id = 4, name = "n", testUrl = "https://a")
    private val tuneRules = listOf(rule(12, 4), rule(10, 4), rule(11, 4))

    @Test
    fun `TC#47 Tune 복원 계획은 Tune 1·규칙 3 을 같은 id·모든 컬럼으로, 규칙은 id 순서`() {
        val plan = planTuneRestore(listOf(PluginWithActions(tune, tuneRules)))!!

        assertEquals(listOf(tune), plan.tunes)
        assertEquals(listOf(rule(10, 4), rule(11, 4), rule(12, 4)), plan.rules)
    }

    @Test
    fun `TC#47 빈 스냅샷은 계획이 없다`() {
        assertNull(planTuneRestore(emptyList()))
    }

    @Test
    fun `TC#48 Tune 삭제는 스냅샷 읽기와 삭제를 블록 하나에서 하고 연결은 남긴다`() = runBlocking {
        val store = FakeListStore(pages = listOf(page(1, 4)), tunes = listOf(tune), rules = tuneRules)

        val snapshot = deleteTunesWithSnapshot(listOf(4), store)

        assertEquals(tune, snapshot.single().plugin)
        assertEquals(tuneRules.toSet(), snapshot.single().actions.toSet())
        assertTrue(store.tunes.isEmpty() && store.rules.isEmpty())
        assertEquals(page(1, 4), store.pages[1])
        assertEquals(1, store.transactions)
        assertEquals(0, store.outsideCalls)
    }

    @Test
    fun `TC#48 Tune 복원은 Tune·규칙 삽입이 블록 하나이고 웹페이지는 쓰지 않는다`() = runBlocking {
        val store = FakeListStore(pages = listOf(page(1, 4)))

        restoreTunes(planTuneRestore(listOf(PluginWithActions(tune, tuneRules)))!!, store)

        assertEquals(mapOf(4L to tune), store.tunes)
        assertEquals(tuneRules.associateBy { it.id }, store.rules)
        assertEquals(mapOf(1L to page(1, 4)), store.pages)
        assertEquals(1, store.transactions)
        assertEquals(0, store.outsideCalls)
    }

    @Test
    fun `TC#48 규칙 삽입 중 예외 → Tune 행 포함 반영 0`() = runBlocking {
        val store = FakeListStore(failOnLastRule = true)

        try {
            restoreTunes(planTuneRestore(listOf(PluginWithActions(tune, tuneRules)))!!, store)
            fail("exception expected")
        } catch (e: IllegalStateException) {
        }

        assertTrue(store.tunes.isEmpty() && store.rules.isEmpty())
    }

    @Test
    fun `TC#48 같은 id 의 Tune 이 있으면 반영 0, 기존 Tune 의 규칙 불변`() = runBlocking {
        val other = Plugin(id = 4, name = "other", testUrl = null)
        val otherRules = listOf(rule(20, 4))
        val store = FakeListStore(tunes = listOf(other), rules = otherRules)

        try {
            restoreTunes(planTuneRestore(listOf(PluginWithActions(tune, tuneRules)))!!, store)
            fail("conflict expected")
        } catch (e: RestoreConflictException) {
        }

        assertEquals(mapOf(4L to other), store.tunes)
        assertEquals(otherRules.associateBy { it.id }, store.rules)
    }

    @Test
    fun `TC#48 같은 id 의 규칙이 있으면 반영 0`() = runBlocking {
        val stray = rule(11, 99)
        val store = FakeListStore(rules = listOf(stray))

        try {
            restoreTunes(planTuneRestore(listOf(PluginWithActions(tune, tuneRules)))!!, store)
            fail("conflict expected")
        } catch (e: RestoreConflictException) {
        }

        assertTrue(store.tunes.isEmpty())
        assertEquals(mapOf(11L to stray), store.rules)
    }

    private val many = (1L..1001L).toList()

    @Test
    fun `TC#34 id 1000개 이상 삭제·복원은 호출마다 900개 이하로 나눠 블록 하나에서 모두 반영한다`() = runBlocking {
        val store = FakeListStore(pages = many.map { page(it) })

        val snapshot = deleteWebPagesWithSnapshot(many, store)
        assertEquals(1001, snapshot.size)
        assertTrue(store.pages.isEmpty())
        assertEquals(1, store.transactions)

        restoreWebPages(planWebPageRestore(snapshot)!!, store)
        assertEquals(many.toSet(), store.pages.keys)
        assertEquals(2, store.transactions)
        assertTrue("호출별 id 수 ${store.idCallSizes}", store.idCallSizes.all { it <= 900 })
        assertEquals(1001 * 3, store.idCallSizes.sum())
    }

    @Test
    fun `TC#48 Tune 1000개 이상 삭제, 규칙 1000개 이상 복원은 호출마다 900개 이하로 나눠 블록 하나에서 모두 반영한다`() = runBlocking {
        val tunes = many.map { Plugin(id = it, name = "n$it", testUrl = null) }
        val store = FakeListStore(tunes = tunes)
        val snapshot = deleteTunesWithSnapshot(many, store)
        assertEquals(1001, snapshot.size)
        assertTrue(store.tunes.isEmpty())

        val manyRules = many.map { rule(it, 4) }
        restoreTunes(planTuneRestore(listOf(PluginWithActions(tune, manyRules)))!!, store)
        assertEquals(manyRules.associateBy { it.id }, store.rules)
        assertEquals(2, store.transactions)
        assertTrue("호출별 id 수 ${store.idCallSizes}", store.idCallSizes.all { it <= 900 })
        assertEquals(1001 * 2 + 1 + 1001, store.idCallSizes.sum())
    }
}
