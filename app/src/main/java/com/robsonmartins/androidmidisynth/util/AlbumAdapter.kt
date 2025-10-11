package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.robsonmartins.androidmidisynth.dto.MidiAlbum

class AlbumAdapter(
    private val albums: List<MidiAlbum>,
    private val onAlbumClick: (MidiAlbum) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtAlbumName: TextView = view.findViewById(R.id.txtAlbumName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        holder.txtAlbumName.text = album.albumName

        holder.itemView.setOnClickListener {
            onAlbumClick(album)
        }
    }

    override fun getItemCount(): Int = albums.size
}
