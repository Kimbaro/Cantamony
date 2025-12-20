package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserSelectAdapter(
    private val items: List<UserProfile>,
    selectedIndex: Int,
    private val onSelected: (Int) -> Unit,
) : RecyclerView.Adapter<UserSelectAdapter.Vh>() {

    private var selectedIndexInternal: Int = selectedIndex

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val avatar: View = view.findViewById(R.id.avatarCircle)
    }

    fun setSelectedIndex(index: Int) {
        val old = selectedIndexInternal
        selectedIndexInternal = index
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
        if (index != RecyclerView.NO_POSITION) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_select, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        val isSelected = position == selectedIndexInternal
        holder.avatar.setBackgroundResource(
            if (isSelected) R.drawable.bg_avatar_circle_selected else R.drawable.bg_avatar_circle
        )

        holder.itemView.setOnClickListener { onSelected(position) }
    }

    override fun getItemCount(): Int = items.size
}





