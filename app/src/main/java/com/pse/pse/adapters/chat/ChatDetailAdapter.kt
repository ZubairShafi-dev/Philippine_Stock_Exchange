package com.pse.pse.adapters.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.models.chat.Message
import java.text.DateFormat
import java.util.Date


class ChatDetailAdapter(
    private var messages: List<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<ChatDetailAdapter.ChatDetailViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatDetailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ChatDetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatDetailViewHolder, position: Int) {
        val message = messages[position]

        holder.messageText.text = message.message
        holder.messageTime.text = DateFormat.getTimeInstance().format(
            message.createdAt?.toDate()?.let { Date(it.time) }
        )
        holder.messageDate.text = message.createdAt?.toDate()?.let { getFormattedDate(it.time) }

        // Set the background based on the sender ID
        when (message.sender) {
            "1" -> {
                holder.messageText.setBackgroundResource(R.drawable.bubble_right)
                holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_RTL
            }

            "2" -> {
                holder.messageText.setBackgroundResource(R.drawable.bubble_left)
                holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_LTR
            }

            else -> {
                // fallback
                holder.messageText.setBackgroundResource(R.drawable.bubble_left)
                holder.itemView.layoutDirection = View.LAYOUT_DIRECTION_LTR
                Log.e("ChatDetailAdapter", "❌ Unknown or null sender ID: '${message.sender}'")
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(messages: List<Message?>) {
        this.messages = messages as List<Message>
        notifyDataSetChanged()
    }

    class ChatDetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        val messageDate: TextView = itemView.findViewById(R.id.messageDate)
    }

    private fun getFormattedDate(timestamp: Long): String {
        return DateFormat.getDateInstance().format(Date(timestamp))
    }
}
