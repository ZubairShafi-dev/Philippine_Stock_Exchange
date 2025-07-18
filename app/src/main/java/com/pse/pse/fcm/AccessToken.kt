package com.pse.pse.fcm

import android.os.AsyncTask
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.common.collect.Lists
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class AccessToken {
    companion object {
        private const val FIREBASE_MESSAGING_SCOPE =
            "https://www.googleapis.com/auth/firebase.messaging"

        fun getAccessTokenAsync(callback: AccessTokenCallback) {
            AccessTokenTask(callback).execute()
        }
    }

    private class AccessTokenTask(private val callback: AccessTokenCallback) :
        AsyncTask<Void, Void, String?>() {

        override fun doInBackground(vararg params: Void?): String? {
            return try {
                val jsonString = """
  {
  "type": "service_account",
  "project_id": "philippine-stock-exchang-296cd",
  "private_key_id": "bc9685122ebe71439e0e7170653da07a49274b37",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQC5jPRgo1wgy2ca\nexyLg0vSHkwdCvzqUnC628QAh1X+LXxD8rdUt/IscqBH9PZW2fpDYncYRkzwTMjl\ngr2isMbRaUt9EPYmOOxSdSr4TBzqt364ECvIKN4inK2PcXGc76cW+/09Ab2SpYiD\nKgdwWHAS1uD2ta/SoxejcLfQSSCcZASu03JxyF2iiNKHOLF6dw/gF0nWe0vLTGzi\nIGKmA7WT57udLPoeVPyyYCixVqu1QdL1oVqk0BN/66Au9sjGkrQlAW4u763Wafdv\nsQkWTM9sZ4KcNqWJiATlLZpPGbP8qgF2EG+wPkj9/oRJkxfaj1gzb0kIRGqYtQBD\nuSbVwiT1AgMBAAECggEAD4t+C7yT4tF3nVHUFeIVj5BnOwWpI65tcFIqDQtJioZg\nA0dJ0aipeszNncmpe6Q+YbfVl2yIVJdyOzwlTAR4W6HxJ5Ssso4RSmUziOLDh2yB\nCsWcDWRTG945ciuEY6v2OSg6UoqbSbZhhe+W+PQHy6RB65lUFhcAy3oNhD8kIsXf\nu33KfB0iMnII/D+/a8DEbE6e4whlm9QR6kx3TrH7uIXYyUmiQDBRKth3SgJu+/OR\nwUOgoNA1fZoF62UX3P6pen/0dxdqFCr4JEikEUrLWU6NH8NqeT94ZLpdGg2Hv+4P\nUmH01MVuZGh1s48P8/9lHRdaNIV5c6XFseoteLrGIQKBgQDqP56e9wJrafWtfigt\n1Mvn+eDrQUw3DMDYVtcXrR+ILUxyPiig4ea8u4Vw4npCgAEg/EQAoksrN8UVO1+g\ngGSV4gVI5nE6a87uene4zhV2UIjBpZ6FzgOwTUd9QuEn/L0YEDX8P6qHJwKREl/m\n2KF5R+7gHOpvTPAzspnnfqwLZQKBgQDKx7mGV2DqngWpTNMTmtey40vU6ZBQHKy+\nR8z7p2YaxgAN5LiCdjJ1b8vEoZ+WcMqm4oX9vWFjMmAl8Kz9U9cjf+Gv4HQjeOrk\nEKB1DMBOK5xud5tCmeD+NZ9jyIOH2hLyYES/i8tlzrz/94sOvBE1RZugJ1nL3ySf\ncpCNyvDCUQKBgA+Ve/AHbtnGitmn8vRZm9crAJOmHHPtHUdHP7gLhHWCzfsrt3g8\njyUvNIl2B7w0195h6gRAx89wPmRTNuFuqUJvbvqSmiXQ1kt3Sk+5JiRg5zg61HkC\nY6xu97qVoHUQk2PucCj/81BagAk2t7qb3uI6ruGqCs6sx64oy6RjAP8hAoGAdJJK\nggM7UMTVBlWGxi9uroTiNBys/JDvcVe/bC/4j42hvFrvAvjF2yMQphIvtwFSTovM\ncWjsUmVERqtMFzmaZOsAJ+ZFfZrmlYNFauSQrJ9/hzW1CJ2DbUAQSAGeM3vXBoT3\nIJJRtE4b8p4wz6Cn19MTOwdEJOI7oHnwxcKTk3ECgYBRrnY7vMVChr7Ptn69DY72\nQAYqRIUkL0tFamimu1rdkoKD8xdluWdG/qd/vqAy4NpXOw7uF6yJsBUxZWPJF+7t\n7r/JSeM7iQ4SJKL8Twhli7ASdRGQHZvq5WZlJ/o3Y76CCuItyWbOx+VN2atWJYYG\nOZBzU2UclI2BovgaNmBRyg==\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-fbsvc@philippine-stock-exchang-296cd.iam.gserviceaccount.com",
  "client_id": "109894096775029961107",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40philippine-stock-exchang-296cd.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com"
}

            """
                val stream: InputStream =
                    ByteArrayInputStream(jsonString.toByteArray(StandardCharsets.UTF_8))
                val googleCredentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList(FIREBASE_MESSAGING_SCOPE))
                googleCredentials.refreshIfExpired()
                googleCredentials.accessToken.tokenValue
            } catch (e: IOException) {
                Log.e("AccessToken", "Error retrieving access token", e)
                null
            }
        }

        override fun onPostExecute(token: String?) {
            callback.onAccessTokenReceived(token)
        }
    }

    interface AccessTokenCallback {
        fun onAccessTokenReceived(token: String?)
    }
}
