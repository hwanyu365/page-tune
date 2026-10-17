package com.hwanyu365.pagetune.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.pagetune.databinding.ItemWebpageBinding
import com.hwanyu365.pagetune.db.WebPageWithPlugin

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
        return ViewHolder(ItemWebpageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemWebpageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WebPageWithPlugin) {
            binding.tvTitle.text = item.webPage.title
            binding.tvUrl.text = item.webPage.url
            binding.tvPlugin.text = "Tune: ${item.plugin?.name ?: "None"}"

            if (isDeleteMode) {
                binding.chkDelete.visibility = View.VISIBLE
                binding.btnEdit.visibility = View.GONE
                binding.chkDelete.isChecked = selectedIds.contains(item.webPage.id)

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
                binding.chkDelete.visibility = View.GONE
                binding.btnEdit.visibility = View.VISIBLE

                itemView.setOnClickListener { onItemClick(item) }
                binding.btnEdit.setOnClickListener { onEditClick(item) }
            }
        }
    }
}
