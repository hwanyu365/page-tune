package com.hwanyu365.pagetune.util

/** 비동기 결과가 늦게 와도 마지막 요청의 결과만 반영하도록 요청마다 번호를 매긴다. */
class RequestGeneration {
    private var current = 0

    fun next(): Int = ++current

    fun invalidate() {
        current++
    }

    fun isCurrent(token: Int): Boolean = token == current
}
