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

    @Delete
    suspend fun deleteWebPage(webPage: WebPage)

    @Query("DELETE FROM webpages WHERE id IN (:ids)")
    suspend fun deleteWebPagesByIds(ids: List<Long>)

    @Query("SELECT * FROM webpages ORDER BY id DESC")
    suspend fun getAllWebPages(): List<WebPage>

    @Transaction
    @Query("SELECT * FROM webpages ORDER BY id DESC")
    suspend fun getAllWebPagesWithPlugin(): List<WebPageWithPlugin>

    @Query("SELECT * FROM webpages WHERE id = :id")
    suspend fun getWebPageById(id: Long): WebPage?

    @Query("UPDATE webpages SET url = :newUrl WHERE id = :id")
    suspend fun updateUrl(id: Long, newUrl: String)
}
