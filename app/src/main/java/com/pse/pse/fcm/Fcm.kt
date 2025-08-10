package com.pse.pse.fcm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class Fcm {
    fun sendFCMNotification(
        targetDeviceToken: String, title: String, body: String, accessToken: String
    ) {
        val url =
            "https://fcm.googleapis.com/v1/projects/philippine-stock-exchang-db/messages:send" // Replace with your project ID

        val client = OkHttpClient()

        // Build the JSON payload
        val json = JSONObject().apply {
            put("message", JSONObject().apply {
                put("token", targetDeviceToken)
                put("data", JSONObject().apply {
                    put("title", title)
                    put("body", body)
                })
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $accessToken") // Use the access token
            .post(requestBody).build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    println("Failed to send notification: ${response.code} ${response.message}")
                } else {
                    println("Notification sent successfully")
                }
            } catch (e: IOException) {
                println("Error sending notification: ${e.message}")
            }
        }
    }
}
