package com.pse.pse.data.repository


import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.functions.functions
import com.pse.pse.models.CreditMeta
import com.pse.pse.models.EnsureSalaryResult
import com.pse.pse.models.LevelCondition
import com.pse.pse.models.SalaryProfile
import com.pse.pse.models.TeamLevelModel
import com.pse.pse.models.TeamLevelStatus
import com.pse.pse.models.TeamStats
import com.pse.pse.models.UserListModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TeamRepository {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions = Firebase.functions("us-central1")


    /**
     * Calls computeTeamLevelsAndCreditProfit on the server.
     *
     * @return Pair(first = list of level stats, second = profit meta)
     */
    suspend fun fetchLevelsAndMaybeCredit(
        userId: String
    ): Pair<List<TeamLevelStatus>, CreditMeta> {

        val data = hashMapOf("userId" to userId)
        val result = functions.getHttpsCallable("computeTeamLevelsAndCreditProfit").call(data)
            .await().data as HashMap<*, *>

        /* ---------- levels ---------- */
        @Suppress("UNCHECKED_CAST") val levelsRaw = result["levels"] as List<HashMap<String, *>>

        val levels = levelsRaw.map { m ->
            /* users -------------------------------------------------------- */
            @Suppress("UNCHECKED_CAST") val usersRaw = m["users"] as List<HashMap<String, *>>
            val users = usersRaw.map { u ->
                UserListModel(
                    uid = u["uid"] as String,
                    name = u["firstName"] as? String ?: "",
                    lName = u["lastName"] as? String ?: "",
                    status = u["status"] as String
                )
            }

            val tl = TeamLevelModel(
                level = (m["level"] as Number).toInt(),
                requiredMembers = (m["requiredMembers"] as Number).toInt(),
                profitPercentage = (m["profitPercentage"] as Number).toDouble(),
                totalUsers = (m["totalUsers"] as Number).toInt(),
                activeUsers = (m["activeUsers"] as Number).toInt(),
                inactiveUsers = (m["inactiveUsers"] as Number).toInt(),
                totalDeposit = (m["totalDeposit"] as Number).toDouble(),
                users = users                                  // ← NEW
            )
            TeamLevelStatus(tl, m["levelUnlocked"] as Boolean)
        }

        /* ---------- meta ---------- */
        val meta = CreditMeta(
            booked = result["profitBooked"] as Boolean,
            creditedAmount = (result["creditedAmount"] as Number).toDouble()
        )

        return levels to meta
    }

    /** util: current user’s investment.totalDeposit */
    private suspend fun getUserTotalDeposit(userId: String): Double {
        val snap = db.collection("accounts").whereEqualTo("userId", userId).limit(1).get().await()

        return snap.documents.firstOrNull()?.get("investment.totalDeposit")?.toString()
            ?.toDoubleOrNull() ?: 0.0
    }

    /** ---------- TEAM-RANKINGS (self-deposit only) ---------- */
    suspend fun calculateTeamRanking(userId: String): TeamStats {

        // rank ladder (5 rows)
        val levelConditions = listOf(
            LevelCondition(1, 2500.0),   // Astro Cadet
            LevelCondition(2, 5000.0),   // Star Commander
            LevelCondition(3, 15000.0),   // Galaxy Leader
            LevelCondition(4, 50000.0),   // Nova Captain
            LevelCondition(5, 100000.0)    // Solar General
        )

        val selfDeposit = getUserTotalDeposit(userId)

        val unlocked = levelConditions.filter { selfDeposit >= it.minInvestment }.map { it.level }

        return TeamStats(
            currentInvestment = selfDeposit, unlockedLevels = unlocked
        )
    }


    suspend fun ensureSalaryProfile(userId: String): EnsureSalaryResult {
        val raw = functions.getHttpsCallable("ensureSalaryProfile").call(mapOf("userId" to userId))
            .await().data as HashMap<*, *>
        return EnsureSalaryResult(
            ok = raw["ok"] as? Boolean ?: true, existed = raw["existed"] as? Boolean ?: false
        )
    }

    suspend fun fetchSalaryCurrentAdb(userId: String): Double? {
        val raw = functions.getHttpsCallable("getSalaryProfile")
            .call(mapOf("userId" to userId))
            .await().data as HashMap<*, *>

        val exists = raw["exists"] as? Boolean ?: false
        if (!exists) return null

        return (raw["currentAdb"] as? Number)?.toDouble()
    }

    fun salaryProfileFlow(userId: String): Flow<SalaryProfile?> = callbackFlow {
        val ref = db.collection("salaryProfiles").document(userId)

        val registration = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(null)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                trySend(snap.toObject<SalaryProfile>())
            } else {
                trySend(null)
            }
        }

        awaitClose { registration.remove() }
    }
}