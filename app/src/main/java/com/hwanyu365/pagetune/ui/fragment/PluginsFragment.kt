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
import com.hwanyu365.pagetune.ui.adapter.PluginAdapter
import kotlinx.android.synthetic.main.fragment_plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginsFragment : Fragment() {

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
    ): View? {
        return inflater.inflate(R.layout.fragment_plugins, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PluginAdapter(
            onEditClick = { plugin ->
                val intent = Intent(requireContext(), PluginEditActivity::class.java).apply {
                    putExtra("plugin_id", plugin.id)
                }
                startActivity(intent)
            }
        )

        rv_plugins.layoutManager = LinearLayoutManager(requireContext())
        rv_plugins.adapter = adapter

        fab_add_plugin.setOnClickListener {
            val intent = Intent(requireContext(), PluginEditActivity::class.java)
            startActivity(intent)
        }

        btn_cancel_delete.setOnClickListener {
            exitDeleteMode()
        }

        btn_confirm_delete.setOnClickListener {
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

        chk_select_all.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

        loadPlugins()
    }

    override fun onResume() {
        super.onResume()
        loadPlugins()
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
        layout_header.visibility = View.VISIBLE
        layout_delete_actions.visibility = View.VISIBLE
        fab_add_plugin.visibility = View.GONE
        chk_select_all.isChecked = false
    }

    private fun exitDeleteMode() {
        adapter.setDeleteMode(false)
        layout_header.visibility = View.GONE
        layout_delete_actions.visibility = View.GONE
        fab_add_plugin.visibility = View.VISIBLE
    }
}
