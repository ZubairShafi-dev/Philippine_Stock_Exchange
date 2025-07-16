package com.trustledger.aitrustledger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trustledger.aitrustledger.R
import com.pse.pse.models.NotificationModel
import java.util.Date

class NotificationAdapter(
    private val notifications: List<NotificationModel>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    // ViewHolder to hold the view components for each notification item
    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvBody: TextView = itemView.findViewById(R.id.message)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(notification: NotificationModel) {
            tvTitle.text = notification.title
            tvBody.text = notification.body


            val formattedTime = notification.timestamp.let {
                val date = Date(it)  // Convert the long timestamp to a Date object
                java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                    .format(date)
            } ?: "—"


            tvTime.text = formattedTime
        }
    }

    // Create new views (called by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_announcment, parent, false)
        return NotificationViewHolder(view)
    }

    // Replace the contents of a view (called by the layout manager)
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
       holder.bind(notification)

    }

    // Return the size of the data set (invoked by layout manager)
    override fun getItemCount(): Int {
        return notifications.size
    }
}
