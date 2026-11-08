package com.hwanyu365.pagetune.util

sealed interface Intro {
    data object GettingStarted : Intro
    data class WhatsNew(val versions: List<Int>) : Intro
    data object None : Intro
}

/** [recordNow] 가 있으면 보여줄 것과 무관하게 바로 기록한다. 시작 안내·새 소식은 닫을 때 현재 versionCode 를 기록한다. */
data class IntroDecision(val show: Intro, val recordNow: Int?)

fun decideIntro(recorded: Int?, current: Int, hasData: Boolean, noteVersions: List<Int>): IntroDecision {
    if (recorded == null && !hasData) return IntroDecision(Intro.GettingStarted, null)
    if (recorded != null && recorded >= current) return IntroDecision(Intro.None, null)
    val versions = noteVersions.filter { (recorded == null || it > recorded) && it <= current }.sortedDescending()
    return if (versions.isEmpty()) IntroDecision(Intro.None, current) else IntroDecision(Intro.WhatsNew(versions), null)
}

fun versionNameOf(code: Int): String = "${code / 10000}.${code / 100 % 100}.${code % 100}"
