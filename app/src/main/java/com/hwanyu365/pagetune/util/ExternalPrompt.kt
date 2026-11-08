package com.hwanyu365.pagetune.util

/** 확인 창 문구의 재료. [intent] 이면 설치돼 있지 않을 때의 대체 주소 유무를 함께 보인다. */
data class ExternalPrompt(val kind: Kind, val subject: String?, val fallback: String?, val intent: Boolean = false) {
    enum class Kind { APP, EMAIL, PHONE, ADDRESS, UNKNOWN }

    companion object {
        const val MAX_LENGTH = 80

        fun describe(target: ExternalTarget, appLabel: (String) -> String?): ExternalPrompt = when (target) {
            is ExternalTarget.IntentTarget -> {
                val fallback = target.fallbackUrl?.let(::cut)
                when {
                    target.pkg != null -> ExternalPrompt(Kind.APP, cut(appLabel(target.pkg) ?: target.pkg), fallback, intent = true)
                    target.data != null -> ExternalPrompt(Kind.ADDRESS, cut(target.data), fallback, intent = true)
                    else -> ExternalPrompt(Kind.UNKNOWN, null, fallback, intent = true)
                }
            }
            is ExternalTarget.View -> {
                val rest = target.url.substringAfter(':')
                when (target.url.substringBefore(':').lowercase()) {
                    "mailto" -> ExternalPrompt(Kind.EMAIL, cut(rest.substringBefore('?')), null)
                    "tel" -> ExternalPrompt(Kind.PHONE, cut(rest), null)
                    else -> ExternalPrompt(Kind.ADDRESS, cut(target.url), null)
                }
            }
        }

        fun canShow(showing: Boolean, finishing: Boolean, destroyed: Boolean): Boolean = !showing && !finishing && !destroyed

        private fun cut(text: String): String = if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH - 1) + "…"
    }
}
