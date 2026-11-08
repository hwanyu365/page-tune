package com.hwanyu365.pagetune.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.pagetune.databinding.ItemPluginBinding
import com.hwanyu365.pagetune.db.Plugin
import com.hwanyu365.pagetune.ui.bindAvatar
import com.hwanyu365.pagetune.util.DomainAvatar
import com.hwanyu365.pagetune.util.Selection

class PluginAdapter(
    private val onEditClick: (Plugin) -> Unit,
    private val onMoreClick: (View, Plugin) -> Unit,
    private val onLongPress: (Plugin) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
) : RecyclerView.Adapter<PluginAdapter.ViewHolder>() {

    private var items = listOf<Plugin>()
    var isDeleteMode = false
        private set
    private val selection = Selection()

    fun setItems(newItems: List<Plugin>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setDeleteMode(enabled: Boolean, selectedId: Long? = null) {
        isDeleteMode = enabled
        selection.clear()
        selectedId?.let { selection.set(it, true) }
        notifyDataSetChanged()
    }

    fun selectAll(select: Boolean) {
        if (select) selection.selectAll(items.map { it.id }) else selection.clear()
        notifyDataSetChanged()
        onSelectionChanged(selection.ids().size)
    }

    fun getSelectedIds(): List<Long> = selection.ids()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemPluginBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemPluginBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Plugin) {
            val avatar = DomainAvatar.of(item.testUrl, item.name)
            bindAvatar(binding.avatar, binding.tvAvatar, binding.ivAvatarWeb, avatar)
            binding.tvPluginName.text = item.name
            binding.tvHost.text = avatar.host
            binding.tvHost.visibility = if (avatar.host.isEmpty()) View.GONE else View.VISIBLE

            binding.btnMore.visibility = if (isDeleteMode) View.GONE else View.VISIBLE
            binding.chkDelete.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
            if (isDeleteMode) {
                binding.chkDelete.isChecked = selection.contains(item.id)
                itemView.setOnClickListener {
                    selection.toggle(item.id)
                    notifyItemChanged(bindingAdapterPosition)
                    onSelectionChanged(selection.ids().size)
                }
                itemView.setOnLongClickListener(null)
                binding.chkDelete.setOnClickListener {
                    selection.set(item.id, binding.chkDelete.isChecked)
                    onSelectionChanged(selection.ids().size)
                }
            } else {
                itemView.setOnClickListener { onEditClick(item) }
                itemView.setOnLongClickListener { onLongPress(item); true }
                binding.btnMore.setOnClickListener { onMoreClick(it, item) }
            }
        }
    }
}
