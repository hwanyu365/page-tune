package com.hwanyu365.webplugin.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.webplugin.R
import com.hwanyu365.webplugin.db.WebPageWithPlugin
import kotlinx.android.synthetic.main.item_webpage.view.*

class WebPageAdapter(
    private val onItemClick: (WebPageWithPlugin) -> Unit,
    private val onEditClick: (WebPageWithPlugin) -> Unit
) : RecyclerView.Adapter<WebPageAdapter.ViewHolder>() {

    private var items = listOf<WebPageWithPlugin>()
    var isDeleteMode = false
        private set
    private val selectedIds = mutableSetOf<Long>()

    fun setItems(newItems: List<WebPageWithPlugin>) {
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
            items.forEach { selectedIds.add(it.webPage.id) }
        } else {
            selectedIds.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedIds(): List<Long> {
        return selectedIds.toList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_webpage, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: WebPageWithPlugin) {
            itemView.tv_title.text = item.webPage.title
            itemView.tv_url.text = item.webPage.url
            itemView.tv_plugin.text = "Plugin: ${item.plugin?.name ?: "None"}"

            if (isDeleteMode) {
                itemView.chk_delete.visibility = View.VISIBLE
                itemView.btn_edit.visibility = View.GONE
                itemView.chk_delete.isChecked = selectedIds.contains(item.webPage.id)

                itemView.setOnClickListener {
                    val id = item.webPage.id
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id)
                    } else {
                        selectedIds.add(id)
                    }
                    notifyItemChanged(adapterPosition)
                }
            } else {
                itemView.chk_delete.visibility = View.GONE
                itemView.btn_edit.visibility = View.VISIBLE

                itemView.setOnClickListener { onItemClick(item) }
                itemView.btn_edit.setOnClickListener { onEditClick(item) }
            }
        }
    }
}
