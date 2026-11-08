package com.hwanyu365.pagetune.util

import java.net.IDN

data class DomainAvatar(val host: String, val letter: String?, val colorIndex: Int) {
    companion object {
        fun of(url: String?, fallbackName: String): DomainAvatar {
            val host = hostOf(url.orEmpty())
            val source = listOf(host, fallbackName).firstOrNull { firstLetter(it) != null }
            return DomainAvatar(host, source?.let(::firstLetter), Math.floorMod((source ?: "").hashCode(), 8))
        }

        private fun hostOf(url: String): String {
            if (url.isBlank()) return ""
            val withScheme = if ("://" in url) url else "https://$url"
            val raw = withScheme.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfterLast('@').substringBefore(':').lowercase()
            val unicode = runCatching { IDN.toUnicode(raw) }.getOrDefault(raw)
            return unicode.removePrefix("www.").takeIf { it != unicode } ?: unicode.removePrefix("m.")
        }

        private fun firstLetter(s: String): String? {
            val cp = s.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst()
            return if (cp.isPresent) String(Character.toChars(cp.asInt)).uppercase() else null
        }
    }
}
