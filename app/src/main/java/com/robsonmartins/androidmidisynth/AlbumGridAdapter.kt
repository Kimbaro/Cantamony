package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.robsonmartins.androidmidisynth.dto.CantamonyAlbum

class AlbumGridAdapter(
    private val items: List<CantamonyAlbum>,
    private val selectedAlbumName: String?,
    private val onClick: (CantamonyAlbum) -> Unit,
) : RecyclerView.Adapter<AlbumGridAdapter.Vh>() {

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.root)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val subtitle: TextView = view.findViewById(R.id.tvSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album_grid, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = items[position]
        holder.title.text = item.albumName
        holder.subtitle.text = item.albumAsset["mid"]
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: " "

        val isSelected = selectedAlbumName != null && selectedAlbumName == item.albumName
        holder.root.setBackgroundResource(
            if (isSelected) R.drawable.bg_album_card_selected else R.drawable.bg_album_card
        )

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}





