package com.hwanyu365.pagetune.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

sealed interface ExternalTarget {
    data class View(val url: String) : ExternalTarget
    data class IntentTarget(val data: String?, val pkg: String?, val fallbackUrl: String?) : ExternalTarget
}

sealed interface LinkDecision {
    data object InWebView : LinkDecision
    data object Block : LinkDecision
    data class External(val target: ExternalTarget) : LinkDecision
}

object UrlPolicy {

    private val WEB = setOf("http", "https")
    private val BLOCKED = setOf("javascript", "data", "file", "content", "blob", "filesystem", "about", "chrome", "view-source")
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val PORT = Regex("^\\d+(/.*)?$", RegexOption.DOT_MATCHES_ALL)
    private const val INTENT_BLOCK = "#Intent;"

    private fun scheme(url: String): String? = SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase()

    fun classifyLink(url: String, isMainFrame: Boolean, hasGesture: Boolean, isRedirect: Boolean, appId: String): LinkDecision {
        val scheme = scheme(url) ?: return LinkDecision.Block
        if (scheme in WEB) return LinkDecision.InWebView
        val lower = url.trim().lowercase()
        if (lower == "about:blank" || lower == "about:srcdoc") return LinkDecision.InWebView
        if (scheme in BLOCKED) return LinkDecision.Block
        if (!isMainFrame || (!hasGesture && !isRedirect)) return LinkDecision.Block
        val target = if (scheme == "intent") parseIntent(url, appId) ?: return LinkDecision.Block else ExternalTarget.View(url)
        return LinkDecision.External(target)
    }

    fun parseIntent(raw: String, appId: String): ExternalTarget.IntentTarget? {
        val url = raw.trim()
        val at = url.indexOf(INTENT_BLOCK)
        if (at < 0 || !url.endsWith(";end")) return null
        val rest = url.substring("intent:".length, at)
        val fields = url.substring(at + INTENT_BLOCK.length, url.length - "end".length).split(';')
            .mapNotNull { f -> f.indexOf('=').takeIf { it > 0 }?.let { f.substring(0, it) to f.substring(it + 1) } }
            .toMap()
        fun value(key: String) = fields[key]?.let(::percentDecode)
        val scheme = value("scheme")
        val pkg = value("package")
        if (scheme != null && scheme.lowercase() in BLOCKED) return null
        if (pkg == appId) return null
        val fallback = value("S.browser_fallback_url")?.takeIf { scheme(it) in WEB }
        return ExternalTarget.IntentTarget(scheme?.let { "$it:$rest" }, pkg, fallback)
    }

    fun isBrowserDownload(url: String): Boolean = scheme(url) in WEB

    fun normalizeInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val match = SCHEME.find(trimmed)?.takeUnless { PORT.matches(it.groupValues[2]) }
        val url = when {
            match == null -> "https://$trimmed"
            match.groupValues[1].lowercase() in WEB -> match.groupValues[1].lowercase() + ":" + match.groupValues[2]
            else -> return null
        }
        return url.takeIf { host(it).isNotEmpty() }
    }

    private fun host(url: String): String {
        val afterScheme = url.substringAfter(':')
        if (!afterScheme.startsWith("//")) return ""
        val authority = afterScheme.substring(2).takeWhile { it !in "/?#" }
        return authority.substringAfterLast('@').replace(Regex(":\\d*$"), "")
    }

    private fun percentDecode(value: String): String? {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%') {
                if (i + 2 >= value.length) return null
                bytes.write(value.substring(i + 1, i + 3).toIntOrNull(16) ?: return null)
                i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }
}
