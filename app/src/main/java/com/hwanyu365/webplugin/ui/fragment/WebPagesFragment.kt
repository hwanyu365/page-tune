package com.hwanyu365.webplugin.ui.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hwanyu365.webplugin.R
import com.hwanyu365.webplugin.WebBrowserActivity
import com.hwanyu365.webplugin.db.AppDatabase
import com.hwanyu365.webplugin.db.Plugin
import com.hwanyu365.webplugin.db.WebPage
import com.hwanyu365.webplugin.db.WebPageWithPlugin
import com.hwanyu365.webplugin.ui.adapter.WebPageAdapter
import kotlinx.android.synthetic.main.dialog_webpage_edit.view.*
import kotlinx.android.synthetic.main.fragment_webpages.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebPagesFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: WebPageAdapter
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
        return inflater.inflate(R.layout.fragment_webpages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WebPageAdapter(
            onItemClick = { webpageWithPlugin ->
                // Open WebBrowserActivity
                val intent = Intent(requireContext(), WebBrowserActivity::class.java).apply {
                    putExtra("webpage_id", webpageWithPlugin.webPage.id)
                }
                startActivity(intent)
            },
            onEditClick = { webpageWithPlugin ->
                showWebPageEditDialog(webpageWithPlugin.webPage)
            }
        )

        rv_webpages.layoutManager = LinearLayoutManager(requireContext())
        rv_webpages.adapter = adapter

        fab_add_webpage.setOnClickListener {
            showWebPageEditDialog(null)
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
                .setTitle("Delete Pages")
                .setMessage("Are you sure you want to delete ${selectedIds.size} pages?")
                .setPositiveButton("Delete") { _, _ ->
                    mainScope.launch {
                        withContext(Dispatchers.IO) {
                            db.webPageDao().deleteWebPagesByIds(selectedIds)
                        }
                        exitDeleteMode()
                        loadWebPages()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        chk_select_all.setOnCheckedChangeListener { _, isChecked ->
            adapter.selectAll(isChecked)
        }

        loadWebPages()
    }

    override fun onResume() {
        super.onResume()
        loadWebPages()
    }

    private fun loadWebPages() {
        mainScope.launch {
            val pages = withContext(Dispatchers.IO) {
                db.webPageDao().getAllWebPagesWithPlugin()
            }
            adapter.setItems(pages)
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
        fab_add_webpage.visibility = View.GONE
        chk_select_all.isChecked = false
    }

    private fun exitDeleteMode() {
        adapter.setDeleteMode(false)
        layout_header.visibility = View.GONE
        layout_delete_actions.visibility = View.GONE
        fab_add_webpage.visibility = View.VISIBLE
    }

    private fun showWebPageEditDialog(webPage: WebPage?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_webpage_edit, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        mainScope.launch {
            // Load plugins for spinner
            val plugins = withContext(Dispatchers.IO) {
                db.pluginDao().getAllPlugins()
            }

            // Spinner setup
            val spinnerItems = mutableListOf("None")
            plugins.forEach { spinnerItems.add(it.name) }
            val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerItems)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dialogView.sp_plugins.adapter = spinnerAdapter

            // Populate data if editing
            if (webPage != null) {
                dialogView.tv_dialog_title.text = "Edit Web Page"
                dialogView.et_title.setText(webPage.title)
                dialogView.et_url.setText(webPage.url)
                
                val pluginIndex = plugins.indexOfFirst { it.id == webPage.pluginId }
                if (pluginIndex != -1) {
                    dialogView.sp_plugins.setSelection(pluginIndex + 1) // +1 because of "None"
                } else {
                    dialogView.sp_plugins.setSelection(0)
                }
            } else {
                dialogView.tv_dialog_title.text = "Add Web Page"
            }

            dialogView.btn_dialog_cancel.setOnClickListener {
                dialog.dismiss()
            }

            dialogView.btn_dialog_save.setOnClickListener {
                val title = dialogView.et_title.text.toString().trim()
                var url = dialogView.et_url.text.toString().trim()
                
                if (title.isEmpty() || url.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Add protocol if missing
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }

                val selectedPosition = dialogView.sp_plugins.selectedItemPosition
                val selectedPluginId = if (selectedPosition > 0) plugins[selectedPosition - 1].id else null

                mainScope.launch {
                    withContext(Dispatchers.IO) {
                        if (webPage == null) {
                            db.webPageDao().insertWebPage(WebPage(title = title, url = url, pluginId = selectedPluginId))
                        } else {
                            db.webPageDao().updateWebPage(webPage.copy(title = title, url = url, pluginId = selectedPluginId))
                        }
                    }
                    dialog.dismiss()
                    loadWebPages()
                }
            }

            dialog.show()
        }
    }
}
