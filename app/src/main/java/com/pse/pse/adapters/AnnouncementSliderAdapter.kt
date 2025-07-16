package com.trustledger.aitrustledger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.trustledger.aitrustledger.R

/**
 * A simple ViewPager2 adapter that takes a list of image URLs
 * and loads them (via Glide) into an ImageView for each page.
 */
class AnnouncementSliderAdapter(
    private val imageUrls: List<String>
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
            .placeholder(R.drawable.glassy_action_card_bg)    // optional placeholder
            .error(R.drawable.error_image)                // optional error image
            .into(holder.imageView)
    }
}
