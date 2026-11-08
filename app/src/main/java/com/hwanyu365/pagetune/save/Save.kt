package com.hwanyu365.pagetune.save

import com.hwanyu365.pagetune.db.PluginAction

data class SavePlan(
    val name: String,
    val testUrl: String,
    val rules: List<PluginAction>,
    val pluginId: Long?,
    val linkWebPageId: Long?,
    val pullNavigation: Boolean = false,
)

/** 저장소의 쓰기 연산. 트랜잭션 블록의 수신자로만 얻을 수 있다. */
interface TuneWrites {
    suspend fun insertTune(name: String, testUrl: String, pullNavigation: Boolean): Long
    suspend fun updateTune(id: Long, name: String, testUrl: String, pullNavigation: Boolean)
    suspend fun replaceRules(pluginId: Long, rules: List<PluginAction>)
    suspend fun linkWebPage(webPageId: Long, pluginId: Long)
}

interface TuneStore {
    suspend fun <T> inTransaction(block: suspend TuneWrites.() -> T): T
}

/** 이름이 비면 null. 연결은 새 Tune 일 때만 한다. */
fun planSave(name: String, testUrl: String, rules: List<PluginAction>, pluginId: Long?, linkWebPageId: Long?, pullNavigation: Boolean = false): SavePlan? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    return SavePlan(trimmed, testUrl.trim(), rules, pluginId, if (pluginId == null) linkWebPageId else null, pullNavigation)
}

/** 저장된 Tune id 를 돌려준다. */
suspend fun executeSave(plan: SavePlan, store: TuneStore): Long = store.inTransaction {
    val id = if (plan.pluginId == null) {
        insertTune(plan.name, plan.testUrl, plan.pullNavigation)
    } else {
        updateTune(plan.pluginId, plan.name, plan.testUrl, plan.pullNavigation)
        plan.pluginId
    }
    replaceRules(id, plan.rules)
    plan.linkWebPageId?.let { linkWebPage(it, id) }
    id
}
