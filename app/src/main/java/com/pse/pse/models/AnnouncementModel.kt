package com.pse.pse.models

import com.google.firebase.Timestamp

data class AnnouncementModel(
    val announcement: String = "",
    val message: String = "",
    val time: Timestamp? = Timestamp.now()
)
{
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "announcement" to announcement,
            "message" to message,
            "time" to time)
}
}
