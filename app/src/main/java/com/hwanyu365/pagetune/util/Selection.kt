package com.hwanyu365.pagetune.util

/** 삭제 선택 모드의 선택 상태. 카드 탭과 체크박스 탭이 같은 상태를 바꾼다. */
class Selection {
    private val selected = linkedSetOf<Long>()

    fun contains(id: Long) = id in selected

    fun toggle(id: Long) = set(id, id !in selected)

    fun set(id: Long, checked: Boolean) {
        if (checked) selected.add(id) else selected.remove(id)
    }

    fun selectAll(ids: List<Long>) {
        selected.addAll(ids)
    }

    fun clear() = selected.clear()

    fun ids(): List<Long> = selected.toList()
}
