package com.hwanyu365.pagetune.util

import org.json.JSONObject

data class ElementLabel(val kind: Kind, val text: String?) {
    enum class Kind { LINK, IMAGE, BUTTON, INPUT, FRAME, MEDIA, AREA }

    companion object {
        private const val MAX = 30
        private val WHITESPACE = Regex("""\s+""")

        fun of(detailsJson: String): ElementLabel? {
            val info = runCatching { JSONObject(detailsJson) }.getOrNull() ?: return null
            val attrs = info.optJSONObject("attributes")
            fun attr(name: String) = attrs?.optString(name).orEmpty()
            val kind = when (info.optString("tagName").lowercase()) {
                "a" -> Kind.LINK
                "img", "picture", "svg" -> Kind.IMAGE
                "button" -> Kind.BUTTON
                "input" -> if (attr("type").lowercase() in setOf("button", "submit", "reset")) Kind.BUTTON else Kind.INPUT
                "textarea", "select" -> Kind.INPUT
                "iframe" -> Kind.FRAME
                "video", "audio" -> Kind.MEDIA
                else -> Kind.AREA
            }
            val text = (listOf("aria-label", "alt", "title").map(::attr) + info.optString("textContent"))
                .map { it.replace(WHITESPACE, " ").trim() }
                .firstOrNull { it.isNotEmpty() }
                ?.let { if (it.length > MAX) it.take(MAX) + "…" else it }
            return ElementLabel(kind, text)
        }
    }
}
