package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminVaultEntity
import com.example.data.model.LiveTickerEntity
import com.example.data.model.NotificationAlertEntity
import com.example.data.model.PayoutMethodEntity
import com.example.data.model.ReferralEntity
import com.example.data.model.SupportMessageEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserAccountEntity
import com.example.data.prefs.UserPreferences
import com.example.data.prefs.UserPreferencesRepository
import com.example.util.Formatters
import com.example.util.GoogleAuthHelper
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.initialize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

class JioPanalRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val prefsRepo: UserPreferencesRepository,
    private val notificationHelper: NotificationHelper
) {
    constructor(context: Context) : this(
        db = initFirestore(context.applicationContext),
        auth = Firebase.auth,
        prefsRepo = UserPreferencesRepository(context.applicationContext),
        notificationHelper = NotificationHelper(context.applicationContext)
    )

    companion object {
        private fun initFirestore(appContext: Context): FirebaseFirestore {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                Firebase.initialize(appContext)
            }
            return FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
        }

        const val COL_USERS = "users"
        const val COL_TRANSACTIONS = "transactions"
        const val COL_PAYOUT_METHODS = "payout_methods"
        const val COL_ALERTS = "notification_alerts"
        const val COL_SUPPORT_MESSAGES = "support_messages"
        const val COL_REFERRALS = "referrals"
        const val COL_LIVE_TICKER = "live_ticker"
        const val COL_APP_CONFIG = "app_config"
        const val COL_ADMIN_CONFIG = "admin_config"
    }

    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow

    fun observeAuthUser(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged { a, b -> a?.uid == b?.uid && a?.email == b?.email }

    private val snapshotExecutor = java.util.concurrent.Executor { it.run() }

    private fun <T> snapshotQueryFlow(
        query: Query,
        path: String,
        mapper: (QuerySnapshot) -> T
    ): Flow<T> = callbackFlow {
        val registration = query.addSnapshotListener(snapshotExecutor) { snap, error ->
            if (error != null) {
                try {
                    FirestoreErrorHandler.handleFirestoreError(error, OperationType.LIST, path, auth)
                } catch (e: Throwable) {
                    close(e)
                }
                return@addSnapshotListener
            }
            if (snap != null) {
                trySend(mapper(snap))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun <T> snapshotDocumentFlow(
        ref: DocumentReference,
        path: String,
        mapper: (DocumentSnapshot) -> T
    ): Flow<T> = callbackFlow {
        val registration = ref.addSnapshotListener(snapshotExecutor) { snap, error ->
            if (error != null) {
                try {
                    FirestoreErrorHandler.handleFirestoreError(error, OperationType.GET, path, auth)
                } catch (e: Throwable) {
                    close(e)
                }
                return@addSnapshotListener
            }
            if (snap != null) {
                trySend(mapper(snap))
            }
        }
        awaitClose { registration.remove() }
    }

    fun observeUser(uid: String): Flow<UserAccountEntity?> =
        snapshotDocumentFlow(db.collection(COL_USERS).document(uid), "$COL_USERS/$uid") { snap ->
            UserAccountEntity.fromSnapshot(snap)
        }

    fun observeTransactions(uid: String): Flow<List<TransactionEntity>> =
        snapshotQueryFlow(
            db.collection(COL_TRANSACTIONS).whereEqualTo("userId", uid),
            "$COL_TRANSACTIONS?userId=$uid"
        ) { snap ->
            snap.documents.mapNotNull { TransactionEntity.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }
        }

    fun observePayoutMethods(uid: String): Flow<List<PayoutMethodEntity>> =
        snapshotQueryFlow(
            db.collection(COL_PAYOUT_METHODS).whereEqualTo("userId", uid),
            "$COL_PAYOUT_METHODS?userId=$uid"
        ) { snap ->
            snap.documents.mapNotNull { PayoutMethodEntity.fromSnapshot(it) }
                .sortedWith(compareByDescending<PayoutMethodEntity> { it.isDefault }.thenByDescending { it.createdAt })
        }

    fun observeAlerts(uid: String): Flow<List<NotificationAlertEntity>> =
        snapshotQueryFlow(
            db.collection(COL_ALERTS).whereEqualTo("userId", uid),
            "$COL_ALERTS?userId=$uid"
        ) { snap ->
            snap.documents.mapNotNull { NotificationAlertEntity.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }
        }

    fun observeSupportMessages(uid: String): Flow<List<SupportMessageEntity>> =
        snapshotQueryFlow(
            db.collection(COL_SUPPORT_MESSAGES).whereEqualTo("userId", uid),
            "$COL_SUPPORT_MESSAGES?userId=$uid"
        ) { snap ->
            snap.documents.mapNotNull { SupportMessageEntity.fromSnapshot(it) }
                .sortedBy { it.createdAt }
        }

    fun observeReferrals(uid: String): Flow<List<ReferralEntity>> =
        snapshotQueryFlow(
            db.collection(COL_REFERRALS).whereEqualTo("referrerId", uid),
            "$COL_REFERRALS?referrerId=$uid"
        ) { snap ->
            snap.documents.mapNotNull { ReferralEntity.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }
        }

    fun observeLiveTicker(): Flow<List<LiveTickerEntity>> =
        snapshotQueryFlow(
            db.collection(COL_LIVE_TICKER)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20),
            COL_LIVE_TICKER
        ) { snap ->
            snap.documents.mapNotNull { LiveTickerEntity.fromSnapshot(it) }
        }

    fun observePublicConfig(): Flow<AdminVaultEntity> =
        snapshotDocumentFlow(
            db.collection(COL_APP_CONFIG).document(AdminVaultEntity.PUBLIC_CONFIG_ID),
            "$COL_APP_CONFIG/${AdminVaultEntity.PUBLIC_CONFIG_ID}"
        ) { snap ->
            AdminVaultEntity.fromPublicSnapshot(snap) ?: AdminVaultEntity()
        }

    fun observeAdminVault(): Flow<AdminVaultEntity> =
        snapshotDocumentFlow(
            db.collection(COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID),
            "$COL_ADMIN_CONFIG/${AdminVaultEntity.SINGLETON_ID}"
        ) { snap ->
            AdminVaultEntity.fromAdminSnapshot(snap) ?: AdminVaultEntity()
        }

    fun observePendingTransactions(): Flow<List<TransactionEntity>> =
        snapshotQueryFlow(
            db.collection(COL_TRANSACTIONS).whereEqualTo("status", TransactionEntity.STATUS_PENDING),
            "$COL_TRANSACTIONS?status=PENDING"
        ) { snap ->
            snap.documents.mapNotNull { TransactionEntity.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }
        }

    fun observeAllUsers(): Flow<List<UserAccountEntity>> =
        snapshotQueryFlow(
            db.collection(COL_USERS).orderBy("lastLoginAt", Query.Direction.DESCENDING),
            COL_USERS
        ) { snap ->
            snap.documents.mapNotNull { UserAccountEntity.fromSnapshot(it) }
        }

    fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        snapshotQueryFlow(
            db.collection(COL_TRANSACTIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100),
            COL_TRANSACTIONS
        ) { snap ->
            snap.documents.mapNotNull { TransactionEntity.fromSnapshot(it) }
        }

    private suspend fun getPublicOrAdminConfig(isAdmin: Boolean): AdminVaultEntity {
        if (isAdmin) {
            val adminRef = db.collection(COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID)
            val snap = try {
                adminRef.get().awaitTask()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FirestoreErrorHandler.handleFirestoreError(
                    e, OperationType.GET, "$COL_ADMIN_CONFIG/${AdminVaultEntity.SINGLETON_ID}", auth
                )
            }
            AdminVaultEntity.fromAdminSnapshot(snap)?.let { return it }
            val seed = AdminVaultEntity()
            adminRef.set(seed.toAdminMap()).awaitTask()
            db.collection(COL_APP_CONFIG).document(AdminVaultEntity.PUBLIC_CONFIG_ID)
                .set(seed.toPublicConfigMap()).awaitTask()
            return seed
        } else {
            return try {
                val pubSnap = db.collection(COL_APP_CONFIG)
                    .document(AdminVaultEntity.PUBLIC_CONFIG_ID)
                    .get()
                    .awaitTask()
                AdminVaultEntity.fromPublicSnapshot(pubSnap) ?: AdminVaultEntity()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AdminVaultEntity()
            }
        }
    }

    suspend fun syncSignedInUser(
        firebaseUser: FirebaseUser,
        referralCodeUsed: String = ""
    ): Result<UserAccountEntity> {
        val uid = firebaseUser.uid
        val cleanEmail = (firebaseUser.email ?: "").trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalStateException("Google account has no verified email address."))
        }
        val path = "$COL_USERS/$uid"
        return try {
            val userRef = db.collection(COL_USERS).document(uid)
            val snap = userRef.get().awaitTask()
            val isAdminUser = cleanEmail == AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL && firebaseUser.isEmailVerified
            if (isAdminUser) {
                getPublicOrAdminConfig(isAdmin = true)
            }

            val existing = UserAccountEntity.fromSnapshot(snap)
            val resultUser = if (existing == null) {
                val prefix = cleanEmail.substringBefore("@")
                    .replace(Regex("[^a-zA-Z0-9]"), "")
                    .uppercase()
                    .take(6)
                    .ifBlank { "PARTNER" }
                val generatedCode = "JP-$prefix-15"
                val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: cleanEmail.substringBefore("@")
                        .split(".", "_", "-")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                        .ifBlank { "Jio Partner" }

                val newUser = UserAccountEntity(
                    userId = uid,
                    email = cleanEmail,
                    fullName = displayName,
                    status = UserAccountEntity.STATUS_INACTIVE,
                    referralCode = generatedCode,
                    referredBy = referralCodeUsed.trim().uppercase(),
                    loginCount = 1,
                    isLoggedIn = true
                )
                userRef.set(newUser.toCreateMap()).awaitTask()

                db.collection(COL_ALERTS).add(
                    NotificationAlertEntity(
                        userId = uid,
                        userEmail = cleanEmail,
                        title = "Welcome to Jio Panal",
                        smsBody = "JIO PANAL ALERT: Welcome $cleanEmail! Activate your partner account (₹5,000) via vishalkumar90@upi to unlock 15% commission credits & RUN Panal.",
                        alertType = "SYSTEM"
                    ).toCreateMap()
                ).awaitTask()

                db.collection(COL_SUPPORT_MESSAGES).add(
                    SupportMessageEntity(
                        userId = uid,
                        userEmail = cleanEmail,
                        senderRole = "SUPPORT",
                        message = "Namaste $displayName! Welcome to Jio Panal 24/7 Support. Your account syncs live across devices. Ask anything about ₹5,000 Activation, 15% Commission, or Telegram (@Sainishaab9076)."
                    ).toCreateMap()
                ).awaitTask()

                newUser.copy(
                    lastLoginAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
            } else {
                val nextCount = existing.loginCount + 1
                val updates = hashMapOf<String, Any>(
                    "isLoggedIn" to true,
                    "loginCount" to nextCount,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existing.fullName.isBlank() && !firebaseUser.displayName.isNullOrBlank()) {
                    updates["fullName"] = firebaseUser.displayName!!.trim()
                }
                if (existing.referredBy.isBlank() && referralCodeUsed.isNotBlank()) {
                    updates["referredBy"] = referralCodeUsed.trim().uppercase()
                }
                userRef.update(updates).awaitTask()
                existing.copy(
                    isLoggedIn = true,
                    loginCount = nextCount,
                    lastLoginAt = System.currentTimeMillis()
                )
            }

            prefsRepo.setLoggedInEmail(cleanEmail)
            Result.success(resultUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path, auth)
            } catch (logged: Exception) {
                Result.failure(logged)
            }
        }
    }

    suspend fun logout(context: Context? = null) {
        val current = auth.currentUser
        if (current != null) {
            try {
                db.collection(COL_USERS).document(current.uid).update(
                    mapOf(
                        "isLoggedIn" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitTask()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort mark offline before clearing credentials
            }
        }
        prefsRepo.setLoggedInEmail(null)
        if (context != null) {
            GoogleAuthHelper.signOutGoogle(context)
        } else {
            auth.signOut()
        }
    }

    suspend fun setDarkMode(enabled: Boolean) = prefsRepo.setDarkMode(enabled)
    suspend fun setLanguageCode(code: String) = prefsRepo.setLanguageCode(code)
    suspend fun setBiometricEnabled(enabled: Boolean) = prefsRepo.setBiometricEnabled(enabled)
    suspend fun setSecurityPin(pin: String) = prefsRepo.setSecurityPin(pin)

    suspend fun updateProfile(
        uid: String,
        fullName: String,
        phone: String,
        city: String,
        businessName: String
    ) {
        val path = "$COL_USERS/$uid"
        try {
            db.collection(COL_USERS).document(uid).update(
                mapOf(
                    "fullName" to fullName.trim().take(100),
                    "phone" to phone.trim().take(25),
                    "city" to city.trim().take(80),
                    "businessName" to businessName.trim().take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, path, auth)
        }
    }

    suspend fun submitActivationOrDeposit(
        user: UserAccountEntity,
        amount: Double,
        utrNumber: String,
        paymentMethod: String,
        isActivation: Boolean
    ) {
        val uid = user.userId
        val email = user.email
        val config = getPublicOrAdminConfig(isAdmin = false)
        val commission = ((amount * (config.commissionRatePercent / 100.0)) * 100.0).roundToInt() / 100.0
        val cleanUtr = utrNumber.trim().uppercase()

        try {
            val tx = TransactionEntity(
                userId = uid,
                userEmail = email,
                type = if (isActivation) TransactionEntity.TYPE_ACTIVATION else TransactionEntity.TYPE_DEPOSIT,
                amount = amount,
                commissionAmount = commission,
                utrOrRef = cleanUtr,
                title = if (isActivation) "Account Activation Deposit" else "Gateway Deposit ($paymentMethod)",
                subtitle = "To ${config.merchantUpiId} • UTR $cleanUtr • Est. 15% Comm ${Formatters.formatInr(commission)}",
                status = TransactionEntity.STATUS_PENDING,
                paymentMethod = paymentMethod
            )
            db.collection(COL_TRANSACTIONS).add(tx.toCreateMap()).awaitTask()

            if (isActivation && user.status == UserAccountEntity.STATUS_INACTIVE) {
                db.collection(COL_USERS).document(uid).update(
                    mapOf(
                        "status" to UserAccountEntity.STATUS_PENDING,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitTask()
            }

            val smsText = "JIO PANAL DEPOSIT ALERT: ${Formatters.formatInr(amount)} submitted via $paymentMethod (UTR: $cleanUtr). Pending Admin approval."
            db.collection(COL_ALERTS).add(
                NotificationAlertEntity(
                    userId = uid,
                    userEmail = email,
                    title = "Deposit Submitted (${Formatters.formatInr(amount)})",
                    smsBody = smsText,
                    amount = amount,
                    alertType = "DEPOSIT"
                ).toCreateMap()
            ).awaitTask()

            db.collection(COL_LIVE_TICKER).add(
                LiveTickerEntity(
                    message = "${Formatters.maskEmail(email)} submitted ${Formatters.formatInr(amount)} via $paymentMethod",
                    highlightAmount = "Pending Verify",
                    isCredit = false
                ).toCreateMap()
            ).awaitTask()

            notificationHelper.sendLiveAlert(
                title = "Deposit Alert • ${Formatters.formatInr(amount)}",
                body = smsText
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, COL_TRANSACTIONS, auth)
        }
    }

    suspend fun addPayoutMethod(
        user: UserAccountEntity,
        methodType: String,
        label: String,
        upiId: String,
        bankName: String,
        accountNumber: String,
        ifscCode: String,
        holderName: String
    ) {
        val uid = user.userId
        try {
            val existingSnap = db.collection(COL_PAYOUT_METHODS)
                .whereEqualTo("userId", uid)
                .get()
                .awaitTask()

            val batch = db.batch()
            existingSnap.documents.forEach { doc ->
                if (doc.getBoolean("isDefault") == true) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "isDefault" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            val newDocRef = db.collection(COL_PAYOUT_METHODS).document()
            val entity = PayoutMethodEntity(
                id = newDocRef.id,
                userId = uid,
                userEmail = user.email,
                methodType = methodType,
                label = label.trim().ifBlank { methodType },
                upiId = upiId.trim(),
                bankName = bankName.trim(),
                accountNumber = accountNumber.trim(),
                ifscCode = ifscCode.trim().uppercase(),
                holderName = holderName.trim(),
                isDefault = true
            )
            batch.set(newDocRef, entity.toCreateMap())
            batch.commit().awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, COL_PAYOUT_METHODS, auth)
        }
    }

    suspend fun setDefaultPayoutMethod(uid: String, methodId: String) {
        try {
            val snap = db.collection(COL_PAYOUT_METHODS)
                .whereEqualTo("userId", uid)
                .get()
                .awaitTask()
            val batch = db.batch()
            snap.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "isDefault" to (doc.id == methodId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, COL_PAYOUT_METHODS, auth)
        }
    }

    suspend fun deletePayoutMethod(methodId: String) {
        try {
            db.collection(COL_PAYOUT_METHODS).document(methodId).delete().awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "$COL_PAYOUT_METHODS/$methodId", auth)
        }
    }

    suspend fun requestUserWithdrawal(
        user: UserAccountEntity,
        amount: Double,
        destinationLabel: String
    ): Result<Unit> {
        val uid = user.userId
        val email = user.email
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter a valid withdrawal amount"))
        }

        return try {
            val userSnap = db.collection(COL_USERS).document(uid).get().awaitTask()
            val latestUser = UserAccountEntity.fromSnapshot(userSnap)
                ?: return Result.failure(IllegalStateException("User profile not found"))

            val userTxs = db.collection(COL_TRANSACTIONS)
                .whereEqualTo("userId", uid)
                .get()
                .awaitTask()
            val pendingWithdrawals = userTxs.documents.mapNotNull { TransactionEntity.fromSnapshot(it) }
                .filter { it.type == TransactionEntity.TYPE_WITHDRAWAL && it.status == TransactionEntity.STATUS_PENDING }
                .sumOf { it.amount }

            val available = (latestUser.walletBalance - pendingWithdrawals).coerceAtLeast(0.0)
            if (amount > available) {
                return Result.failure(
                    IllegalArgumentException("Insufficient available balance (${Formatters.formatInr(available)} after pending withdrawals)")
                )
            }

            val ref = "WD${System.currentTimeMillis().toString().takeLast(8)}"
            db.collection(COL_TRANSACTIONS).add(
                TransactionEntity(
                    userId = uid,
                    userEmail = email,
                    type = TransactionEntity.TYPE_WITHDRAWAL,
                    amount = amount,
                    commissionAmount = 0.0,
                    utrOrRef = ref,
                    title = "Withdrawal Request",
                    subtitle = "To $destinationLabel • Ref $ref • Pending Admin Approval",
                    status = TransactionEntity.STATUS_PENDING,
                    paymentMethod = "PAYOUT"
                ).toCreateMap()
            ).awaitTask()

            val smsBody = "JIO PANAL WITHDRAWAL REQUEST: ${Formatters.formatInr(amount)} requested to $destinationLabel (Ref: $ref). Pending Admin approval."
            db.collection(COL_ALERTS).add(
                NotificationAlertEntity(
                    userId = uid,
                    userEmail = email,
                    title = "Withdrawal Request Submitted (${Formatters.formatInr(amount)})",
                    smsBody = smsBody,
                    amount = amount,
                    alertType = "DEPOSIT"
                ).toCreateMap()
            ).awaitTask()

            db.collection(COL_LIVE_TICKER).add(
                LiveTickerEntity(
                    message = "${Formatters.maskEmail(email)} requested Withdrawal",
                    highlightAmount = "${Formatters.formatInr(amount)} Pending",
                    isCredit = false
                ).toCreateMap()
            ).awaitTask()

            notificationHelper.sendLiveAlert("Withdrawal Request • ${Formatters.formatInr(amount)}", smsBody)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, COL_TRANSACTIONS, auth)
            } catch (logged: Exception) {
                Result.failure(logged)
            }
        }
    }

    suspend fun addReferral(user: UserAccountEntity, friendName: String, friendEmail: String) {
        val bonus = 750.0
        try {
            db.collection(COL_REFERRALS).add(
                ReferralEntity(
                    referrerId = user.userId,
                    referrerEmail = user.email,
                    friendName = friendName.trim(),
                    friendEmail = friendEmail.trim().lowercase(),
                    status = "PENDING",
                    commissionEarned = 0.0
                ).toCreateMap()
            ).awaitTask()

            db.collection(COL_ALERTS).add(
                NotificationAlertEntity(
                    userId = user.userId,
                    userEmail = user.email,
                    title = "Referral Invite Logged",
                    smsBody = "JIO PANAL REFERRAL: Invite sent to ${friendName.trim()}. Earn ${Formatters.formatInr(bonus)} (15%) once their ₹5,000 activation is approved!",
                    alertType = "SYSTEM"
                ).toCreateMap()
            ).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, COL_REFERRALS, auth)
        }
    }

    suspend fun sendSupportMessage(user: UserAccountEntity, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        try {
            db.collection(COL_SUPPORT_MESSAGES).add(
                SupportMessageEntity(
                    userId = user.userId,
                    userEmail = user.email,
                    senderRole = "USER",
                    message = clean
                ).toCreateMap()
            ).awaitTask()

            val lower = clean.lowercase()
            val autoReply = when {
                "5000" in lower || "activ" in lower || "upi" in lower ->
                    "Account Activation requires ₹5,000 via UPI (vishalkumar90@upi) or QR. After submitting your UTR, Admin approves it manually & credits your 15% bonus. Urgent help: @Sainishaab9076 on Telegram."
                "comm" in lower || "15%" in lower || "refer" in lower ->
                    "Jio Panal pays a flat 15% commission on approved deposits & referrals. Share your referral code from the Support/Referral tab!"
                "withdraw" in lower || "bank" in lower ->
                    "Add your UPI ID or Bank Account in Ledger -> Accounts, then submit a withdrawal request. Admin verifies and settles payouts directly."
                else ->
                    "Thank you! Your message is synced to Admin Control Desk. An Admin can reply directly here, or tap Telegram (@Sainishaab9076) for instant support."
            }
            db.collection(COL_SUPPORT_MESSAGES).add(
                SupportMessageEntity(
                    userId = user.userId,
                    userEmail = user.email,
                    senderRole = "SUPPORT",
                    message = autoReply
                ).toCreateMap()
            ).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, COL_SUPPORT_MESSAGES, auth)
        }
    }

    suspend fun markAlertsRead(uid: String) {
        try {
            val snap = db.collection(COL_ALERTS)
                .whereEqualTo("userId", uid)
                .get()
                .awaitTask()
            val unread = snap.documents.filter { it.getBoolean("isRead") == false }
            if (unread.isEmpty()) return
            val batch = db.batch()
            unread.forEach { doc -> batch.update(doc.reference, "isRead", true) }
            batch.commit().awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, COL_ALERTS, auth)
        }
    }

    // ---------------- ADMIN OPERATIONS ----------------

    suspend fun verifyAdminPasskey(passkey: String): Boolean {
        return try {
            val vault = getPublicOrAdminConfig(isAdmin = true)
            passkey.trim() == vault.adminPasskey
        } catch (e: CancellationException) {
            throw e
        } catch (_: FirebaseFirestoreException) {
            false
        }
    }

    suspend fun updateAdminPasskey(newPasskey: String) {
        val vault = getPublicOrAdminConfig(isAdmin = true)
        val updated = vault.copy(adminPasskey = newPasskey.trim())
        try {
            db.collection(COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID)
                .set(updated.toAdminMap())
                .awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, COL_ADMIN_CONFIG, auth)
        }
    }

    suspend fun approveTransaction(txId: String): Result<Unit> {
        return try {
            val adminConfigRef = db.collection(COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID)
            val txRef = db.collection(COL_TRANSACTIONS).document(txId)

            var txResult: TransactionEntity? = null
            var creditedUser: UserAccountEntity? = null
            var finalCommission = 0.0

            db.runTransaction { txn ->
                val txSnap = txn.get(txRef)
                val tx = TransactionEntity.fromSnapshot(txSnap)
                    ?: throw IllegalStateException("Transaction not found")
                if (tx.status != TransactionEntity.STATUS_PENDING) {
                    throw IllegalStateException("Transaction already processed")
                }

                val userRef = db.collection(COL_USERS).document(tx.userId)
                val userSnap = txn.get(userRef)
                val user = UserAccountEntity.fromSnapshot(userSnap)
                    ?: throw IllegalStateException("User account not found")

                val adminSnap = txn.get(adminConfigRef)
                val vault = AdminVaultEntity.fromAdminSnapshot(adminSnap) ?: AdminVaultEntity()

                txResult = tx

                if (tx.type == TransactionEntity.TYPE_WITHDRAWAL) {
                    if (tx.amount > user.walletBalance) {
                        throw IllegalStateException("User wallet balance (${Formatters.formatInr(user.walletBalance)}) is insufficient for this withdrawal")
                    }
                    val remaining = user.walletBalance - tx.amount
                    txn.update(
                        userRef,
                        mapOf(
                            "walletBalance" to remaining,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    txn.update(
                        txRef,
                        mapOf(
                            "status" to TransactionEntity.STATUS_SUCCESS,
                            "title" to "Withdrawal Approved & Settled",
                            "subtitle" to tx.subtitle.replace("Pending Admin Approval", "Approved by Admin"),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    creditedUser = user.copy(walletBalance = remaining)
                } else {
                    val commission = ((tx.amount * (vault.commissionRatePercent / 100.0)) * 100.0).roundToInt() / 100.0
                    finalCommission = commission

                    val newStatus = if (tx.type == TransactionEntity.TYPE_ACTIVATION || tx.amount >= vault.activationAmount) {
                        UserAccountEntity.STATUS_ACTIVE
                    } else {
                        user.status
                    }
                    val newBalance = user.walletBalance + tx.amount + commission
                    val newCommEarned = user.commissionEarned + commission
                    val newTotalDep = user.totalDeposited + tx.amount

                    txn.update(
                        txRef,
                        mapOf(
                            "status" to TransactionEntity.STATUS_SUCCESS,
                            "commissionAmount" to commission,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    txn.update(
                        userRef,
                        mapOf(
                            "status" to newStatus,
                            "walletBalance" to newBalance,
                            "commissionEarned" to newCommEarned,
                            "totalDeposited" to newTotalDep,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    txn.set(
                        adminConfigRef,
                        vault.copy(commissionPoolBalance = vault.commissionPoolBalance + commission).toAdminMap()
                    )

                    val commTxRef = db.collection(COL_TRANSACTIONS).document()
                    txn.set(
                        commTxRef,
                        TransactionEntity(
                            userId = tx.userId,
                            userEmail = tx.userEmail,
                            type = TransactionEntity.TYPE_COMMISSION,
                            amount = commission,
                            commissionAmount = commission,
                            utrOrRef = "COMM-${tx.utrOrRef}",
                            title = "15% Commission Credited",
                            subtitle = "15% bonus on ${Formatters.formatInr(tx.amount)} approved deposit",
                            status = TransactionEntity.STATUS_SUCCESS,
                            paymentMethod = "SYSTEM"
                        ).toCreateMap()
                    )
                    creditedUser = user.copy(status = newStatus, walletBalance = newBalance)
                }
            }.awaitTask()

            val tx = txResult ?: return Result.success(Unit)
            val updatedUser = creditedUser ?: return Result.success(Unit)

            if (tx.type == TransactionEntity.TYPE_WITHDRAWAL) {
                val smsBody = "JIO PANAL PAYOUT APPROVED: Withdrawal of ${Formatters.formatInr(tx.amount)} (Ref: ${tx.utrOrRef}) approved by Admin."
                db.collection(COL_ALERTS).add(
                    NotificationAlertEntity(
                        userId = tx.userId,
                        userEmail = tx.userEmail,
                        title = "Withdrawal Approved: ${Formatters.formatInr(tx.amount)}",
                        smsBody = smsBody,
                        amount = tx.amount,
                        alertType = "CREDIT"
                    ).toCreateMap()
                ).awaitTask()
                db.collection(COL_LIVE_TICKER).add(
                    LiveTickerEntity(
                        message = "${Formatters.maskEmail(tx.userEmail)} Withdrawal Settled",
                        highlightAmount = "${Formatters.formatInr(tx.amount)} Paid",
                        isCredit = false
                    ).toCreateMap()
                ).awaitTask()
                notificationHelper.sendLiveAlert("Withdrawal Approved • ${Formatters.formatInr(tx.amount)}", smsBody)
            } else {
                val smsBody = "CREDIT ALERT: Your Jio Panal A/C ${Formatters.maskEmail(tx.userEmail)} is CREDITED with ${Formatters.formatInr(tx.amount)} + ${Formatters.formatInr(finalCommission)} (15% Commission). Status: ${updatedUser.status}. Avl Bal: ${Formatters.formatInr(updatedUser.walletBalance)}."
                db.collection(COL_ALERTS).add(
                    NotificationAlertEntity(
                        userId = tx.userId,
                        userEmail = tx.userEmail,
                        title = "Credit Alert: +${Formatters.formatInr(tx.amount + finalCommission)}",
                        smsBody = smsBody,
                        amount = tx.amount + finalCommission,
                        alertType = "CREDIT"
                    ).toCreateMap()
                ).awaitTask()
                db.collection(COL_LIVE_TICKER).add(
                    LiveTickerEntity(
                        message = "${Formatters.maskEmail(tx.userEmail)} ${Formatters.formatInr(tx.amount)} Approved",
                        highlightAmount = "+${Formatters.formatInr(finalCommission)} (15%) Credited",
                        isCredit = true
                    ).toCreateMap()
                ).awaitTask()
                notificationHelper.sendLiveAlert("Credit Approved • +${Formatters.formatInr(tx.amount + finalCommission)}", smsBody)
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "$COL_TRANSACTIONS/$txId", auth)
            } catch (logged: Exception) {
                Result.failure(logged)
            }
        }
    }

    suspend fun rejectTransaction(txId: String): Result<Unit> {
        return try {
            val txRef = db.collection(COL_TRANSACTIONS).document(txId)
            val txSnap = txRef.get().awaitTask()
            val tx = TransactionEntity.fromSnapshot(txSnap)
                ?: return Result.failure(IllegalStateException("Transaction not found"))
            if (tx.status != TransactionEntity.STATUS_PENDING) {
                return Result.success(Unit)
            }

            txRef.update(
                mapOf(
                    "status" to TransactionEntity.STATUS_REJECTED,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()

            if (tx.type == TransactionEntity.TYPE_WITHDRAWAL) {
                val smsBody = "JIO PANAL WITHDRAWAL DECLINED: Withdrawal request of ${Formatters.formatInr(tx.amount)} (Ref: ${tx.utrOrRef}) was declined by Admin. Available wallet balance remains intact."
                db.collection(COL_ALERTS).add(
                    NotificationAlertEntity(
                        userId = tx.userId,
                        userEmail = tx.userEmail,
                        title = "Withdrawal Declined",
                        smsBody = smsBody,
                        amount = tx.amount,
                        alertType = "SECURITY"
                    ).toCreateMap()
                ).awaitTask()
                notificationHelper.sendLiveAlert("Withdrawal Declined", smsBody)
                return Result.success(Unit)
            }

            if (tx.type == TransactionEntity.TYPE_ACTIVATION) {
                val userRef = db.collection(COL_USERS).document(tx.userId)
                val userSnap = userRef.get().awaitTask()
                val user = UserAccountEntity.fromSnapshot(userSnap)
                if (user != null && user.status == UserAccountEntity.STATUS_PENDING) {
                    userRef.update(
                        mapOf(
                            "status" to UserAccountEntity.STATUS_INACTIVE,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).awaitTask()
                }
            }

            val smsBody = "JIO PANAL ALERT: Your request of ${Formatters.formatInr(tx.amount)} (UTR: ${tx.utrOrRef}) was declined by Admin. Please check UTR or contact @Sainishaab9076."
            db.collection(COL_ALERTS).add(
                NotificationAlertEntity(
                    userId = tx.userId,
                    userEmail = tx.userEmail,
                    title = "Request Declined (UTR ${tx.utrOrRef})",
                    smsBody = smsBody,
                    amount = tx.amount,
                    alertType = "SECURITY"
                ).toCreateMap()
            ).awaitTask()
            notificationHelper.sendLiveAlert("Transaction Declined", smsBody)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "$COL_TRANSACTIONS/$txId", auth)
            } catch (logged: Exception) {
                Result.failure(logged)
            }
        }
    }

    suspend fun setUserStatusByAdmin(userId: String, newStatus: String) {
        try {
            val userRef = db.collection(COL_USERS).document(userId)
            val snap = userRef.get().awaitTask()
            val user = UserAccountEntity.fromSnapshot(snap) ?: return
            userRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()
            val smsBody = "JIO PANAL ADMIN UPDATE: Your account status is now $newStatus."
            db.collection(COL_ALERTS).add(
                NotificationAlertEntity(
                    userId = userId,
                    userEmail = user.email,
                    title = "Account Status: $newStatus",
                    smsBody = smsBody,
                    alertType = "SYSTEM"
                ).toCreateMap()
            ).awaitTask()
            notificationHelper.sendLiveAlert("Account Status Updated", smsBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "$COL_USERS/$userId", auth)
        }
    }

    suspend fun withdrawAdminCommission(
        amount: Double,
        destination: String
    ): Result<Unit> {
        val currentAdmin = auth.currentUser ?: return Result.failure(IllegalStateException("Admin sign-in required"))
        return try {
            val adminRef = db.collection(COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID)
            var remainingPool = 0.0

            db.runTransaction { txn ->
                val adminSnap = txn.get(adminRef)
                val vault = AdminVaultEntity.fromAdminSnapshot(adminSnap) ?: AdminVaultEntity()
                if (amount <= 0.0 || amount > vault.commissionPoolBalance) {
                    throw IllegalArgumentException(
                        "Amount exceeds Admin commission pool (${Formatters.formatInr(vault.commissionPoolBalance)})"
                    )
                }
                remainingPool = vault.commissionPoolBalance - amount
                txn.set(adminRef, vault.copy(commissionPoolBalance = remainingPool).toAdminMap())

                val ref = "ADMWD${System.currentTimeMillis().toString().takeLast(6)}"
                val txDoc = db.collection(COL_TRANSACTIONS).document()
                txn.set(
                    txDoc,
                    TransactionEntity(
                        userId = currentAdmin.uid,
                        userEmail = currentAdmin.email?.lowercase() ?: AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL,
                        type = TransactionEntity.TYPE_ADMIN_WITHDRAWAL,
                        amount = amount,
                        commissionAmount = 0.0,
                        utrOrRef = ref,
                        title = "Admin Commission Withdrawal",
                        subtitle = "Settled to $destination • Pool Bal ${Formatters.formatInr(remainingPool)}",
                        status = TransactionEntity.STATUS_SUCCESS,
                        paymentMethod = "ADMIN_PAYOUT"
                    ).toCreateMap()
                )
            }.awaitTask()

            db.collection(COL_LIVE_TICKER).add(
                LiveTickerEntity(
                    message = "Admin Commission Payout Settled to $destination",
                    highlightAmount = "${Formatters.formatInr(amount)} Withdrawn",
                    isCredit = true
                ).toCreateMap()
            ).awaitTask()
            notificationHelper.sendLiveAlert(
                title = "Admin Withdrawal • ${Formatters.formatInr(amount)}",
                body = "Admin commission withdrawal of ${Formatters.formatInr(amount)} settled to $destination. Remaining Pool: ${Formatters.formatInr(remainingPool)}."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, COL_ADMIN_CONFIG, auth)
            } catch (logged: Exception) {
                Result.failure(logged)
            }
        }
    }

    suspend fun creditUserByAdmin(
        targetEmail: String,
        amount: Double,
        includeCommission: Boolean = true,
        deductFromAdminPool: Boolean = false,
        note: String = ""
    ): Result<Unit> {
        val cleanEmail = targetEmail.trim().lowercase()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Enter a valid user Gmail address"))
        }
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter a valid amount greater than ₹0"))
        }

        return try {
            val userQuery = db.collection(COL_USERS)
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .awaitTask()
            val targetDoc = userQuery.documents.firstOrNull()
                ?: return Result.failure(
                    IllegalStateException("User $cleanEmail hasn't signed in with Google yet. Ask them to open the app and sign in first.")
                )

            val adminRef = db.collection(COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID)
            var totalAdded = 0.0
            var newBalance = 0.0
            var commission = 0.0

            db.runTransaction { txn ->
                val userSnap = txn.get(targetDoc.reference)
                val user = UserAccountEntity.fromSnapshot(userSnap)
                    ?: throw IllegalStateException("Target user record missing")
                val adminSnap = txn.get(adminRef)
                val vault = AdminVaultEntity.fromAdminSnapshot(adminSnap) ?: AdminVaultEntity()

                commission = if (includeCommission) {
                    ((amount * (vault.commissionRatePercent / 100.0)) * 100.0).roundToInt() / 100.0
                } else {
                    0.0
                }
                totalAdded = amount + commission
                if (deductFromAdminPool && totalAdded > vault.commissionPoolBalance) {
                    throw IllegalArgumentException(
                        "Insufficient Admin Pool balance (${Formatters.formatInr(vault.commissionPoolBalance)})"
                    )
                }

                val newStatus = if (amount >= vault.activationAmount && user.status != UserAccountEntity.STATUS_SUSPENDED) {
                    UserAccountEntity.STATUS_ACTIVE
                } else {
                    user.status
                }
                newBalance = user.walletBalance + totalAdded
                txn.update(
                    targetDoc.reference,
                    mapOf(
                        "status" to newStatus,
                        "walletBalance" to newBalance,
                        "commissionEarned" to user.commissionEarned + commission,
                        "totalDeposited" to user.totalDeposited + amount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (deductFromAdminPool) {
                    txn.set(
                        adminRef,
                        vault.copy(commissionPoolBalance = vault.commissionPoolBalance - totalAdded).toAdminMap()
                    )
                }

                val ref = "ADMCR${System.currentTimeMillis().toString().takeLast(6)}"
                val reason = note.trim().ifBlank { "Added by Admin Control Vault" }
                val depositDoc = db.collection(COL_TRANSACTIONS).document()
                txn.set(
                    depositDoc,
                    TransactionEntity(
                        userId = user.userId,
                        userEmail = cleanEmail,
                        type = TransactionEntity.TYPE_DEPOSIT,
                        amount = amount,
                        commissionAmount = commission,
                        utrOrRef = ref,
                        title = "Admin Wallet Credit",
                        subtitle = "$reason • Ref $ref" + if (commission > 0.0) " • +15% Bonus" else "",
                        status = TransactionEntity.STATUS_SUCCESS,
                        paymentMethod = "ADMIN_CREDIT"
                    ).toCreateMap()
                )

                if (commission > 0.0) {
                    val commDoc = db.collection(COL_TRANSACTIONS).document()
                    txn.set(
                        commDoc,
                        TransactionEntity(
                            userId = user.userId,
                            userEmail = cleanEmail,
                            type = TransactionEntity.TYPE_COMMISSION,
                            amount = commission,
                            commissionAmount = commission,
                            utrOrRef = "COMM-$ref",
                            title = "15% Commission Credited",
                            subtitle = "15% bonus on Admin credit of ${Formatters.formatInr(amount)}",
                            status = TransactionEntity.STATUS_SUCCESS,
                            paymentMethod = "SYSTEM"
                        ).toCreateMap()
                    )
                }
            }.awaitTask()

            val smsBody = buildString {
                append("CREDIT SMS ALERT: Your Jio Panal A/C ${Formatters.maskEmail(cleanEmail)} received ${Formatters.formatInr(amount)}")
                if (commission > 0.0) {
                    append(" + ${Formatters.formatInr(commission)} (15% Comm)")
                }
                append(" from Admin Vault. Avl Bal: ${Formatters.formatInr(newBalance)}.")
            }
            db.collection(COL_ALERTS).add(
                NotificationAlertEntity(
                    userId = targetDoc.id,
                    userEmail = cleanEmail,
                    title = "Admin Credit: +${Formatters.formatInr(totalAdded)}",
                    smsBody = smsBody,
                    amount = totalAdded,
                    alertType = "CREDIT"
                ).toCreateMap()
            ).awaitTask()

            db.collection(COL_LIVE_TICKER).add(
                LiveTickerEntity(
                    message = "${Formatters.maskEmail(cleanEmail)} Credited by Admin",
                    highlightAmount = "+${Formatters.formatInr(totalAdded)}",
                    isCredit = true
                ).toCreateMap()
            ).awaitTask()

            notificationHelper.sendLiveAlert(
                title = "Live Credit SMS • +${Formatters.formatInr(totalAdded)}",
                body = smsBody
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, COL_USERS, auth)
            } catch (logged: Exception) {
                Result.failure(logged)
            }
        }
    }

    suspend fun sendAdminReplyToUser(userId: String, userEmail: String, message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        try {
            db.collection(COL_SUPPORT_MESSAGES).add(
                SupportMessageEntity(
                    userId = userId,
                    userEmail = userEmail,
                    senderRole = "SUPPORT",
                    message = "[Admin Desk] $clean"
                ).toCreateMap()
            ).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, COL_SUPPORT_MESSAGES, auth)
        }
    }
}
