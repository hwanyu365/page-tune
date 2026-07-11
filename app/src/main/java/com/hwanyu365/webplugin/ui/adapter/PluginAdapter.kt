package com.hwanyu365.webplugin.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.webplugin.R
import com.hwanyu365.webplugin.db.Plugin
import kotlinx.android.synthetic.main.item_plugin.view.*

class PluginAdapter(
    private val onEditClick: (Plugin) -> Unit
) : RecyclerView.Adapter<PluginAdapter.ViewHolder>() {

    private var items = listOf<Plugin>()
    var isDeleteMode = false
        private set
    private val selectedIds = mutableSetOf<Long>()

    fun setItems(newItems: List<Plugin>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setDeleteMode(enabled: Boolean) {
        isDeleteMode = enabled
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun selectAll(select: Boolean) {
        if (select) {
            items.forEach { selectedIds.add(it.id) }
        } else {
            selectedIds.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<Long> {
        return selectedIds.toList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_plugin, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: Plugin) {
            itemView.tv_plugin_name.text = item.name

            if (isDeleteMode) {
                itemView.chk_delete.visibility = View.VISIBLE
                itemView.btn_edit_plugin.visibility = View.GONE
                itemView.chk_delete.isChecked = selectedIds.contains(item.id)

                itemView.setOnClickListener {
                    val id = item.id
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id)
                    } else {
                        selectedIds.add(id)
                    }
                    notifyItemChanged(adapterPosition)
                }
            } else {
                itemView.chk_delete.visibility = View.GONE
                itemView.btn_edit_plugin.visibility = View.VISIBLE

                itemView.setOnClickListener { onEditClick(item) }
                itemView.btn_edit_plugin.setOnClickListener { onEditClick(item) }
            }
        }
    }
}
