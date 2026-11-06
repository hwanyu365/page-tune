package com.hwanyu365.pagetune.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hwanyu365.pagetune.util.Selection
import com.hwanyu365.pagetune.R
import com.hwanyu365.pagetune.databinding.ItemWebpageBinding
import com.hwanyu365.pagetune.db.WebPageWithPlugin

class WebPageAdapter(
    private val onItemClick: (WebPageWithPlugin) -> Unit,
    private val onEditClick: (WebPageWithPlugin) -> Unit
) : RecyclerView.Adapter<WebPageAdapter.ViewHolder>() {

    private var items = listOf<WebPageWithPlugin>()
    var isDeleteMode = false
        private set
    private val selection = Selection()

    fun setItems(newItems: List<WebPageWithPlugin>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setDeleteMode(enabled: Boolean) {
        isDeleteMode = enabled
        selection.clear()
        notifyDataSetChanged()
    }

    fun selectAll(select: Boolean) {
        if (select) selection.selectAll(items.map { it.webPage.id }) else selection.clear()
        notifyDataSetChanged()
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
            binding.tvTitle.text = item.webPage.title
            binding.tvUrl.text = item.webPage.url
            binding.tvPlugin.text = item.plugin?.name?.let { binding.root.context.getString(R.string.webpage_item_tune, it) }
                ?: binding.root.context.getString(R.string.webpage_item_tune_none)

            if (isDeleteMode) {
                binding.chkDelete.visibility = View.VISIBLE
                binding.btnEdit.visibility = View.GONE
                binding.chkDelete.isChecked = selection.contains(item.webPage.id)

                itemView.setOnClickListener {
                    selection.toggle(item.webPage.id)
                    notifyItemChanged(adapterPosition)
                }
                binding.chkDelete.setOnClickListener { selection.set(item.webPage.id, binding.chkDelete.isChecked) }
            } else {
                binding.chkDelete.visibility = View.GONE
                binding.btnEdit.visibility = View.VISIBLE

                itemView.setOnClickListener { onItemClick(item) }
                binding.btnEdit.setOnClickListener { onEditClick(item) }
            }
        }
    }
}
