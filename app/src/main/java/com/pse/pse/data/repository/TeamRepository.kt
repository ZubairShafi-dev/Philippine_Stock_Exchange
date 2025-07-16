package com.pse.pse.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.functions
import com.pse.pse.models.AchievementModel
import com.pse.pse.models.CreditMeta
import com.pse.pse.models.LevelCondition
import com.pse.pse.models.TeamLevelModel
import com.pse.pse.models.TeamLevelStatus
import com.pse.pse.models.TeamStats
import com.pse.pse.models.TransactionModel
import com.pse.pse.models.UserListModel
import kotlinx.coroutines.tasks.await

class TeamRepository {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions = Firebase.functions


    /**
     * Calls computeTeamLevelsAndCreditProfit on the server.
     *
     * @return Pair(first = list of level stats, second = profit meta)
     */
    suspend fun fetchLevelsAndMaybeCredit(
        userId: String
    ): Pair<List<TeamLevelStatus>, CreditMeta> {

        val data = hashMapOf("userId" to userId)
        val result = functions
            .getHttpsCallable("computeTeamLevelsAndCreditProfit")
            .call(data).await().data as HashMap<*, *>

        /* ---------- levels ---------- */
        @Suppress("UNCHECKED_CAST")
        val levelsRaw = result["levels"] as List<HashMap<String, *>>

        val levels = levelsRaw.map { m ->
            /* users -------------------------------------------------------- */
            @Suppress("UNCHECKED_CAST")
            val usersRaw = m["users"] as List<HashMap<String, *>>
            val users = usersRaw.map { u ->
                UserListModel(
                    uid    = u["uid"]        as String,
                    name   = u["firstName"]  as? String ?: "",
                    lName  = u["lastName"]   as? String ?: "",
                    status = u["status"]     as String
                )
            }

            val tl = TeamLevelModel(
                level            = (m["level"]            as Number).toInt(),
                requiredMembers  = (m["requiredMembers"]  as Number).toInt(),
                profitPercentage = (m["profitPercentage"] as Number).toDouble(),
                totalUsers       = (m["totalUsers"]       as Number).toInt(),
                activeUsers      = (m["activeUsers"]      as Number).toInt(),
                inactiveUsers    = (m["inactiveUsers"]    as Number).toInt(),
                totalDeposit     = (m["totalDeposit"]     as Number).toDouble(),
                users            = users                                  // ← NEW
            )
            TeamLevelStatus(tl, m["levelUnlocked"] as Boolean)
        }

        /* ---------- meta ---------- */
        val meta = CreditMeta(
            booked         = result["profitBooked"] as Boolean,
            creditedAmount = (result["creditedAmount"] as Number).toDouble()
        )

        return levels to meta
    }

    private suspend fun getActiveDirectReferrals(uid: String): List<String> {
        return try {
            db.collection("users").whereEqualTo("referralCode", uid)      // people this uid invited
                .whereEqualTo("status", "active")       // only active ones
                .get().await().documents.mapNotNull { it.getString("uid") }
        } catch (e: Exception) {
            Log.e("TeamRepo", "active referral fetch failed for $uid", e)
            emptyList()
        }
    }


    private suspend fun getDirectReferrals(userId: String): List<String> {
        return try {
            val result = db.collection("users").whereEqualTo("referralCode", userId).get().await()

            result.documents.mapNotNull { it.getString("uid") }
        } catch (e: Exception) {
            Log.e("TeamRepo", "Error getting referrals for $userId", e)
            emptyList()
        }
    }

