package com.pse.pse.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pse.pse.models.AccountModel
import com.pse.pse.models.AnnouncementModel
import java.util.Calendar

class AccountRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun getAccount(uId: String): LiveData<AccountModel?> {
        val accountLiveData = MutableLiveData<AccountModel?>()

        db.collection("accounts")
            .whereEqualTo("userId", uId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    accountLiveData.postValue(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val account = snapshot.documents[0].toObject(AccountModel::class.java)
                    accountLiveData.postValue(account)
                } else {
                    accountLiveData.postValue(null)
                }
            }

        return accountLiveData
    }

    fun getAnnouncements(callback: (List<AnnouncementModel>?) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        // Get the current date
        val currentDate = Calendar.getInstance().time

        // Calculate the date one week ago
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.WEEK_OF_YEAR, -1)
        val lastWeekDate = calendar.time

        // Query Firestore for announcements within the last week
        db.collection("announcements")
            .whereGreaterThan("time", lastWeekDate)  // Filter by timestamp
            .orderBy("time", Query.Direction.DESCENDING)  // Order by "time" in descending order
            .get()  // Fetch the documents
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && !snapshot.isEmpty) {
                    // Map documents to AnnouncementModel
                    val announcements = snapshot.documents.mapNotNull {
                        it.toObject(AnnouncementModel::class.java)
                    }
                    callback(announcements)  // Pass the list of announcements
                } else {
                    callback(emptyList())  // No announcements found
                }
            }
            .addOnFailureListener { exception ->
                callback(null)  // Return null if there's an error
            }
    }
    fun getAnnouncementImageUrls(callback: (List<String>?) -> Unit) {
        db.collection("announcement_images")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val urls = snapshot.documents.mapNotNull { doc ->
                        doc.getString("imageUrl")
                    }
                    callback(urls)
                } else {
                    callback(emptyList())
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}