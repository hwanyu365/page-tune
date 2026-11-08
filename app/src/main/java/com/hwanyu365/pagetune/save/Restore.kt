package com.hwanyu365.pagetune.save

import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.db.PluginWithActions
import com.hwanyu365.pagetune.db.WebPage

/** 목록 삭제·복원의 저장소 연산. 트랜잭션 블록의 수신자로만 얻을 수 있다. */
interface ListWrites {
    suspend fun webPagesByIds(ids: List<Long>): List<WebPage>
    suspend fun deleteWebPages(ids: List<Long>)
    suspend fun existingWebPageIds(ids: List<Long>): List<Long>
    suspend fun insertWebPages(pages: List<WebPage>)
    suspend fun tunesByIds(ids: List<Long>): List<PluginWithActions>
    suspend fun deleteTunes(ids: List<Long>)
    suspend fun existingTuneIds(ids: List<Long>): List<Long>
    suspend fun existingRuleIds(ids: List<Long>): List<Long>
    suspend fun insertTunes(tunes: List<Plugin>)
    suspend fun insertRules(rules: List<PluginAction>)
}

interface ListStore {
    suspend fun <T> inTransaction(block: suspend ListWrites.() -> T): T
}

class RestoreConflictException : IllegalStateException("A row with the same id already exists")

data class TuneRestorePlan(val tunes: List<Plugin>, val rules: List<PluginAction>)

fun planWebPageRestore(snapshot: List<WebPage>): List<WebPage>? = snapshot.takeIf { it.isNotEmpty() }

/** 규칙은 Tune 안 순서가 의미를 가지므로 id 순서로 넣는다. 웹페이지 연결은 남아 있으므로 쓰지 않는다. */
fun planTuneRestore(snapshot: List<PluginWithActions>): TuneRestorePlan? =
    snapshot.takeIf { it.isNotEmpty() }?.let { s -> TuneRestorePlan(s.map { it.plugin }, s.flatMap { it.actions }.sortedBy { it.id }) }

// minSdk 24~35 의 SQLite 바인딩 변수 상한(999) 아래로 IN (:ids) 를 나눈다.
private const val ID_CHUNK = 900

private suspend fun <T> List<Long>.inChunks(query: suspend (List<Long>) -> List<T>): List<T> = chunked(ID_CHUNK).flatMap { query(it) }

suspend fun deleteWebPagesWithSnapshot(ids: List<Long>, store: ListStore): List<WebPage> = store.inTransaction {
    ids.inChunks { webPagesByIds(it) }.also { ids.chunked(ID_CHUNK).forEach { deleteWebPages(it) } }
}

// 저장소 삽입이 덮어쓰기여도 기존 행을 지우지 않도록 같은 트랜잭션 안에서 먼저 충돌을 확인한다.
suspend fun restoreWebPages(plan: List<WebPage>, store: ListStore): Unit = store.inTransaction {
    if (plan.map { it.id }.inChunks { existingWebPageIds(it) }.isNotEmpty()) throw RestoreConflictException()
    insertWebPages(plan)
}

suspend fun deleteTunesWithSnapshot(ids: List<Long>, store: ListStore): List<PluginWithActions> = store.inTransaction {
    ids.inChunks { tunesByIds(it) }.also { ids.chunked(ID_CHUNK).forEach { deleteTunes(it) } }
}

suspend fun restoreTunes(plan: TuneRestorePlan, store: ListStore): Unit = store.inTransaction {
    if (plan.tunes.map { it.id }.inChunks { existingTuneIds(it) }.isNotEmpty() || plan.rules.map { it.id }.inChunks { existingRuleIds(it) }.isNotEmpty()) {
        throw RestoreConflictException()
    }
    insertTunes(plan.tunes)
    insertRules(plan.rules)
}
