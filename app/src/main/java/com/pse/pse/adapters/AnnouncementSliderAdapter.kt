package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pse.pse.R

class AnnouncementSliderAdapter(
    private val imageUrls: MutableList<String> = mutableListOf()
) : RecyclerView.Adapter<AnnouncementSliderAdapter.SliderViewHolder>() {

    inner class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.sliderImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement_slider, parent, false)
        return SliderViewHolder(view)
    }

    override fun getItemCount(): Int = imageUrls.size

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        val url = imageUrls[position]
        Glide.with(holder.itemView.context)
            .load(url)
            .centerCrop()
            .placeholder(R.drawable.glassy_action_card_bg)
            .error(R.drawable.error_image)
            .into(holder.imageView)
    }

    /** 🔧 Allow HomeFragment to refresh images without replacing the adapter */
    fun updateData(newUrls: List<String>) {
        imageUrls.clear()
        imageUrls.addAll(newUrls)
        notifyDataSetChanged()
    }
}
