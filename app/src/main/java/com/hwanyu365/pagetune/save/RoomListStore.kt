package com.hwanyu365.pagetune.save

import androidx.room.withTransaction
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.PluginAction
import com.hwanyu365.pagetune.db.WebPage

class RoomListStore(private val db: AppDatabase) : ListStore {

    override suspend fun <T> inTransaction(block: suspend ListWrites.() -> T): T = db.withTransaction {
        object : ListWrites {
            override suspend fun webPagesByIds(ids: List<Long>) = db.webPageDao().getWebPagesByIds(ids)
            override suspend fun deleteWebPages(ids: List<Long>) = db.webPageDao().deleteWebPagesByIds(ids)
            override suspend fun existingWebPageIds(ids: List<Long>) = db.webPageDao().existingIds(ids)
            override suspend fun insertWebPages(pages: List<WebPage>) = db.webPageDao().insertNewWebPages(pages)
            override suspend fun tunesByIds(ids: List<Long>) = db.pluginDao().getPluginsWithActionsByIds(ids)
            override suspend fun deleteTunes(ids: List<Long>) = db.pluginDao().deletePluginsByIds(ids)
            override suspend fun existingTuneIds(ids: List<Long>) = db.pluginDao().existingPluginIds(ids)
            override suspend fun existingRuleIds(ids: List<Long>) = db.pluginDao().existingActionIds(ids)
            override suspend fun insertTunes(tunes: List<Plugin>) = db.pluginDao().insertNewPlugins(tunes)
            override suspend fun insertRules(rules: List<PluginAction>) = db.pluginDao().insertNewActions(rules)
        }.block()
    }
}
