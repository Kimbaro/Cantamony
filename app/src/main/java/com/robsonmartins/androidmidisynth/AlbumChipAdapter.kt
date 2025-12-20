package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum

class AlbumChipAdapter(
    private val items: List<CantamonyAlbum>,
    private val selectedAlbumName: String?,
    private val onClick: (CantamonyAlbum) -> Unit,
) : RecyclerView.Adapter<AlbumChipAdapter.Vh>() {

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvAlbumChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album_chip, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = items[position]
        holder.tv.text = item.albumName
        val isSelected = selectedAlbumName != null && selectedAlbumName == item.albumName
        holder.tv.setBackgroundResource(
            if (isSelected) R.drawable.bg_pill_menu_selected else R.drawable.bg_pill_menu
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}


