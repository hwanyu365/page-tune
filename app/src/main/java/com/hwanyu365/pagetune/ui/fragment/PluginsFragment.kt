package com.hwanyu365.pagetune.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hwanyu365.pagetune.PluginEditActivity
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.databinding.FragmentPluginsBinding
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.save.RoomListStore
import com.hwanyu365.pagetune.save.deleteTunesWithSnapshot
import com.hwanyu365.pagetune.save.planTuneRestore
import com.hwanyu365.pagetune.save.restoreTunes
import com.hwanyu365.pagetune.ui.SelectionMode
import com.hwanyu365.pagetune.ui.UndoSnackbar
import com.hwanyu365.pagetune.ui.adapter.PluginAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginsFragment : Fragment(), TabPage {

    private lateinit var binding: FragmentPluginsBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: PluginAdapter
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var selectionMode: SelectionMode
    private val undo = UndoSnackbar()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        db = AppDatabase.getDatabase(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPluginsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectionMode = SelectionMode(requireActivity() as AppCompatActivity, object : SelectionMode.Listener {
            override fun onSelectAll() = adapter.selectAll(true)
            override fun onDeleteSelected() = confirmDelete(adapter.getSelectedIds())
            override fun onSelectionModeEnded() {
                adapter.setDeleteMode(false)
                binding.fabAddPlugin.visibility = View.VISIBLE
            }
        })

        adapter = PluginAdapter(
            onEditClick = { plugin -> openEditor(plugin) },
            onMoreClick = { anchor, plugin -> showItemMenu(anchor, plugin) },
            onLongPress = { plugin -> enterDeleteMode(plugin.id) },
            onSelectionChanged = { selectionMode.update(it) }
        )

        binding.rvPlugins.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlugins.adapter = adapter

        binding.fabAddPlugin.setOnClickListener {
            val intent = Intent(requireContext(), PluginEditActivity::class.java)
            startActivity(intent)
        }

        binding.btnEmptyAction.setOnClickListener {
            startActivity(Intent(requireContext(), PluginEditActivity::class.java))
        }

        loadPlugins()
    }

    override fun onResume() {
        super.onResume()
        loadPlugins()
    }

    override fun onPause() {
        super.onPause()
        undo.close()
    }

    private fun openEditor(plugin: Plugin) {
        startActivity(Intent(requireContext(), PluginEditActivity::class.java).putExtra("plugin_id", plugin.id))
    }

    /** Tune 을 액션까지 통째로 복제한다. 비슷한 사이트용 Tune 을 처음부터 다시 만들지 않기 위한 기능이다. */
    private fun duplicatePlugin(plugin: Plugin) {
        mainScope.launch {
            withContext(Dispatchers.IO) {
                val source = db.pluginDao().getPluginWithActions(plugin.id) ?: return@withContext
                // id 를 0 으로 비워야 Room 이 새 행으로 넣는다. 그대로 두면 REPLACE 정책 때문에 원본을 덮어쓴다.
                val newId = db.pluginDao().insertPlugin(
                    source.plugin.copy(id = 0, name = getString(R.string.tune_copy_name, source.plugin.name))
                )
                db.pluginDao().insertActions(source.actions.map { it.copy(id = 0, pluginId = newId) })
            }
            loadPlugins()
            Toast.makeText(requireContext(), R.string.tune_duplicated, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPlugins() {
        mainScope.launch {
            val plugins = withContext(Dispatchers.IO) {
                db.pluginDao().getAllPlugins()
            }
            adapter.setItems(plugins)
            binding.layoutEmpty.visibility = if (plugins.isEmpty()) View.VISIBLE else View.GONE
            binding.rvPlugins.visibility = if (plugins.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete_mode) {
            enterDeleteMode(null)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onTabLeft() {
        if (!this::adapter.isInitialized) return
        selectionMode.finish()
        undo.close()
    }

    private fun showItemMenu(anchor: View, plugin: Plugin) {
        PopupMenu(requireContext(), anchor).apply {
            inflate(R.menu.menu_tune_item)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_edit -> openEditor(plugin)
                    R.id.action_duplicate -> duplicatePlugin(plugin)
                    R.id.action_delete -> confirmDelete(listOf(plugin.id))
                }
                true
            }
        }.show()
    }

    private fun enterDeleteMode(selectedId: Long?) {
        adapter.setDeleteMode(true, selectedId)
        binding.fabAddPlugin.visibility = View.GONE
        selectionMode.start(adapter.getSelectedIds().size)
    }

    private fun confirmDelete(ids: List<Long>) {
        if (ids.isEmpty()) {
            Toast.makeText(requireContext(), R.string.list_none_selected, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tune_delete_title)
            .setMessage(resources.getQuantityString(R.plurals.tune_delete_message, ids.size, ids.size))
            .setPositiveButton(R.string.delete) { _, _ -> delete(ids) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete(ids: List<Long>) {
        val store = RoomListStore(db)
        mainScope.launch {
            val snapshot = withContext(Dispatchers.IO) { deleteTunesWithSnapshot(ids, store) }
            selectionMode.finish()
            loadPlugins()
            val plan = planTuneRestore(snapshot) ?: return@launch
            undo.show(binding.root, binding.fabAddPlugin, resources.getQuantityString(R.plurals.tune_deleted, plan.tunes.size, plan.tunes.size)) {
                mainScope.launch {
                    try {
                        withContext(Dispatchers.IO) { restoreTunes(plan, store) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), R.string.list_restore_failed, Toast.LENGTH_SHORT).show()
                    }
                    loadPlugins()
                }
            }
        }
    }
}
