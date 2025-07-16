package com.trustledger.aitrustledger.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.trustledger.aitrustledger.R
import com.pse.pse.models.NotificationModel
import com.pse.pse.utils.NotificationPreferenceManager
import com.pse.pse.utils.SharedPrefManager
import kotlin.random.Random

class NotificationService : FirebaseMessagingService() {
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var notificationPreferences: NotificationPreferenceManager

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = applicationContext.getSharedPreferences("MyPrefs", MODE_PRIVATE)

        notificationPreferences = NotificationPreferenceManager(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val id = sharedPreferences.getString("id", null)
        if (!id.isNullOrEmpty()) {
            firestore.collection("users").document(id).update("deviceToken", token)
        } else {
            Log.e("NotificationService", "User ID not found in SharedPreferences")
        }
    }

    private val channelId = "AssignId"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("MYTAG", "onMessageReceived: ${message.data}")
        val title =message.data["title"]
        val body = message.data["body"]
        val userId = SharedPrefManager(this).getId()
        val notification= NotificationModel(title.toString(), body.toString(),timestamp = System.currentTimeMillis())
        if (title != null && body != null && userId != null) {
           notificationPreferences.saveNotification(userId,notification)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        manager?.let {
            createNotificationChannel(it)
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(message.data["title"])
                .setContentText(message.data["body"])
                .setSmallIcon(R.drawable.app_logo)
                .setAutoCancel(false)
                .setContentIntent(null)
                .build()
            it.notify(Random.nextInt(), notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(manager: NotificationManager) {
        val channel =
            NotificationChannel(channelId, "assignwork", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "New work"
                    enableLights(true)
                }
        manager.createNotificationChannel(channel)
    }
}
