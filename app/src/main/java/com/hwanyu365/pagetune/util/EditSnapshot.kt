package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction

/** 편집 화면의 저장 대상 값. 규칙은 id 를 뺀 내용으로 비교한다. 새로 추가한 규칙의 id 는 0 이다. */
data class EditSnapshot(val name: String, val testUrl: String, val rules: List<PluginAction>) {

    fun differsFrom(other: EditSnapshot): Boolean = this != other

    fun rulesDifferFrom(other: EditSnapshot): Boolean = rules != other.rules

    companion object {
        fun of(name: String, testUrl: String, rules: List<PluginAction>) =
            EditSnapshot(name.trim(), testUrl.trim(), rules.map { it.copy(id = 0, pluginId = 0) })
    }
}
