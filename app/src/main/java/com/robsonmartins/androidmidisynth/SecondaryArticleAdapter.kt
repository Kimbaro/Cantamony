package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class SecondaryArticle(
    val title: String,
    val category: String,
)

class SecondaryArticleAdapter(
    private val items: List<SecondaryArticle>,
) : RecyclerView.Adapter<SecondaryArticleAdapter.Vh>() {

    inner class Vh(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvCategory: TextView = view.findViewById(R.id.tvCategory)

        fun bind(item: SecondaryArticle) {
            tvTitle.text = item.title
            tvCategory.text = item.category
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_secondary_article, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}





