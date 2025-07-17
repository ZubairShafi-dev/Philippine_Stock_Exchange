package com.pse.pse.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pse.pse.R
import com.pse.pse.models.UserListModel
import java.util.Locale

class ReferralDataAdapter(
    private val users: List<UserListModel>
) : RecyclerView.Adapter<ReferralDataAdapter.ReferralVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferralVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_referral_user, parent, false)
        return ReferralVH(view)
    }

    override fun onBindViewHolder(holder: ReferralVH, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class ReferralVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.userName)
        private val userIdText: TextView = itemView.findViewById(R.id.userId)
        private val statusText: TextView = itemView.findViewById(R.id.userStatus)

        fun bind(user: UserListModel) {
            nameText.text = "${user.name} ${user.lName}"
            userIdText.text = user.uid
            statusText.text = user.status.capitalize(Locale.ROOT)

            // Color status
            statusText.setTextColor(
                itemView.context.getColor(
                    if (user.status.equals("active", ignoreCase = true))
                        R.color.green
                    else
                        R.color.red
                )
            )
        }
    }
}
