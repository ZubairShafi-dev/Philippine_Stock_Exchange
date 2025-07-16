package com.trustledger.aitrustledger.fcm

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
  "project_id": "aitrustledger-3fe07",
  "private_key_id": "e8f8b87794fa08a7b62a51fadfd12ed0f0f75c04",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDI5W7+NLP4URAr\nTX06HOsqWOdlFzH2zBmGTG9GZg2xSxgn/vf4iqSLM93rH8GxN2yBb5nlCTeAgyGL\nRCHDZCZ2hfL0873uy+UJue0RKvNWilnSlAG5aj8m1gwlP8i57FZncNcEb3y3kd7N\n47+uj22GzDYbQOrzdHr3l7pWVpd0c+E9i8zBDEi9cZTpXO5pR+QLnBNE9tOlv0SD\nF1Qw/HrTo8YJydaFoYj1gLv5+s0qT26mXBm4UIUjClnYDS6nLRHEwZ6vEvkPaUdT\nz9F6A5XCqwEQHy4jAov35cxmiqjAbiaY1qH30DrqacYY6P+d0RknvXmus6PX2HnK\npJB+VqMRAgMBAAECggEAAPrwnC3GGofMhvkpvrxR7+4Vq/SgsWiPyWvxgScOkxjC\n4VZ6xh21qmAdiZzV+u8o3zO1k4x+CgLpyYMl6u0f5gUzjFstNP9aiqrh854rAfDR\nIFUWGJFI7CY2rAH08P/mMIerpU/rcuRaJIhJ1BILI2S3JCuNPrsCfhdUqQ1WKUyE\nvGYedpRseSaL6tCDjYY/tDihZkuC2IjO7TrjAzf/nVFux0C4jsHYR7nZyTxSG1Eg\n6hEOQeFnhaGxLqf5yN3G2LXPJHdnBxFeMtKpkrD8rcX7y9h0heNLvKyb3a0YGsVQ\nfNQ9YR9sKL2iwvOKmGR7i9bz9RJEAZHJbNY7iKyyGQKBgQDj79bUHaxJk2YSWh3l\nOnpiW4JqreOUP4GykjLYexqFiakn9Ff20lVAVH9p/sXAg256/c4hfB576E8biKVV\n46iAK9pPMpZUo6EsIhtyPaxWR4CXocMc9Ym1uVaehURbjgoieuNJVeypDJxIv6n1\ndSlKHofnk92GBvPxV2WoCY2vKQKBgQDhoVJfSpVAc2F0x/DydMpm1T0geV2gl18q\nMockvrMy2+dd6QOgwOzyXyJVdGMRYpUdDSY3sNkcjeDrNo/SfFa0yt/IeIxWjM71\nPBpoMvI4hyTpibKc9iNWPQBi/UQnzHGec2OEsX+YD2bQzC9Kk7VeBMlqxxlhvFLq\nOFFxyXsZqQKBgD8R4Cm1RDTfmkC9usPw2Gha1c1a7DvbDrIwje0kswP8QVgS3jwn\nmvs/5jmYC3PnaiySCfVt+KlkcG838je/1KISgEelwb8Nv80MavfDZOpCwqwyUGC0\n+DPWYsdeLLoApYFA658hLWjhWmUu04Jdtt0RcZ18ZrFtPxaqjjBe5FtxAoGBAM2O\nJPc/gX34H98+kFqy3/qTZl8BcrTtcvuEkO5+9c7t3HkH4hA/8x5UYXks0VxzTZnr\n8tdlvEZxU2m2iYyfTnbjJMEEYgYvvhRZL02irF2ncY95rUmmTEcyx/lm9wKzFQff\n49htxOqJjYfHnYX5z4/aGI242XzbD0bnC/v4LLgRAoGAUOCXMse4Taw3fLPY9Gac\nqozDDD0JKAte4kPNq2bOSMg1u03ZvfynJR6ncAe8cEkxcW+0kDKP9CGISDU06rhY\nm0TETJW7Ny2QxQA5wsUoKUNwM23SbQ3RwfBUljlwzACUlbW4IhYtoFYYJKNTlglz\nZuALmS9s5EIaygpmE08iO4E=\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-fbsvc@aitrustledger-3fe07.iam.gserviceaccount.com",
  "client_id": "111587825525969513663",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40aitrustledger-3fe07.iam.gserviceaccount.com",
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
