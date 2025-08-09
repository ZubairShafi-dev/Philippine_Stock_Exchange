// app/src/main/java/com/pse/pse/data/repository/LeadershipRepository.kt
package com.pse.pse.data.repository

import android.app.Application
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.pse.pse.models.LeadershipProgress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LeadershipRepository(application: Application) {
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    fun observeProgress(userId: String): Flow<LeadershipProgress> = callbackFlow {
        val ref = db.collection("leadership").document(userId)
        val sub = ref.addSnapshotListener { snap, _ ->
            trySend(snapshotToProgress(userId, snap))
        }
        awaitClose { sub.remove() }
    }

    private fun snapshotToProgress(userId: String, snap: DocumentSnapshot?): LeadershipProgress {
        if (snap == null || !snap.exists()) return LeadershipProgress(userId = userId)
        return LeadershipProgress(
            userId = userId,
            directActiveCount = (snap.getLong("directActiveCount") ?: 0L).toInt(),
            awarded = (snap.get("awarded") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
            lastCheckedAt = snap.getTimestamp("lastCheckedAt") ?: Timestamp.now(),
            lastAwardAmount = (snap.getDouble("lastAwardAmount") ?: 0.0)
        )
    }

    suspend fun ensureCheckNow(userId: String): Result<Unit> {
        return try {
            functions
                .getHttpsCallable("checkLeadershipBonus")
                .call(mapOf("userId" to userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
