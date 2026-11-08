package com.hwanyu365.pagetune.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.databinding.ItemWebpageBinding
import com.hwanyu365.pagetune.db.WebPageWithPlugin
import com.hwanyu365.pagetune.ui.bindAvatar
import com.hwanyu365.pagetune.util.DomainAvatar
import com.hwanyu365.pagetune.util.Selection

class WebPageAdapter(
    private val onItemClick: (WebPageWithPlugin) -> Unit,
    private val onMoreClick: (View, WebPageWithPlugin) -> Unit,
    private val onLongPress: (WebPageWithPlugin) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
) : RecyclerView.Adapter<WebPageAdapter.ViewHolder>() {

    private var items = listOf<WebPageWithPlugin>()
    var isDeleteMode = false
        private set
    private val selection = Selection()

    fun setItems(newItems: List<WebPageWithPlugin>) {
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
        if (select) selection.selectAll(items.map { it.webPage.id }) else selection.clear()
        notifyDataSetChanged()
        onSelectionChanged(selection.ids().size)
    }

    fun getSelectedIds(): List<Long> = selection.ids()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemWebpageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemWebpageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WebPageWithPlugin) {
            val context = binding.root.context
            val avatar = DomainAvatar.of(item.webPage.url, item.webPage.title)
            bindAvatar(binding.avatar, binding.tvAvatar, binding.ivAvatarWeb, avatar)
            binding.tvTitle.text = item.webPage.title
            binding.tvHost.text = avatar.host
            binding.tvHost.visibility = if (avatar.host.isEmpty()) View.GONE else View.VISIBLE
            binding.tvPlugin.text = item.plugin?.name ?: context.getString(R.string.webpage_item_tune_none)
            binding.tvPlugin.setTextColor(ContextCompat.getColor(context, if (item.plugin != null) R.color.brandOnSurface else R.color.onSurfaceVariant))

            binding.btnMore.visibility = if (isDeleteMode) View.GONE else View.VISIBLE
            binding.chkDelete.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
            if (isDeleteMode) {
                binding.chkDelete.isChecked = selection.contains(item.webPage.id)
                itemView.setOnClickListener {
                    selection.toggle(item.webPage.id)
                    notifyItemChanged(bindingAdapterPosition)
                    onSelectionChanged(selection.ids().size)
                }
                itemView.setOnLongClickListener(null)
                binding.chkDelete.setOnClickListener {
                    selection.set(item.webPage.id, binding.chkDelete.isChecked)
                    onSelectionChanged(selection.ids().size)
                }
            } else {
                itemView.setOnClickListener { onItemClick(item) }
                itemView.setOnLongClickListener { onLongPress(item); true }
                binding.btnMore.setOnClickListener { onMoreClick(it, item) }
            }
        }
    }
}
