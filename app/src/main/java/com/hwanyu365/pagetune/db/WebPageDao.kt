package com.hwanyu365.pagetune.db

import androidx.room.*

data class WebPageWithPlugin(
    @Embedded val webPage: WebPage,
    @Relation(
        parentColumn = "pluginId",
        entityColumn = "id"
    )
    val plugin: Plugin?
)

@Dao
interface WebPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebPage(webPage: WebPage): Long

    @Update
    suspend fun updateWebPage(webPage: WebPage)

    @Query("DELETE FROM webpages WHERE id IN (:ids)")
    suspend fun deleteWebPagesByIds(ids: List<Long>)

    @Query("SELECT * FROM webpages WHERE id IN (:ids)")
    suspend fun getWebPagesByIds(ids: List<Long>): List<WebPage>

    @Query("SELECT id FROM webpages WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNewWebPages(pages: List<WebPage>)

    @Query("SELECT EXISTS(SELECT 1 FROM webpages)")
    suspend fun hasAny(): Boolean

    @Transaction
    @Query("SELECT * FROM webpages ORDER BY id DESC")
    suspend fun getAllWebPagesWithPlugin(): List<WebPageWithPlugin>

    @Query("SELECT * FROM webpages WHERE id = :id")
    suspend fun getWebPageById(id: Long): WebPage?

    @Query("UPDATE webpages SET url = :newUrl WHERE id = :id")
    suspend fun updateUrl(id: Long, newUrl: String)

    @Query("UPDATE webpages SET pluginId = :pluginId WHERE id = :id")
    suspend fun updatePluginId(id: Long, pluginId: Long)
}
