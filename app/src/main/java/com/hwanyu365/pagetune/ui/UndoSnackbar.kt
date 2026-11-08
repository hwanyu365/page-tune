package com.hwanyu365.pagetune.ui

import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.google.android.material.snackbar.Snackbar
import com.hwanyu365.pagetune.R

/** 삭제 되돌리기 창. 스냅샷은 실행취소 동작에만 담기므로 창이 닫히면 함께 버려진다. */
class UndoSnackbar {
    private var bar: Snackbar? = null

    fun show(parent: View, anchor: View?, message: String, onUndo: () -> Unit) {
        close()
        val duration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parent.context.getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(
                UNDO_MILLIS, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
            )
        } else {
            UNDO_MILLIS
        }
        bar = Snackbar.make(parent, message, duration).setAction(R.string.list_undo) { onUndo() }.setAnchorView(anchor).also { it.show() }
    }

    fun close() {
        bar?.dismiss()
        bar = null
    }

    private companion object {
        const val UNDO_MILLIS = 5000
    }
}
