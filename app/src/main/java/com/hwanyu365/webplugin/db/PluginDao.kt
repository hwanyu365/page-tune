package com.hwanyu365.webplugin.db

import androidx.room.*

data class PluginWithActions(
    @Embedded val plugin: Plugin,
    @Relation(
        parentColumn = "id",
        entityColumn = "pluginId"
    )
    val actions: List<PluginAction>
)

@Dao
interface PluginDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: Plugin): Long

    @Update
    suspend fun updatePlugin(plugin: Plugin)

    @Delete
    suspend fun deletePlugin(plugin: Plugin)

    @Query("SELECT * FROM plugins ORDER BY id DESC")
    suspend fun getAllPlugins(): List<Plugin>

    @Transaction
    @Query("SELECT * FROM plugins WHERE id = :pluginId")
    suspend fun getPluginWithActions(pluginId: Long): PluginWithActions?

    @Transaction
    @Query("SELECT * FROM plugins ORDER BY id DESC")
    suspend fun getAllPluginsWithActions(): List<PluginWithActions>

    // Plugin Actions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PluginAction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<PluginAction>)

    @Query("DELETE FROM plugin_actions WHERE pluginId = :pluginId")
    suspend fun deleteActionsByPluginId(pluginId: Long)

    @Query("DELETE FROM plugins WHERE id IN (:ids)")
    suspend fun deletePluginsByIds(ids: List<Long>)
}
