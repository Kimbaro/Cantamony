package com.robsonmartins.androidmidisynth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(val author: String, val body: String)

class ChatAdapter(
    private val items: List<ChatMessage>,
) : RecyclerView.Adapter<ChatAdapter.Vh>() {

    class Vh(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: View = view.findViewById(R.id.bubble)
        val tvAuthor: TextView = view.findViewById(R.id.tvAuthor)
        val tvBody: TextView = view.findViewById(R.id.tvBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return Vh(view)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val item = items[position]
        val isMe = item.author == "나"

        holder.tvAuthor.text = item.author
        holder.tvBody.text = item.body

        // 말풍선 좌/우 정렬
        val lp = holder.bubble.layoutParams as? FrameLayout.LayoutParams
        if (lp != null) {
            lp.gravity = if (isMe) (android.view.Gravity.END) else (android.view.Gravity.START)
            holder.bubble.layoutParams = lp
        }

        // 말풍선 스타일(배경/텍스트 컬러) + 내 메시지는 author 숨김
        if (isMe) {
            holder.bubble.setBackgroundResource(R.drawable.bg_chat_bubble_out)
            holder.tvAuthor.visibility = View.GONE
            holder.tvBody.setTextColor(0xFFFFFFFF.toInt())
        } else {
            holder.bubble.setBackgroundResource(R.drawable.bg_chat_bubble_in)
            holder.tvAuthor.visibility = View.VISIBLE
            holder.tvBody.setTextColor(0xFF374151.toInt())
        }
    }

    override fun getItemCount(): Int = items.size
}


