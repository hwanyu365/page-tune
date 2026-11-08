package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONArray
import org.json.JSONObject

/** 화면이 다시 만들어져도 저장하지 않은 편집과 변경 판정 기준을 잃지 않도록 인스턴스 상태에 넣는 값. */
data class EditState(val name: String, val testUrl: String, val rules: List<PluginAction>, val initial: EditSnapshot, val pageUrl: String?, val pullNavigation: Boolean = false) {

    fun encode(): String = JSONObject()
        .put("name", name)
        .put("testUrl", testUrl)
        .put("rules", rules.toJson())
        .put("pullNavigation", pullNavigation)
        .put("initial", JSONObject().put("name", initial.name).put("testUrl", initial.testUrl).put("rules", initial.rules.toJson()).put("pullNavigation", initial.pullNavigation))
        .apply { pageUrl?.let { put("pageUrl", it) } }
        .toString()

    /** 인스턴스 상태가 Binder 거래 한도를 넘으면 앱이 죽는다 → 상한을 넘으면 넘기지 않고 DB·인자로 다시 채우게 둔다. Parcel 은 문자열을 UTF-16 으로 쓴다. */
    fun encodeForInstanceState(limitBytes: Int = MAX_STATE_BYTES): String? = encode().takeIf { it.length * 2 <= limitBytes }

    companion object {
        const val MAX_STATE_BYTES = 200_000

        /** 로드 중이면 WebView 주소는 이전 문서이거나 비어 있다 → 진행 중인 주소가 가장 앞선다. */
        fun pageToRestore(loadingUrl: String?, loadFailed: Boolean, failedUrl: String?, webViewUrl: String?, lastLoadedUrl: String?): String? =
            loadingUrl ?: (if (loadFailed) failedUrl else webViewUrl) ?: lastLoadedUrl

        fun decode(text: String): EditState? = runCatching {
            val o = JSONObject(text)
            val i = o.getJSONObject("initial")
            EditState(
                name = o.getString("name"),
                testUrl = o.getString("testUrl"),
                rules = o.getJSONArray("rules").toRules(),
                initial = EditSnapshot(i.getString("name"), i.getString("testUrl"), i.getJSONArray("rules").toRules(), i.optBoolean("pullNavigation")),
                pageUrl = o.optStringOrNull("pageUrl"),
                pullNavigation = o.optBoolean("pullNavigation"),
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
