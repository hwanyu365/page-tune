package com.hwanyu365.pagetune.util

object HexColor {
    private val HEX = Regex("[0-9A-Fa-f]+")

    /** 저장 형식(`#RRGGBB` 대문자)만 만든다. 알파·색 이름은 받지 않는다. */
    fun parse(input: String): String? {
        val body = input.trim().removePrefix("#")
        if (!HEX.matches(body)) return null
        val six = when (body.length) {
            6 -> body
            3 -> body.map { "$it$it" }.joinToString("")
            else -> return null
        }
        return "#" + six.uppercase()
    }
}
