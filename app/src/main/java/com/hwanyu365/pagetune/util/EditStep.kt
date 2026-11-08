package com.hwanyu365.pagetune.util

enum class EditStep(val number: Int) {
    ADDRESS(1), PICK(2), RULE(3), SAVE(4);

    companion object {
        // 로드 실패를 선택보다 먼저 본다 → 선택 해제가 늦거나 빠져도 단계는 주소다.
        fun of(loadFailed: Boolean, hasSelection: Boolean, pageLoaded: Boolean, rulesChanged: Boolean): EditStep = when {
            loadFailed -> ADDRESS
            hasSelection -> RULE
            !pageLoaded -> ADDRESS
            rulesChanged -> SAVE
            else -> PICK
        }
    }
}
