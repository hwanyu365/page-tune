package com.hwanyu365.pagetune.ui

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.hwanyu365.pagetune.R

/** 삭제 선택 모드의 상단 바. 닫기 버튼과 시스템 뒤로가기는 ActionMode 가 처리한다. */
class SelectionMode(private val activity: AppCompatActivity, private val listener: Listener) : ActionMode.Callback {

    interface Listener {
        fun onSelectAll()
        fun onDeleteSelected()
        fun onSelectionModeEnded()
    }

    private var mode: ActionMode? = null

    fun start(count: Int) {
        if (mode == null) mode = activity.startSupportActionMode(this)
        update(count)
    }

    fun update(count: Int) {
        mode?.title = activity.resources.getQuantityString(R.plurals.list_selected_count, count, count)
    }

    fun finish() {
        mode?.finish()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.menu_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_select_all -> listener.onSelectAll()
            R.id.action_delete_selected -> listener.onDeleteSelected()
            else -> return false
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        this.mode = null
        listener.onSelectionModeEnded()
    }
}
