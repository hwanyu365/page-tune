package com.hwanyu365.webplugin.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plugins")
data class Plugin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val testUrl: String? = null
)
