package com.hwanyu365.pagetune.save

import androidx.room.withTransaction
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction

class RoomTuneStore(private val db: AppDatabase) : TuneStore {

    override suspend fun <T> inTransaction(block: suspend TuneWrites.() -> T): T = db.withTransaction {
        object : TuneWrites {
            override suspend fun insertTune(name: String, testUrl: String, pullNavigation: Boolean) =
                db.pluginDao().insertPlugin(Plugin(name = name, testUrl = testUrl, pullNavigation = pullNavigation))

            override suspend fun updateTune(id: Long, name: String, testUrl: String, pullNavigation: Boolean) =
                db.pluginDao().updatePlugin(Plugin(id = id, name = name, testUrl = testUrl, pullNavigation = pullNavigation))

            override suspend fun replaceRules(pluginId: Long, rules: List<PluginAction>) {
                db.pluginDao().deleteActionsByPluginId(pluginId)
                db.pluginDao().insertActions(rules.map { it.copy(pluginId = pluginId) })
            }

            override suspend fun linkWebPage(webPageId: Long, pluginId: Long) =
                db.webPageDao().updatePluginId(webPageId, pluginId)
        }.block()
    }
}
