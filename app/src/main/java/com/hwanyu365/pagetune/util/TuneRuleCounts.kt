package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction

data class TuneRuleCounts(val hide: Int = 0, val color: Int = 0, val click: Int = 0, val start: Int = 0) {

    enum class Kind { HIDE, COLOR, CLICK, START }

    fun nonZero(): List<Pair<Kind, Int>> =
        listOf(Kind.HIDE to hide, Kind.COLOR to color, Kind.CLICK to click, Kind.START to start).filter { it.second > 0 }

    companion object {
        fun of(actions: List<PluginAction>) = TuneRuleCounts(
            hide = actions.count { it.actionType == "hidden" },
            color = actions.count { it.actionType == "color" },
            click = actions.count { it.actionType == "click" },
            start = actions.count { it.actionType == "scroll" }
        )
    }
}
