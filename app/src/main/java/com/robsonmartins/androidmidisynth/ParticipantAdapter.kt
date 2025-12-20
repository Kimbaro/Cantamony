package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ParticipantAdapter(
    private val items: List<Participant>,
) : RecyclerView.Adapter<ParticipantAdapter.Vh>() {

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val statusDot: View = view.findViewById(R.id.viewStatusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.statusDot.visibility = if (item.isActive) View.VISIBLE else View.INVISIBLE
    }

    override fun getItemCount(): Int = items.size
}





