package com.hwanyu365.pagetune.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plugin_actions",
    foreignKeys = [
        ForeignKey(
            entity = Plugin::class,
            parentColumns = ["id"],
            childColumns = ["pluginId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pluginId"])]
)
data class PluginAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pluginId: Long,
    val actionType: String, // "hidden", "click" or "color"
    val targetSelector: String, // JSON dynamic selector details
    val triggerType: String? = null, // "gesture_top", "gesture_bottom", "gnb_prev", "gnb_next"
    val gestureAreaPercent: Int = 40, // Default to 40% from reference app
    val styleCss: String? = null // "color" action 의 CSS 선언부 (e.g. "background-color:#000000;color:#ffffff")
)
