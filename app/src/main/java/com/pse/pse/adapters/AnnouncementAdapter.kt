package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.models.AnnouncementModel

class AnnouncementAdapter(
    private val item: List<AnnouncementModel>
) :
    RecyclerView.Adapter<AnnouncementAdapter.AnnouncementViewHolder>() {
    inner class AnnouncementViewHolder(itemView: android.view.View) :
        RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvBody: TextView = itemView.findViewById(R.id.message)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(item: AnnouncementModel?) {
            tvTitle.text = item?.announcement
            tvBody.text = item?.message

            val formattedTime = item?.time?.toDate()?.let {
                java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())
                    .format(it)
            } ?: "—"

            tvTime.text = formattedTime
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AnnouncementAdapter.AnnouncementViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcment, parent, false)
        return AnnouncementViewHolder(v)
    }

    override fun onBindViewHolder(
        holder: AnnouncementAdapter.AnnouncementViewHolder,
        position: Int
    ) {
        holder.bind(item[position])

        // Hide divider if this is the last item
        val divider = holder.itemView.findViewById<android.view.View>(R.id.divider)
        divider.visibility =
            if (position == item.size - 1) android.view.View.GONE else android.view.View.VISIBLE
    }

    override fun getItemCount(): Int = item.size
}