    suspend fun calculateTeamRanking(userId: String): TeamStats {
        val db = FirebaseFirestore.getInstance()

        val levelConditions = listOf(
            LevelCondition(1, 50.0, 5, 500.0, 2500.0),
            LevelCondition(2, 100.0, 10, 1500.0, 9000.0),
            LevelCondition(3, 150.0, 15, 2500.0, 25000.0),
            LevelCondition(4, 500.0, 20, 6500.0, 50000.0),
            LevelCondition(5, 1000.0, 35, 12000.0, 100000.0),
            LevelCondition(6, 2000.0, 50, 20000.0, 200000.0),
            LevelCondition(7, 3000.0, 75, 35000.0, 350000.0),
            LevelCondition(8, 5000.0, 100, 50000.0, 550000.0),
            LevelCondition(9, 8000.0, 150, 75000.0, 1000000.0),
            LevelCondition(10, 12000.0, 200, 125000.0, 3000000.0)


        )

        val directReferrals = getActiveDirectReferrals(userId)
        val allTeamMembers = mutableSetOf<String>()
        allTeamMembers.addAll(directReferrals)

        var directBusiness = 0.0
        var groupSell = 0.0

        // Direct business: sum of level 1 referrals' current balances
        for (uid in directReferrals) {
            val balance = getUserTotalDeposit(uid)
            directBusiness += balance
            groupSell += balance
        }

        // Traverse all levels for group sell
        val queue = ArrayDeque(directReferrals)
        while (queue.isNotEmpty()) {
            val uid = queue.removeFirst()
            val referrals = getDirectReferrals(uid)
            allTeamMembers.addAll(referrals)
            for (refId in referrals) {
                groupSell += getUserTotalDeposit(refId)
                queue.addLast(refId)
            }
        }

        // Get user current investment
        val userInvestment = getUserTotalDeposit(userId)

        // Evaluate unlocks
        val unlocked = mutableListOf<Int>()
        for (condition in levelConditions) {
            if (userInvestment >= condition.minInvestment && allTeamMembers.size >= condition.activeMembers && directBusiness >= condition.directBusiness && groupSell >= condition.groupSell) {
                unlocked.add(condition.level)
            }
        }

        return TeamStats(
            currentInvestment = userInvestment,
            activeMembers = allTeamMembers.size,
            directBusiness = directBusiness,
            groupSell = groupSell,
            unlockedLevels = unlocked
        )
    }

    private suspend fun getUserTotalDeposit(userId: String): Double {
        val result = db.collection("accounts").whereEqualTo("userId", userId).get().await()
        return result.documents.firstOrNull()?.get("investment.totalDeposit")?.toString()
            ?.toDoubleOrNull() ?: 0.0
    }

    suspend fun storeAchievementAndUpdateBalance(model: AchievementModel): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()

            // Step 1: Get account document
            val userAccountSnap =
                db.collection("accounts").whereEqualTo("userId", model.userId).get().await()

            if (userAccountSnap.isEmpty) {
                Log.e("TeamRepo", "❌ No account found for user: ${model.userId}")
                return false
            }

            val docRef = userAccountSnap.documents.first().reference

            // Step 2: Check if already claimed
            val docData = userAccountSnap.documents.first().data
            val claimedMap = docData?.get("claimedRewards") as? Map<*, *> ?: emptyMap<Any, Any>()
            if (claimedMap.containsKey(model.rankName)) {
                Log.w("TeamRepo", "⚠ Reward for ${model.rankName} already claimed")
                return false
            }

            // Step 3: Store achievement
            db.collection("achievements").add(model).await()

            // Step 4: Safely increment balance in a transaction
            db.runTransaction { tr ->
                val accountSnap = tr.get(docRef)
                val currentBalance =
                    ((accountSnap.get("investment") as? Map<*, *>)?.get("currentBalance"))?.toString()
                        ?.toDoubleOrNull() ?: 0.0

                val newBalance = currentBalance + model.rewardAmount

                // Update nested balance
                tr.update(docRef, "investment.currentBalance", newBalance)

                // Also mark reward as claimed
                tr.update(docRef, "claimedRewards.${model.rankName}", true)
            }.await()

            // Step 5: Log transaction entry
            val tx = TransactionModel(
                userId = model.userId,
                amount = model.rewardAmount,
                type = TransactionModel.TYPE_ACHIEVEMENT,
                address = model.rankName,
                status = TransactionModel.STATUS_COLLECTED,
                balanceUpdated = true,
                timestamp = Timestamp.now()
            )
            db.collection("transactions").document().set(tx.toMap()).await()

            Log.d("TeamRepo", "✅ Reward processed, balance updated, and transaction saved.")
            true

        } catch (e: Exception) {
            Log.e("TeamRepo", "🔥 Error storing achievement or updating balance", e)
            false
        }
    }
}