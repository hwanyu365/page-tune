package com.hwanyu365.pagetune.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.pagetune.databinding.ItemPluginBinding
import com.hwanyu365.pagetune.db.Plugin

class PluginAdapter(
    private val onEditClick: (Plugin) -> Unit,
    private val onCopyClick: (Plugin) -> Unit
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
        return ViewHolder(ItemPluginBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemPluginBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Plugin) {
            binding.tvPluginName.text = item.name

            if (isDeleteMode) {
                binding.chkDelete.visibility = View.VISIBLE
                binding.btnEditPlugin.visibility = View.GONE
                binding.btnCopyPlugin.visibility = View.GONE
                binding.chkDelete.isChecked = selectedIds.contains(item.id)

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
                binding.chkDelete.visibility = View.GONE
                binding.btnEditPlugin.visibility = View.VISIBLE
                binding.btnCopyPlugin.visibility = View.VISIBLE

                itemView.setOnClickListener { onEditClick(item) }
                binding.btnEditPlugin.setOnClickListener { onEditClick(item) }
                binding.btnCopyPlugin.setOnClickListener { onCopyClick(item) }
            }
        }
    }
}
