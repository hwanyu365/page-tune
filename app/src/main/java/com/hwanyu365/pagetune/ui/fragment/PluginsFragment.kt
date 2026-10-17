package com.hwanyu365.pagetune.ui.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hwanyu365.pagetune.PluginEditActivity
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.databinding.FragmentPluginsBinding
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.ui.adapter.PluginAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginsFragment : Fragment() {

    private lateinit var binding: FragmentPluginsBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: PluginAdapter
    private val mainScope = CoroutineScope(Dispatchers.Main)

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

        adapter = PluginAdapter(
            onEditClick = { plugin ->
                val intent = Intent(requireContext(), PluginEditActivity::class.java).apply {
                    putExtra("plugin_id", plugin.id)
                }
                startActivity(intent)
            },
            onCopyClick = { plugin ->
                duplicatePlugin(plugin)
            }
        )

        binding.rvPlugins.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlugins.adapter = adapter

        binding.fabAddPlugin.setOnClickListener {
            val intent = Intent(requireContext(), PluginEditActivity::class.java)
            startActivity(intent)
        }

        binding.btnCancelDelete.setOnClickListener {
            exitDeleteMode()
        }

        binding.btnConfirmDelete.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), "No items selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Tunes")
                .setMessage("Are you sure you want to delete " + selectedIds.size + " Tunes?")
                .setPositiveButton("Delete") { _, _ ->
                    mainScope.launch {
                        withContext(Dispatchers.IO) {
                            db.pluginDao().deletePluginsByIds(selectedIds)
                        }
                        exitDeleteMode()
                        loadPlugins()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.chkSelectAll.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

        loadPlugins()
    }

    override fun onResume() {
        super.onResume()
        loadPlugins()
    }

    /** Tune 을 액션까지 통째로 복제한다. 비슷한 사이트용 Tune 을 처음부터 다시 만들지 않기 위한 기능이다. */
    private fun duplicatePlugin(plugin: Plugin) {
        mainScope.launch {
            withContext(Dispatchers.IO) {
                val source = db.pluginDao().getPluginWithActions(plugin.id) ?: return@withContext
                // id 를 0 으로 비워야 Room 이 새 행으로 넣는다. 그대로 두면 REPLACE 정책 때문에 원본을 덮어쓴다.
                val newId = db.pluginDao().insertPlugin(
                    source.plugin.copy(id = 0, name = source.plugin.name + " (copy)")
                )
                db.pluginDao().insertActions(source.actions.map { it.copy(id = 0, pluginId = newId) })
            }
            loadPlugins()
            Toast.makeText(requireContext(), "Tune duplicated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPlugins() {
        mainScope.launch {
            val plugins = withContext(Dispatchers.IO) {
                db.pluginDao().getAllPlugins()
            }
            adapter.setItems(plugins)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete_mode) {
            enterDeleteMode()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun enterDeleteMode() {
        adapter.setDeleteMode(true)
        binding.layoutHeader.visibility = View.VISIBLE
        binding.layoutDeleteActions.visibility = View.VISIBLE
        binding.fabAddPlugin.visibility = View.GONE
        binding.chkSelectAll.isChecked = false
    }

    private fun exitDeleteMode() {
        adapter.setDeleteMode(false)
        binding.layoutHeader.visibility = View.GONE
        binding.layoutDeleteActions.visibility = View.GONE
        binding.fabAddPlugin.visibility = View.VISIBLE
    }
}
