package com.pse.pse.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pse.pse.models.PlanModel
import com.pse.pse.models.TransactionModel
import com.pse.pse.utils.SharedPrefManager
import com.pse.pse.utils.Status
import com.pse.pse.utils.Constants
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class BuyPlanRepo(private val context: Context) {
    private val prefService = SharedPrefManager(context.applicationContext)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private var planListener: ListenerRegistration? = null

    private suspend fun getUserId(): String? {
        return prefService.getId()
    }

    fun getPlans(): LiveData<List<PlanModel>> {
        val plansLiveData = MutableLiveData<List<PlanModel>>()

        planListener =
            db.collection(Constants.PLAN_COLLECTION).addSnapshotListener { snapshots, e ->
                if (e != null) {
                    plansLiveData.value = emptyList()
                    return@addSnapshotListener
                }
                val plans = snapshots?.documents?.mapNotNull { it.toObject(PlanModel::class.java) }
                plansLiveData.value = plans ?: emptyList()
            }

        return plansLiveData
    }

    private suspend fun getPlanDetails(planName: String): PlanModel? {
        return try {
            val snapshot =
                db.collection("plans").whereEqualTo("planName", planName).limit(1).get().await()

            if (snapshot.isEmpty) null
            else snapshot.documents.first().toObject(PlanModel::class.java)
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "Error fetching plan details: $e")
            null
        }
    }

    suspend fun buyPlan(amount: Double, planName: String): Status {
        return try {
            val userId = getUserId() ?: return Status.NO_USER_FOUND
            val planDetails = getPlanDetails(planName) ?: return Status.NO_PLAN_FOUND

            // 1️⃣ Pre-create the userPlan document for reference
            val userPlanRef = db.collection("userPlans").document()
            val nowTs = Timestamp.now()
            val durationDays = planDetails.planDays ?: 0

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = nowTs.seconds * 1000
            calendar.add(Calendar.DAY_OF_YEAR, durationDays)
            val expiryDate = Timestamp(calendar.time)

            val planData = mapOf(
                "user_id" to userId,
                "plan_name" to planName,
                "invested_amount" to amount,
                "direct_profit" to (amount * (planDetails.directProfit ?: 0.0) / 100),
                "daily_profit" to (amount * (planDetails.dailyPercentage ?: 0.0) / 100),
                "percentage" to (planDetails.dailyPercentage ?: 0.0),
                "directProfitPercent" to (planDetails.directProfit ?: 0.0),
                "start_date" to nowTs,
                "status" to "active",
                "lastCollectedDate" to nowTs,
                "profitTrack" to 0.0,
                "expiry_date" to expiryDate,
                "docId" to userPlanRef.id
            )

            // 2️⃣ Find account document (before transaction for reference)
            val accountSnapshot = db.collection("accounts")
                .whereEqualTo("userId", userId).limit(1).get().await()
            if (accountSnapshot.isEmpty) return Status.NO_USER_FOUND
            val accountDoc = accountSnapshot.first()
            val accountRef = accountDoc.reference

            // 3️⃣ Find user document (for referral/activation)
            val userQuery =
                db.collection("users").whereEqualTo("uid", userId).limit(1).get().await()
            val userDocToUpdate = userQuery.firstOrNull()
            val userRef = userDocToUpdate?.reference

            // 4️⃣ Transaction — atomically update balances and create plan
            db.runTransaction { tr ->
                // Always read latest inside transaction!
                val accountSnap = tr.get(accountRef)
                val investmentMap =
                    accountSnap.get("investment") as? Map<*, *> ?: emptyMap<String, Any>()
                val earningsMap =
                    accountSnap.get("earnings") as? Map<*, *> ?: emptyMap<String, Any>()

                val remainingBalance =
                    (investmentMap["remainingBalance"] as? Number)?.toDouble() ?: 0.0
                val currentBalance = (investmentMap["currentBalance"] as? Number)?.toDouble() ?: 0.0
                val minInvestment = planDetails.minAmount ?: 0.0

                if (amount < minInvestment) throw Exception("INVALID_AMOUNT")
                if (remainingBalance < amount || currentBalance < amount) throw Exception("NOT_ENOUGH_BALANCE")

                // Calculate new balances
                val newRemainingBalance = remainingBalance - amount
                val newCurrentBalance = currentBalance - amount

                // Earnings (no changes on buy, but you could add teamProfit here if needed)
                val currentBuyingProfit = (earningsMap["buyingProfit"] as? Double) ?: 0.0
                val currentReferralProfit = (earningsMap["referralProfit"] as? Double) ?: 0.0
                val currentTeamProfit = (earningsMap["teamProfit"] as? Double) ?: 0.0
                val updatedBuyingProfit = currentBuyingProfit
                val updatedDailyProfit =
                    updatedBuyingProfit + currentReferralProfit + currentTeamProfit
                val updatedTotalEarned = updatedDailyProfit

                // Update account balances atomically
                tr.update(
                    accountRef, mapOf(
                        "investment.remainingBalance" to newRemainingBalance,
                        "investment.currentBalance" to newCurrentBalance,
                        "earnings.buyingProfit" to updatedBuyingProfit,
                        "earnings.dailyProfit" to updatedDailyProfit,
                        "earnings.totalEarned" to updatedTotalEarned
                    )
                )

                // Create the plan in userPlans (safe to do in transaction, as docId is known)
                tr.set(userPlanRef, planData)

                // Activate user if needed
                if (userRef != null) {
                    tr.update(userRef, "status", "active")
                }

                // Log investment transaction (safe in transaction)
                val txRef = db.collection("transactions").document()
                val investmentTxn = TransactionModel(
                    transactionId = txRef.id,
                    userId = userId,
                    amount = amount,
                    type = TransactionModel.TYPE_INVESTMENT_BOUGHT,
                    address = planName,
                    status = TransactionModel.STATUS_BOUGHT,
                    balanceUpdated = true,
                    timestamp = Timestamp.now()
                )
                tr.set(txRef, investmentTxn.toMap())

                // Could also add referral bonus logic here, **within** the transaction for atomicity!
            }.await()

            Status.SUCCESS
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "Error buying plan: ${e.message}", e)
            when (e.message) {
                "INVALID_AMOUNT" -> Status.INVALID_AMOUNT
                "NOT_ENOUGH_BALANCE" -> Status.NOT_ENOUGH_BALANCE
                else -> Status.ERROR
            }
        }
    }

    private suspend fun firstTimeBuy(userId: String) {
        try {
            val userQuery =
                db.collection("users").whereEqualTo("uid", userId).limit(1).get().await()

            val userDoc = userQuery.documents.firstOrNull()

            if (userDoc != null) {
                userDoc.reference.update("status", "active").await()
                Log.d("BuyPlanRepo", "First time buy setup completed.")
            } else {
                Log.w("BuyPlanRepo", "No user found with uid = $userId")
            }

        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "Error in firstTimeBuy setup: ${e.message}", e)
        }
    }

    suspend fun sellForex(planDocId: String): Status {
        return try {
            // References (do this outside transaction)
            val planRef = db.collection("userPlans").document(planDocId)
            val planSnap = planRef.get().await()
            if (!planSnap.exists()) return Status.NO_PLAN_FOUND
            val data = planSnap.data ?: return Status.ERROR

            val userId = data["user_id"] as? String ?: return Status.ERROR
            val planName = data["plan_name"] as? String ?: return Status.ERROR

            // Pre-load account doc reference (for speed)
            val acctSnap =
                db.collection("accounts").whereEqualTo("userId", userId).limit(1).get().await()
            if (acctSnap.isEmpty) return Status.NO_USER_FOUND
            val acctDoc = acctSnap.first()
            val acctRef = acctDoc.reference

            db.runTransaction { tr ->
                // 🔒 Re-fetch everything inside the transaction for atomicity!
                val planTxnSnap = tr.get(planRef)
                val planStatus = planTxnSnap.getString("status") ?: ""
                if (planStatus == "sold" || planStatus == "forex_sold") {
                    // Already sold, prevent double credit
                    throw Exception("ALREADY_SOLD")
                }

                val invested = (planTxnSnap.get("invested_amount") as? Number)?.toDouble() ?: 0.0
                val profitTrack = (planTxnSnap.get("profitTrack") as? Number)?.toDouble() ?: 0.0

                // Account
                val acctTxnSnap = tr.get(acctRef)
                val invMap = acctTxnSnap.get("investment") as? Map<*, *> ?: emptyMap<String, Any>()
                val curMap = acctTxnSnap.get("earnings") as? Map<*, *> ?: emptyMap<String, Any>()
                val remBal = (invMap["remainingBalance"] as? Number)?.toDouble() ?: 0.0
                val curBal = (invMap["currentBalance"] as? Number)?.toDouble() ?: 0.0

                // 🔹 CREDIT PRINCIPAL BACK — No profit here as Forex logic, but you can adjust if needed
                val newRem = remBal + invested
                val newCur = curBal + invested

                // (Optional) Add profit to earnings if that matches your logic, otherwise skip

                // ATOMIC UPDATE: account balances
                tr.update(
                    acctRef, mapOf(
                        "investment.remainingBalance" to newRem,
                        "investment.currentBalance" to newCur
                    )
                )

                // ATOMIC UPDATE: plan status and log sold/expiry
                tr.update(
                    planRef, mapOf(
                        "status" to "forex_sold",  // More specific than just "sold"
                        "lastTrackedDate" to Timestamp.now(),
                        "expiry_date" to Timestamp.now()
                    )
                )

                // ATOMIC CREATE: transaction log
                val txRef = db.collection("transactions").document()
                val investmentTxn = TransactionModel(
                    transactionId = txRef.id,
                    userId = userId,
                    amount = invested + profitTrack,  // If you want to add profit, otherwise just `invested`
                    type = TransactionModel.TYPE_INVESTMENT_SOLD,
                    address = planName,
                    status = TransactionModel.STATUS_SOLD,
                    balanceUpdated = true,
                    timestamp = Timestamp.now()
                )
                tr.set(txRef, investmentTxn.toMap())
            }.await()

            Status.SUCCESS
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error in sellForex: ${e.message}", e)
            when (e.message) {
                "ALREADY_SOLD" -> Status.ERROR // Or a custom status if you want
                else -> Status.ERROR
            }
        }
    }

    suspend fun buyStock(amount: Double, stockSymbol: String): Status {
        return try {
            Log.d("BuyPlanRepo", "🔁 Starting buyStock: amount=$amount, symbol=$stockSymbol")

            val userId = getUserId()
            if (userId.isNullOrEmpty()) {
                Log.d("BuyPlanRepo", "❌ No user ID found")
                return Status.NO_USER_FOUND
            }

            val stockDetails = getPlanDetails(stockSymbol)
            if (stockDetails == null) {
                Log.d("BuyPlanRepo", "❌ No plan details for symbol=$stockSymbol")
                return Status.NO_PLAN_FOUND
            }

            val dailyPct = stockDetails.dailyPercentage ?: 0.0
            val directPct = stockDetails.directProfit ?: 0.0

            if (amount <= 0) {
                Log.d("BuyPlanRepo", "❌ Invalid amount entered: $amount")
                return Status.INVALID_AMOUNT
            }

            // Pre-create references outside transaction for performance
            val acctSnap = db.collection("accounts")
                .whereEqualTo("userId", userId).limit(1).get().await()
            if (acctSnap.isEmpty) {
                Log.d("BuyPlanRepo", "❌ No account found for userId=$userId")
                return Status.NO_USER_FOUND
            }
            val acctDoc = acctSnap.first()
            val acctRef = acctDoc.reference

            // Pre-create userPlan doc reference
            val userPlanRef = db.collection("userPlans").document()
            val nowTs = Timestamp.now()
            val durationDays = stockDetails.planDays ?: 0

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = nowTs.seconds * 1000
            calendar.add(Calendar.DAY_OF_YEAR, durationDays)
            val expiryDate = Timestamp(calendar.time)

            val planMap = mapOf(
                "user_id" to userId,
                "plan_name" to stockSymbol,
                "invested_amount" to amount,
                "direct_profit" to (amount * (directPct / 100)),
                "daily_profit" to (amount * (dailyPct / 100)),
                "percentage" to dailyPct,
                "directProfitPercent" to directPct,
                "start_date" to nowTs,
                "expiry_date" to expiryDate,
                "status" to "stock_open",
                "lastCollectedDate" to nowTs,
                "profitTrack" to 0.0,
                "lastTrackedDate" to nowTs,
                "docId" to userPlanRef.id
            )

            // Preload user doc (for activation)
            val userSnap = db.collection("users")
                .whereEqualTo("uid", userId).limit(1).get().await()
            val userDocToUpdate = userSnap.firstOrNull()
            val userRef = userDocToUpdate?.reference

            db.runTransaction { tr ->
                // Re-fetch account and check balance atomically
                val accTxnSnap = tr.get(acctRef)
                val invMap = accTxnSnap.get("investment") as? Map<*, *> ?: emptyMap<String, Any>()
                val remBal = (invMap["remainingBalance"] as? Number)?.toDouble() ?: 0.0
                val curBal = (invMap["currentBalance"] as? Number)?.toDouble() ?: 0.0

                if (curBal < amount || remBal < amount) {
                    throw Exception("NOT_ENOUGH_BALANCE")
                }

                // Deduct funds atomically
                tr.update(
                    acctRef, mapOf(
                        "investment.remainingBalance" to (remBal - amount),
                        "investment.currentBalance" to (curBal - amount)
                    )
                )

                // Save plan (atomically, with known docId)
                tr.set(userPlanRef, planMap)

                // Activate user if needed
                if (userRef != null) {
                    tr.update(userRef, "status", "active")
                }

                // Transaction log (inside txn)
                val txRef = db.collection("transactions").document()
                val investmentTxn = TransactionModel(
                    transactionId = txRef.id,
                    userId = userId,
                    amount = amount,
                    type = TransactionModel.TYPE_INVESTMENT_BOUGHT,
                    address = stockSymbol,
                    status = TransactionModel.STATUS_BOUGHT,
                    balanceUpdated = true,
                    timestamp = Timestamp.now()
                )
                tr.set(txRef, investmentTxn.toMap())
            }.await()

            // Optionally, call collectStockDailyProfit AFTER transaction if needed (not atomic, but safe as it doesn't touch balances directly)
            // collectStockDailyProfit(userId) // If required

            Log.d("BuyPlanRepo", "✅ buyStock completed successfully.")
            firstTimeBuy(userId)
            Status.SUCCESS

        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error in buyStock: ${e.message}", e)
            when (e.message) {
                "NOT_ENOUGH_BALANCE" -> Status.NOT_ENOUGH_BALANCE
                else -> Status.ERROR
            }
        }
    }

    suspend fun sellStock(planDocId: String): Status {
        return try {
            // Preload plan and account doc references
            val planRef = db.collection("userPlans").document(planDocId)
            val planSnap = planRef.get().await()
            if (!planSnap.exists()) return Status.NO_PLAN_FOUND
            val data = planSnap.data ?: return Status.ERROR

            val userId = data["user_id"] as? String ?: return Status.ERROR
            val planName = data["plan_name"] as? String ?: return Status.ERROR

            val acctSnap =
                db.collection("accounts").whereEqualTo("userId", userId).limit(1).get().await()
            if (acctSnap.isEmpty) return Status.NO_USER_FOUND
            val acctDoc = acctSnap.first()
            val acctRef = acctDoc.reference

            db.runTransaction { tr ->
                // --- Fetch latest inside transaction for atomicity ---
                val planTxnSnap = tr.get(planRef)
                val planStatus = planTxnSnap.getString("status") ?: ""
                if (planStatus == "stock_sold" || planStatus == "sold") {
                    // Already sold, prevent double credit
                    throw Exception("ALREADY_SOLD")
                }

                val invested = (planTxnSnap.get("invested_amount") as? Number)?.toDouble() ?: 0.0
                val profitTrack = (planTxnSnap.get("profitTrack") as? Number)?.toDouble() ?: 0.0

                // --- Account ---
                val acctTxnSnap = tr.get(acctRef)
                val invMap = acctTxnSnap.get("investment") as? Map<*, *> ?: emptyMap<String, Any>()
                val earnMap = acctTxnSnap.get("earnings") as? Map<*, *> ?: emptyMap<String, Any>()

                val remBal = (invMap["remainingBalance"] as? Number)?.toDouble() ?: 0.0
                val curBal = (invMap["currentBalance"] as? Number)?.toDouble() ?: 0.0

                val buyProfit = (earnMap["buyingProfit"] as? Number)?.toDouble() ?: 0.0
                val dailyProfit = (earnMap["dailyProfit"] as? Number)?.toDouble() ?: 0.0
                val totalEarned = (earnMap["totalEarned"] as? Number)?.toDouble() ?: 0.0

                // --- Credit principal+profit and update earnings ---
                val credit = invested + profitTrack
                tr.update(
                    acctRef, mapOf(
                        "earnings.buyingProfit" to buyProfit + profitTrack,
                        "earnings.dailyProfit" to dailyProfit + profitTrack,
                        "earnings.totalEarned" to totalEarned + profitTrack,
                        "investment.remainingBalance" to remBal + credit,
                        "investment.currentBalance" to curBal + credit
                    )
                )

                // --- Mark plan as sold ---
                tr.update(
                    planRef, mapOf(
                        "status" to "stock_sold",
                        "lastTrackedDate" to Timestamp.now(),
                        "expiry_date" to Timestamp.now()
                    )
                )

                // --- Log transaction ---
                val txRef = db.collection("transactions").document()
                val investmentTxn = TransactionModel(
                    transactionId = txRef.id,
                    userId = userId,
                    amount = credit,
                    type = TransactionModel.TYPE_INVESTMENT_SOLD,
                    address = planName,
                    status = TransactionModel.STATUS_SOLD,
                    balanceUpdated = true,
                    timestamp = Timestamp.now()
                )
                tr.set(txRef, investmentTxn.toMap())
            }.await()

            Status.SUCCESS
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error in sellStock: ${e.message}", e)
            when (e.message) {
                "ALREADY_SOLD" -> Status.ERROR // Or custom status
                else -> Status.ERROR
            }
        }
    }

    fun getWalletTotalLive(planStatus: String, userId: String): LiveData<Double> {
        val totalLiveData = MutableLiveData<Double>()

        Log.d(
            "FirestoreQuery",
            "Fetching wallet total for userId: $userId and planStatus: $planStatus"
        )

        db.collection("userPlans")
            .whereEqualTo("user_id", userId)
            .whereEqualTo("status", planStatus)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e("FirestoreQuery", "Error fetching data: ${error?.message}")
                    totalLiveData.postValue(0.0)
                    return@addSnapshotListener
                }

                Log.d(
                    "FirestoreQuery",
                    "Snapshot received: ${snapshot.documents.size} documents found"
                )

                var total = 0.0
                val now = Date()

                for (doc in snapshot.documents) {
                    Log.d("FirestoreQuery", "Document data: ${doc.data}")

                    val invested = (doc["invested_amount"] as? Number)?.toDouble() ?: 0.0
                    val percent = (doc["percentage"] as? Number)?.toDouble() ?: 0.0
                    val lastTracked = (doc["lastTrackedDate"] as? Timestamp)?.toDate() ?: now
                    val profitTrack = (doc["profitTrack"] as? Number)?.toDouble() ?: 0.0

                    Log.d(
                        "FirestoreQuery",
                        "Invested: $invested, Percent: $percent, LastTracked: $lastTracked"
                    )

                    // Calculate days since lastTrackedDate
                    val days =
                        TimeUnit.MILLISECONDS.toDays(now.time - lastTracked.time).coerceAtLeast(0)
                    Log.d("FirestoreQuery", "Days since last tracked: $days")

                    val calculatedProfit = invested * (percent / 100) * days
                    Log.d("FirestoreQuery", "Calculated profit: $calculatedProfit")

                    // Logic to include profitTrack if status is medicine_active or stock_open
                    val status = doc["status"] as? String ?: ""
                    val additional = if (status == "medicine_active" || status == "stock_open") {
                        profitTrack
                    } else {
                        calculatedProfit
                    }

                    total += invested + additional
                }

                Log.d("FirestoreQuery", "Total wallet value: $total")
                totalLiveData.postValue(total)
            }

        return totalLiveData
    }

    suspend fun getUsersByPlan(status: String, userId: String): List<Map<String, Any>> {
        return try {
            Log.d("BuyPlanRepo", "🔍 Fetching plsns and status='$status'")

            val snapshot = db.collection("userPlans").whereEqualTo("user_id", userId)
                .whereEqualTo("status", status).get().await()

            Log.d("BuyPlanRepo", "📦 Firestore returned ${snapshot.size()} documents")

            val userPlans = mutableListOf<Map<String, Any>>()

            for (doc in snapshot.documents) {
                val data = doc.data
                if (data != null) {
                    val withId = data + mapOf("docId" to doc.id)
                    Log.d("BuyPlanRepo", "✅ UserPlan found: $withId")
                    userPlans.add(withId)
                } else {
                    Log.w("BuyPlanRepo", "⚠️ Document ${doc.id} has no data")
                }
            }

            if (userPlans.isEmpty()) {
                Log.w("BuyPlanRepo", "⚠️ No matching user plans  with status $status")
            }

            userPlans
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error fetching plan with status $status: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun buyMedicine(amount: Double, planName: String): Status {
        return try {
            val userId = getUserId() ?: return Status.NO_USER_FOUND
            val planDetails = getPlanDetails(planName) ?: return Status.NO_PLAN_FOUND

            // Get the profit percentages from the plan details
            val dailyPct = planDetails.dailyPercentage ?: 0.0
            val directPct = planDetails.directProfit ?: 0.0

            // Calculate profits
            val calculatedDailyProfit = amount * (dailyPct / 100)
            val calculatedDirectProfit = amount * (directPct / 100)

            // Calculate duration
            val durationDays = planDetails.planDays ?: return Status.INVALID_AMOUNT

            // Pre-fetch account doc reference for transaction
            val acctSnap =
                db.collection("accounts").whereEqualTo("userId", userId).limit(1).get().await()
            if (acctSnap.isEmpty) return Status.NO_USER_FOUND
            val acctDoc = acctSnap.first()
            val acctRef = acctDoc.reference

            // Pre-create userPlan doc reference
            val userPlanRef = db.collection("userPlans").document()
            val now = Timestamp.now()
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now.seconds * 1000
            calendar.add(Calendar.DAY_OF_YEAR, durationDays)
            val expiryDate = Timestamp(calendar.time)

            val planMap = mapOf(
                "user_id" to userId,
                "plan_name" to planName,
                "invested_amount" to amount,
                "direct_profit" to calculatedDirectProfit,
                "daily_profit" to calculatedDailyProfit,
                "percentage" to dailyPct,
                "directProfitPercent" to directPct,
                "start_date" to now,
                "expiry_date" to expiryDate,
                "status" to "medicine_active",
                "lastCollectedDate" to now,
                "profitTrack" to 0.0,
                "lastTrackedDate" to now,
                "docId" to userPlanRef.id
            )

            // Preload user doc (for activation, if needed)
            val userSnap = db.collection("users").whereEqualTo("uid", userId).limit(1).get().await()
            val userDocToUpdate = userSnap.firstOrNull()
            val userRef = userDocToUpdate?.reference

            db.runTransaction { tr ->
                // Fetch latest account
                val accTxnSnap = tr.get(acctRef)
                val invMap = accTxnSnap.get("investment") as? Map<*, *> ?: emptyMap<String, Any>()
                val remBal = (invMap["remainingBalance"] as? Number)?.toDouble() ?: 0.0
                val curBal = (invMap["currentBalance"] as? Number)?.toDouble() ?: 0.0

                // Sufficient balance?
                if (amount <= 0 || remBal < amount || curBal < amount) {
                    throw Exception("NOT_ENOUGH_BALANCE")
                }

                // Deduct atomically
                tr.update(
                    acctRef, mapOf(
                        "investment.remainingBalance" to (remBal - amount),
                        "investment.currentBalance" to (curBal - amount)
                    )
                )

                // Save plan (atomic)
                tr.set(userPlanRef, planMap)

                // Optional: Activate user if needed
                if (userRef != null) {
                    tr.update(userRef, "status", "active")
                }

                // Log investment transaction (atomic)
                val txRef = db.collection("transactions").document()
                val investmentTxn = TransactionModel(
                    transactionId = txRef.id,
                    userId = userId,
                    amount = amount,
                    type = TransactionModel.TYPE_INVESTMENT_BOUGHT,
                    address = planName,
                    status = TransactionModel.STATUS_BOUGHT,
                    balanceUpdated = true,
                    timestamp = Timestamp.now()
                )
                tr.set(txRef, investmentTxn.toMap())
            }.await()

            // Optionally, call this after transaction if you want (doesn't touch balance directly)
            // collectMedicineDailyProfit(userId)

            firstTimeBuy(userId)
            Status.SUCCESS

        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "Error in buyMedicine: ${e.message}", e)
            when (e.message) {
                "NOT_ENOUGH_BALANCE" -> Status.NOT_ENOUGH_BALANCE
                else -> Status.ERROR
            }
        }
    }

    // Calculate total days between start date and expiry date
    private fun calculateTotalDays(startDate: Date, expiryDate: Date): Int {
        val timeDifference = expiryDate.time - startDate.time
        return (timeDifference / (1000 * 60 * 60 * 24)).toInt()  // Convert time difference into days
    }

    // Function to fetch plans progress and update the progress bar
    fun fetchPlanProgress(userId: String, callback: (List<Int>) -> Unit) {
        // Reference to the user's plans
        val userPlanRef = db.collection("userPlans").whereEqualTo("user_id", userId)
            .whereEqualTo("status", "medicine_active")  // Only active plans
            .get()

        userPlanRef.addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                callback(emptyList())  // No active plans found for the user
                return@addOnSuccessListener
            }

            val progressList = mutableListOf<Int>()

            // Loop through each user's plan
            for (planDoc in snapshot.documents) {
                val startDate = planDoc.getTimestamp("start_date")?.toDate()
                val expiryDate = planDoc.getTimestamp("expiry_date")?.toDate()
                Log.d("BuyPlanRepo", "planDoc: $planDoc")
                if (startDate != null && expiryDate != null) {
                    val totalDays = calculateTotalDays(
                        startDate, expiryDate
                    )  // Calculate total days for the plan
                    Log.d("BuyPlanRepo", "totalDays: $totalDays")
                    val progress =
                        calculateProgress(startDate, expiryDate)  // Calculate progress for the user
                    Log.d("BuyPlanRepo", "progress: $progress")
                    progressList.add(progress)

                    // Update the progress bar with max value as expiry date and progress as current progress
                    // Assuming you update UI in adapter after getting all progress values
                }
            }

            callback(progressList)  // Return the list of calculated progress values for all plans
        }.addOnFailureListener {
            callback(emptyList())  // Handle failure to fetch user plans
        }
    }

    // Calculate the user's progress as a percentage based on the current date
    private fun calculateProgress(startDate: Date, expiryDate: Date): Int {
        val currentDate = Date()

        // Check if the current date is past the expiry date
        if (currentDate.after(expiryDate)) {
            return 100  // Fully completed if the current date is after the expiry date
        }

        // Calculate the number of days passed from start date to the current date
        val timeDifference = currentDate.time - startDate.time
        val daysPassed = (timeDifference / (1000 * 60 * 60 * 24)).toInt()

        val totalDays = calculateTotalDays(startDate, expiryDate)  // Get total days of the plan
        val progressPercentage =
            ((daysPassed.toDouble() / totalDays) * 100).toInt()  // Calculate progress as percentage

        return progressPercentage.coerceIn(0, 100)  // Ensure the progress is between 0 and 100
    }
}