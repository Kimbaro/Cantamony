package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ActivityMenuAdapter(
    private val items: List<String>,
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<ActivityMenuAdapter.Vh>() {

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_menu, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val title = items[position]
        holder.tv.text = title
        holder.itemView.setOnClickListener { onClick(title) }
    }

    override fun getItemCount(): Int = items.size
}





