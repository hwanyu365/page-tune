package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONArray
import org.json.JSONObject

/** 화면이 다시 만들어져도 저장하지 않은 편집과 변경 판정 기준을 잃지 않도록 인스턴스 상태에 넣는 값. */
data class EditState(val name: String, val testUrl: String, val rules: List<PluginAction>, val initial: EditSnapshot, val pageUrl: String?) {

    fun encode(): String = JSONObject()
        .put("name", name)
        .put("testUrl", testUrl)
        .put("rules", rules.toJson())
        .put("initial", JSONObject().put("name", initial.name).put("testUrl", initial.testUrl).put("rules", initial.rules.toJson()))
        .apply { pageUrl?.let { put("pageUrl", it) } }
        .toString()

    companion object {
        /** 첫 커밋 전에는 WebView 주소가 비어 있다 → 마지막으로 연 주소로 다시 연다. */
        fun pageToRestore(loadFailed: Boolean, failedUrl: String?, webViewUrl: String?, lastLoadedUrl: String?): String? =
            (if (loadFailed) failedUrl else webViewUrl) ?: lastLoadedUrl

        fun decode(text: String): EditState? = runCatching {
            val o = JSONObject(text)
            val i = o.getJSONObject("initial")
            EditState(
                name = o.getString("name"),
                testUrl = o.getString("testUrl"),
                rules = o.getJSONArray("rules").toRules(),
                initial = EditSnapshot(i.getString("name"), i.getString("testUrl"), i.getJSONArray("rules").toRules()),
                pageUrl = o.optStringOrNull("pageUrl"),
            )
        }.getOrNull()

        private fun List<PluginAction>.toJson() = JSONArray().also { array ->
            forEach { r ->
                array.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("pluginId", r.pluginId)
                        .put("actionType", r.actionType)
                        .put("targetSelector", r.targetSelector)
                        .put("gestureAreaPercent", r.gestureAreaPercent)
                        .apply {
                            r.triggerType?.let { put("triggerType", it) }
                            r.styleCss?.let { put("styleCss", it) }
                        }
                )
            }
        }

        private fun JSONArray.toRules() = (0 until length()).map { index ->
            val r = getJSONObject(index)
            PluginAction(
                id = r.getLong("id"),
                pluginId = r.getLong("pluginId"),
                actionType = r.getString("actionType"),
                targetSelector = r.getString("targetSelector"),
                triggerType = r.optStringOrNull("triggerType"),
                gestureAreaPercent = r.getInt("gestureAreaPercent"),
                styleCss = r.optStringOrNull("styleCss"),
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? = if (has(key)) getString(key) else null
    }
}
