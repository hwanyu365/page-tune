package com.hwanyu365.pagetune.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.WebBrowserActivity
import com.hwanyu365.pagetune.databinding.DialogWebpageEditBinding
import com.hwanyu365.pagetune.databinding.FragmentWebpagesBinding
import com.hwanyu365.pagetune.db.AppDatabase
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.db.WebPage
import com.hwanyu365.pagetune.db.WebPageWithPlugin
import com.hwanyu365.pagetune.ui.adapter.WebPageAdapter
import com.hwanyu365.pagetune.util.UrlPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebPagesFragment : Fragment(), TabPage {

    private lateinit var binding: FragmentWebpagesBinding
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
    ): View {
        binding = FragmentWebpagesBinding.inflate(inflater, container, false)
        return binding.root
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

        binding.rvWebpages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWebpages.adapter = adapter

        binding.fabAddWebpage.setOnClickListener {
            showWebPageEditDialog(null)
        }

        binding.btnEmptyAction.setOnClickListener {
            showWebPageEditDialog(null)
        }

        binding.btnCancelDelete.setOnClickListener {
            exitDeleteMode()
        }

        binding.btnConfirmDelete.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), R.string.list_none_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.webpage_delete_title)
                .setMessage(resources.getQuantityString(R.plurals.webpage_delete_message, selectedIds.size, selectedIds.size))
                .setPositiveButton(R.string.delete) { _, _ ->
                    mainScope.launch {
                        withContext(Dispatchers.IO) {
                            db.webPageDao().deleteWebPagesByIds(selectedIds)
                        }
                        exitDeleteMode()
                        loadWebPages()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.chkSelectAll.setOnCheckedChangeListener { _, isChecked ->
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
            binding.layoutEmpty.visibility = if (pages.isEmpty()) View.VISIBLE else View.GONE
            binding.rvWebpages.visibility = if (pages.isEmpty()) View.GONE else View.VISIBLE
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

    override fun onTabLeft() {
        if (this::adapter.isInitialized && adapter.isDeleteMode) exitDeleteMode()
    }

    private fun enterDeleteMode() {
        adapter.setDeleteMode(true)
        binding.layoutHeader.visibility = View.VISIBLE
        binding.layoutDeleteActions.visibility = View.VISIBLE
        binding.fabAddWebpage.visibility = View.GONE
        binding.chkSelectAll.isChecked = false
    }

    private fun exitDeleteMode() {
        adapter.setDeleteMode(false)
        binding.layoutHeader.visibility = View.GONE
        binding.layoutDeleteActions.visibility = View.GONE
        binding.fabAddWebpage.visibility = View.VISIBLE
    }

    private fun showWebPageEditDialog(webPage: WebPage?) {
        val dialogBinding = DialogWebpageEditBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        mainScope.launch {
            // Load plugins for spinner
            val plugins = withContext(Dispatchers.IO) {
                db.pluginDao().getAllPlugins()
            }

            // Spinner setup
            val spinnerItems = mutableListOf(getString(R.string.webpage_tune_none))
            plugins.forEach { spinnerItems.add(it.name) }
            val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerItems)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dialogBinding.spPlugins.adapter = spinnerAdapter

            // Populate data if editing
            if (webPage != null) {
                dialogBinding.tvDialogTitle.text = getString(R.string.webpage_dialog_edit_title)
                dialogBinding.etTitle.setText(webPage.title)
                dialogBinding.etUrl.setText(webPage.url)
                
                val pluginIndex = plugins.indexOfFirst { it.id == webPage.pluginId }
                if (pluginIndex != -1) {
                    dialogBinding.spPlugins.setSelection(pluginIndex + 1) // +1 because of "None"
                } else {
                    dialogBinding.spPlugins.setSelection(0)
                }
            } else {
                dialogBinding.tvDialogTitle.text = getString(R.string.webpage_dialog_add_title)
            }

            dialogBinding.btnDialogCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialogBinding.btnDialogSave.setOnClickListener {
                val title = dialogBinding.etTitle.text.toString().trim()
                val input = dialogBinding.etUrl.text.toString().trim()

                if (title.isEmpty() || input.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.webpage_error_fill_all, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val url = UrlPolicy.normalizeInput(input)
                dialogBinding.tilUrl.error = if (url == null) getString(R.string.url_input_error) else null
                if (url == null) return@setOnClickListener

                val selectedPosition = dialogBinding.spPlugins.selectedItemPosition
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
