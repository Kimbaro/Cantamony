package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FeedAdapter(
    private val onRoomClicked: (roomId: String, title: String) -> Unit,
) : RecyclerView.Adapter<FeedAdapter.Vh>() {

    private var items: List<FeedCard> = emptyList()

    fun submitList(newItems: List<FeedCard>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_secondary_article, parent, false)
        return Vh(view, onRoomClicked)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class Vh(
        view: View,
        private val onRoomClicked: (roomId: String, title: String) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvCategory: TextView = view.findViewById(R.id.tvCategory)

        fun bind(item: FeedCard) {
            when (item) {
                is RoomEntryCard -> {
                    tvTitle.text = item.title
                    tvCategory.text = item.category ?: ""
                    itemView.setOnClickListener { onRoomClicked(item.roomId, item.title) }
                }
            }
        }
    }
}


