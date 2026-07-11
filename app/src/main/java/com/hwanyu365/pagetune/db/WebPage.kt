package com.hwanyu365.pagetune.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "webpages")
data class WebPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val pluginId: Long? // Nullable if no plugin is associated
)
