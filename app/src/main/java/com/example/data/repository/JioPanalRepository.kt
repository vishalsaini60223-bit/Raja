package com.example.data.repository

import android.content.Context
import com.example.data.local.JioPanalDao
import com.example.data.local.JioPanalDatabase
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
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import kotlin.math.roundToInt

class JioPanalRepository(
    private val dao: JioPanalDao,
    private val prefsRepo: UserPreferencesRepository,
    private val notificationHelper: NotificationHelper
) {
    constructor(context: Context) : this(
        dao = JioPanalDatabase.getInstance(context).dao(),
        prefsRepo = UserPreferencesRepository(context.applicationContext),
        notificationHelper = NotificationHelper(context.applicationContext)
    )

    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow
    val liveTickerFlow: Flow<List<LiveTickerEntity>> = dao.observeLiveTicker()
    val adminVaultFlow: Flow<AdminVaultEntity?> = dao.observeAdminVault()
    val pendingTransactionsFlow: Flow<List<TransactionEntity>> = dao.observePendingTransactions()
    val allTransactionsFlow: Flow<List<TransactionEntity>> = dao.observeAllTransactions()
    val allUsersFlow: Flow<List<UserAccountEntity>> = dao.observeAllUsers()
    val allSupportMessagesFlow: Flow<List<SupportMessageEntity>> = dao.observeAllSupportMessages()

    fun observeUser(email: String): Flow<UserAccountEntity?> = dao.observeUser(email)
    fun observeTransactions(email: String): Flow<List<TransactionEntity>> = dao.observeTransactions(email)
    fun observePayoutMethods(email: String): Flow<List<PayoutMethodEntity>> = dao.observePayoutMethods(email)
    fun observeAlerts(email: String): Flow<List<NotificationAlertEntity>> = dao.observeAlerts(email)
    fun observeSupportMessages(email: String): Flow<List<SupportMessageEntity>> = dao.observeSupportMessages(email)
    fun observeReferrals(email: String): Flow<List<ReferralEntity>> = dao.observeReferrals(email)

    suspend fun ensureInitialized() {
        if (dao.getAdminVault() == null) {
            JioPanalDatabase.seedInitialState(dao)
        }
    }

    fun generateSixDigitOtp(email: String): String {
        val random = SecureRandom()
        val otp = (100000 + random.nextInt(900000)).toString()
        notificationHelper.sendLiveAlert(
            title = "Jio Panal Gmail Security OTP",
            body = "Your verification OTP for $email is $otp. Valid for 5 minutes. Do not share."
        )
        return otp
    }

    suspend fun completeGmailLogin(email: String, referralCodeUsed: String = ""): UserAccountEntity {
        ensureInitialized()
        val cleanEmail = email.trim().lowercase()
        var user = dao.getUser(cleanEmail)
        if (user == null) {
            val prefix = cleanEmail.substringBefore("@")
                .replace(Regex("[^a-zA-Z0-9]"), "")
                .uppercase()
                .take(6)
                .ifBlank { "PARTNER" }
            val generatedCode = "JP-$prefix-15"
            val displayName = cleanEmail.substringBefore("@")
                .split(".", "_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
                .ifBlank { "Jio Partner" }

            user = UserAccountEntity(
                email = cleanEmail,
                fullName = displayName,
                status = UserAccountEntity.STATUS_INACTIVE,
                referralCode = generatedCode,
                referredBy = referralCodeUsed.trim().uppercase()
            )
            dao.upsertUser(user)

            dao.insertAlert(
                NotificationAlertEntity(
                    userEmail = cleanEmail,
                    title = "Welcome to Jio Panal",
                    smsBody = "JIO PANAL SMS ALERT: Welcome $cleanEmail! Activate your partner account (₹5,000) via vishalkumar90@upi to unlock 15% commission credits.",
                    alertType = "SYSTEM"
                )
            )

            dao.insertSupportMessage(
                SupportMessageEntity(
                    userEmail = cleanEmail,
                    senderRole = "SUPPORT",
                    message = "Namaste $displayName! Welcome to Jio Panal 24/7 Support. Ask anything about ₹5,000 Account Activation, 15% Commission, or Telegram (@Sainishaab9076)."
                )
            )
        }
        prefsRepo.setLoggedInEmail(cleanEmail)
        return user
    }

    suspend fun logout() {
        prefsRepo.setLoggedInEmail(null)
    }

    suspend fun setDarkMode(enabled: Boolean) = prefsRepo.setDarkMode(enabled)
    suspend fun setLanguageCode(code: String) = prefsRepo.setLanguageCode(code)
    suspend fun setBiometricEnabled(enabled: Boolean) = prefsRepo.setBiometricEnabled(enabled)
    suspend fun setSecurityPin(pin: String) = prefsRepo.setSecurityPin(pin)

    suspend fun updateProfile(
        email: String,
        fullName: String,
        phone: String,
        city: String,
        businessName: String
    ) {
        val current = dao.getUser(email) ?: return
        dao.updateUser(
            current.copy(
                fullName = fullName.trim(),
                phone = phone.trim(),
                city = city.trim(),
                businessName = businessName.trim()
            )
        )
    }

    suspend fun submitActivationOrDeposit(
        email: String,
        amount: Double,
        utrNumber: String,
        paymentMethod: String,
        isActivation: Boolean
    ) {
        ensureInitialized()
        val user = dao.getUser(email) ?: return
        val vault = dao.getAdminVault() ?: AdminVaultEntity()
        val commission = ((amount * (vault.commissionRatePercent / 100.0)) * 100.0).roundToInt() / 100.0
        val cleanUtr = utrNumber.trim().uppercase()

        val tx = TransactionEntity(
            userEmail = email,
            type = if (isActivation) TransactionEntity.TYPE_ACTIVATION else TransactionEntity.TYPE_DEPOSIT,
            amount = amount,
            commissionAmount = commission,
            utrOrRef = cleanUtr,
            title = if (isActivation) "Account Activation Deposit" else "Gateway Deposit ($paymentMethod)",
            subtitle = "To ${vault.merchantUpiId} • UTR $cleanUtr • Est. 15% Comm ${Formatters.formatInr(commission)}",
            status = TransactionEntity.STATUS_PENDING,
            paymentMethod = paymentMethod
        )
        dao.insertTransaction(tx)

        if (isActivation && user.status != UserAccountEntity.STATUS_ACTIVE) {
            dao.updateUser(user.copy(status = UserAccountEntity.STATUS_PENDING))
        }

        val smsText = "JIO PANAL DEPOSIT ALERT: ${Formatters.formatInr(amount)} submitted via $paymentMethod (UTR: $cleanUtr). Pending Admin approval."
        dao.insertAlert(
            NotificationAlertEntity(
                userEmail = email,
                title = "Deposit Submitted (${Formatters.formatInr(amount)})",
                smsBody = smsText,
                amount = amount,
                alertType = "DEPOSIT"
            )
        )
        dao.insertTicker(
            LiveTickerEntity(
                message = "${Formatters.maskEmail(email)} submitted ${Formatters.formatInr(amount)} via $paymentMethod",
                highlightAmount = "Pending Verify",
                isCredit = false
            )
        )
        notificationHelper.sendLiveAlert(
            title = "Deposit Alert • ${Formatters.formatInr(amount)}",
            body = smsText
        )
    }

    suspend fun addPayoutMethod(
        email: String,
        methodType: String,
        label: String,
        upiId: String,
        bankName: String,
        accountNumber: String,
        ifscCode: String,
        holderName: String
    ) {
        val entity = PayoutMethodEntity(
            userEmail = email,
            methodType = methodType,
            label = label.trim(),
            upiId = upiId.trim(),
            bankName = bankName.trim(),
            accountNumber = accountNumber.trim(),
            ifscCode = ifscCode.trim().uppercase(),
            holderName = holderName.trim(),
            isDefault = false
        )
        val id = dao.insertPayoutMethod(entity)
        dao.setDefaultPayoutMethod(email, id)
    }

    suspend fun setDefaultPayoutMethod(email: String, methodId: Long) {
        dao.setDefaultPayoutMethod(email, methodId)
    }

    suspend fun deletePayoutMethod(methodId: Long) {
        dao.deletePayoutMethod(methodId)
    }

    suspend fun requestUserWithdrawal(
        email: String,
        amount: Double,
        destinationLabel: String
    ): Result<Unit> {
        val user = dao.getUser(email) ?: return Result.failure(IllegalStateException("User not found"))
        if (amount <= 0.0 || amount > user.walletBalance) {
            return Result.failure(IllegalArgumentException("Insufficient wallet balance"))
        }
        val ref = "WD${System.currentTimeMillis().toString().takeLast(8)}"
        dao.updateUser(user.copy(walletBalance = user.walletBalance - amount))
        dao.insertTransaction(
            TransactionEntity(
                userEmail = email,
                type = TransactionEntity.TYPE_WITHDRAWAL,
                amount = amount,
                commissionAmount = 0.0,
                utrOrRef = ref,
                title = "Wallet Withdrawal",
                subtitle = "To $destinationLabel • Ref $ref",
                status = TransactionEntity.STATUS_SUCCESS,
                paymentMethod = "PAYOUT"
            )
        )
        val smsBody = "JIO PANAL DEBIT/PAYOUT SMS: ${Formatters.formatInr(amount)} transferred to $destinationLabel. Ref: $ref. Avl Bal: ${Formatters.formatInr(user.walletBalance - amount)}."
        dao.insertAlert(
            NotificationAlertEntity(
                userEmail = email,
                title = "Withdrawal Processed (${Formatters.formatInr(amount)})",
                smsBody = smsBody,
                amount = amount,
                alertType = "CREDIT"
            )
        )
        notificationHelper.sendLiveAlert("Payout Sent • ${Formatters.formatInr(amount)}", smsBody)
        return Result.success(Unit)
    }

    suspend fun addReferral(email: String, friendName: String, friendEmail: String) {
        val user = dao.getUser(email) ?: return
        val bonus = 750.0 // 15% of ₹5,000 activation
        dao.insertReferral(
            ReferralEntity(
                referrerEmail = email,
                friendName = friendName.trim(),
                friendEmail = friendEmail.trim().lowercase(),
                status = "PENDING",
                commissionEarned = 0.0
            )
        )
        dao.insertAlert(
            NotificationAlertEntity(
                userEmail = email,
                title = "Referral Invite Logged",
                smsBody = "JIO PANAL REFERRAL: Invite sent to ${friendName.trim()}. Earn ${Formatters.formatInr(bonus)} (15%) once their ₹5,000 activation is approved!",
                alertType = "SYSTEM"
            )
        )
        // Ensure code exists
        if (user.referralCode.isBlank()) {
            dao.updateUser(user.copy(referralCode = "JP-${email.substringBefore("@").uppercase().take(6)}-15"))
        }
    }

    suspend fun sendSupportMessage(email: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        dao.insertSupportMessage(
            SupportMessageEntity(
                userEmail = email,
                senderRole = "USER",
                message = clean
            )
        )
        val lower = clean.lowercase()
        val autoReply = when {
            "5000" in lower || "activ" in lower || "upi" in lower ->
                "Account Activation requires ₹5,000 via UPI (vishalkumar90@upi) or QR. After submitting your UTR, Admin approves it manually & credits your 15% bonus. Need urgent help? Message @Sainishaab9076 on Telegram."
            "comm" in lower || "15%" in lower || "refer" in lower ->
                "Jio Panal pays a flat 15% commission on approved deposits & referrals. Share your referral code from the Support/Referral tab!"
            "withdraw" in lower || "bank" in lower ->
                "Add your UPI ID or Bank Account in Ledger -> Accounts, then withdraw anytime. Admin can also settle commission payouts directly."
            else ->
                "Thank you! Your ticket has been received by our 24/7 Jio Panal desk. An Admin can reply directly here, or tap the Telegram button (@Sainishaab9076) for instant support."
        }
        dao.insertSupportMessage(
            SupportMessageEntity(
                userEmail = email,
                senderRole = "SUPPORT",
                message = autoReply
            )
        )
    }

    suspend fun markAlertsRead(email: String) {
        dao.markAllAlertsRead(email)
    }

    // ---------------- ADMIN OPERATIONS ----------------

    suspend fun verifyAdminPasskey(passkey: String): Boolean {
        ensureInitialized()
        val vault = dao.getAdminVault() ?: AdminVaultEntity()
        return passkey.trim() == vault.adminPasskey
    }

    suspend fun updateAdminPasskey(newPasskey: String) {
        val vault = dao.getAdminVault() ?: AdminVaultEntity()
        dao.upsertAdminVault(vault.copy(adminPasskey = newPasskey.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun approveTransaction(txId: Long) {
        val tx = dao.getTransactionById(txId) ?: return
        if (tx.status == TransactionEntity.STATUS_SUCCESS) return

        val vault = dao.getAdminVault() ?: AdminVaultEntity()
        val commission = ((tx.amount * (vault.commissionRatePercent / 100.0)) * 100.0).roundToInt() / 100.0

        dao.updateTransaction(
            tx.copy(
                status = TransactionEntity.STATUS_SUCCESS,
                commissionAmount = commission
            )
        )

        val user = dao.getUser(tx.userEmail)
        if (user != null) {
            val newStatus = if (tx.type == TransactionEntity.TYPE_ACTIVATION || tx.amount >= vault.activationAmount) {
                UserAccountEntity.STATUS_ACTIVE
            } else {
                user.status
            }
            val updatedUser = user.copy(
                status = newStatus,
                walletBalance = user.walletBalance + tx.amount + commission,
                commissionEarned = user.commissionEarned + commission,
                totalDeposited = user.totalDeposited + tx.amount
            )
            dao.updateUser(updatedUser)

            // Also record 15% Commission Credit transaction
            dao.insertTransaction(
                TransactionEntity(
                    userEmail = tx.userEmail,
                    type = TransactionEntity.TYPE_COMMISSION,
                    amount = commission,
                    commissionAmount = commission,
                    utrOrRef = "COMM-${tx.utrOrRef}",
                    title = "15% Commission Credited",
                    subtitle = "15% bonus on ${Formatters.formatInr(tx.amount)} approved deposit",
                    status = TransactionEntity.STATUS_SUCCESS,
                    paymentMethod = "SYSTEM"
                )
            )

            // Credit Admin pool with platform commission tracking if needed (or keep Admin's 200,000+ growing)
            dao.upsertAdminVault(
                vault.copy(
                    commissionPoolBalance = vault.commissionPoolBalance + commission,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val smsBody = "CREDIT SMS ALERT: Your Jio Panal A/C ${Formatters.maskEmail(tx.userEmail)} is CREDITED with ${Formatters.formatInr(tx.amount)} + ${Formatters.formatInr(commission)} (15% Commission). Status: ACTIVE. Avl Bal: ${Formatters.formatInr(updatedUser.walletBalance)}."
            dao.insertAlert(
                NotificationAlertEntity(
                    userEmail = tx.userEmail,
                    title = "Credit Alert: +${Formatters.formatInr(tx.amount + commission)}",
                    smsBody = smsBody,
                    amount = tx.amount + commission,
                    alertType = "CREDIT"
                )
            )
            dao.insertTicker(
                LiveTickerEntity(
                    message = "${Formatters.maskEmail(tx.userEmail)} ${Formatters.formatInr(tx.amount)} Approved",
                    highlightAmount = "+${Formatters.formatInr(commission)} (15%) Credited",
                    isCredit = true
                )
            )
            notificationHelper.sendLiveAlert(
                title = "Live Credit SMS • +${Formatters.formatInr(tx.amount + commission)}",
                body = smsBody
            )
        }
    }

    suspend fun rejectTransaction(txId: Long) {
        val tx = dao.getTransactionById(txId) ?: return
        if (tx.status != TransactionEntity.STATUS_PENDING) return
        dao.updateTransaction(tx.copy(status = TransactionEntity.STATUS_REJECTED))

        val user = dao.getUser(tx.userEmail)
        if (user != null && tx.type == TransactionEntity.TYPE_ACTIVATION && user.status == UserAccountEntity.STATUS_PENDING) {
            dao.updateUser(user.copy(status = UserAccountEntity.STATUS_INACTIVE))
        }

        val smsBody = "JIO PANAL ALERT: Your request of ${Formatters.formatInr(tx.amount)} (UTR: ${tx.utrOrRef}) was declined by Admin. Please check UTR or contact @Sainishaab9076."
        dao.insertAlert(
            NotificationAlertEntity(
                userEmail = tx.userEmail,
                title = "Request Declined (UTR ${tx.utrOrRef})",
                smsBody = smsBody,
                amount = tx.amount,
                alertType = "SECURITY"
            )
        )
        notificationHelper.sendLiveAlert("Transaction Declined", smsBody)
    }

    suspend fun setUserStatusByAdmin(email: String, newStatus: String) {
        val user = dao.getUser(email) ?: return
        dao.updateUser(user.copy(status = newStatus))
        val smsBody = "JIO PANAL ADMIN UPDATE: Your account status is now $newStatus."
        dao.insertAlert(
            NotificationAlertEntity(
                userEmail = email,
                title = "Account Status: $newStatus",
                smsBody = smsBody,
                alertType = "SYSTEM"
            )
        )
        notificationHelper.sendLiveAlert("Account Status Updated", smsBody)
    }

    suspend fun withdrawAdminCommission(
        amount: Double,
        destination: String,
        activeUserEmail: String?
    ): Result<Unit> {
        ensureInitialized()
        val vault = dao.getAdminVault() ?: AdminVaultEntity()
        if (amount <= 0.0 || amount > vault.commissionPoolBalance) {
            return Result.failure(IllegalArgumentException("Amount exceeds Admin commission pool (${Formatters.formatInr(vault.commissionPoolBalance)})"))
        }
        val newBalance = vault.commissionPoolBalance - amount
        dao.upsertAdminVault(
            vault.copy(
                commissionPoolBalance = newBalance,
                updatedAt = System.currentTimeMillis()
            )
        )
        val ref = "ADMWD${System.currentTimeMillis().toString().takeLast(6)}"
        val targetEmail = activeUserEmail ?: "admin@jiopanal.in"
        dao.insertTransaction(
            TransactionEntity(
                userEmail = targetEmail,
                type = TransactionEntity.TYPE_ADMIN_WITHDRAWAL,
                amount = amount,
                commissionAmount = 0.0,
                utrOrRef = ref,
                title = "Admin Commission Withdrawal",
                subtitle = "Settled to $destination • Pool Bal ${Formatters.formatInr(newBalance)}",
                status = TransactionEntity.STATUS_SUCCESS,
                paymentMethod = "ADMIN_PAYOUT"
            )
        )
        dao.insertTicker(
            LiveTickerEntity(
                message = "Admin Commission Payout Settled to $destination",
                highlightAmount = "${Formatters.formatInr(amount)} Withdrawn",
                isCredit = true
            )
        )
        notificationHelper.sendLiveAlert(
            title = "Admin Withdrawal • ${Formatters.formatInr(amount)}",
            body = "Admin commission withdrawal of ${Formatters.formatInr(amount)} settled to $destination. Remaining Pool: ${Formatters.formatInr(newBalance)}."
        )
        return Result.success(Unit)
    }

    suspend fun creditUserByAdmin(
        targetEmail: String,
        amount: Double,
        includeCommission: Boolean = true,
        note: String = ""
    ): Result<Unit> {
        ensureInitialized()
        val cleanEmail = targetEmail.trim().lowercase()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Enter a valid user Gmail address"))
        }
        if (amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter a valid amount greater than ₹0"))
        }

        val vault = dao.getAdminVault() ?: AdminVaultEntity()
        var user = dao.getUser(cleanEmail)
        if (user == null) {
            val prefix = cleanEmail.substringBefore("@")
                .replace(Regex("[^a-zA-Z0-9]"), "")
                .uppercase()
                .take(6)
                .ifBlank { "PARTNER" }
            val displayName = cleanEmail.substringBefore("@")
                .split(".", "_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                .ifBlank { "Jio Partner" }
            user = UserAccountEntity(
                email = cleanEmail,
                fullName = displayName,
                status = if (amount >= vault.activationAmount) UserAccountEntity.STATUS_ACTIVE else UserAccountEntity.STATUS_INACTIVE,
                referralCode = "JP-$prefix-15"
            )
            dao.upsertUser(user)
        }

        val commission = if (includeCommission) {
            ((amount * (vault.commissionRatePercent / 100.0)) * 100.0).roundToInt() / 100.0
        } else {
            0.0
        }
        val totalAdded = amount + commission
        val newStatus = if (amount >= vault.activationAmount && user.status != UserAccountEntity.STATUS_SUSPENDED) {
            UserAccountEntity.STATUS_ACTIVE
        } else {
            user.status
        }
        val updatedUser = user.copy(
            status = newStatus,
            walletBalance = user.walletBalance + totalAdded,
            commissionEarned = user.commissionEarned + commission,
            totalDeposited = user.totalDeposited + amount
        )
        dao.updateUser(updatedUser)

        val ref = "ADMCR${System.currentTimeMillis().toString().takeLast(6)}"
        val reason = note.trim().ifBlank { "Added by Admin Control Vault" }
        dao.insertTransaction(
            TransactionEntity(
                userEmail = cleanEmail,
                type = TransactionEntity.TYPE_DEPOSIT,
                amount = amount,
                commissionAmount = commission,
                utrOrRef = ref,
                title = "Admin Wallet Credit",
                subtitle = "$reason • Ref $ref" + if (commission > 0.0) " • +15% Bonus" else "",
                status = TransactionEntity.STATUS_SUCCESS,
                paymentMethod = "ADMIN_CREDIT"
            )
        )

        if (commission > 0.0) {
            dao.insertTransaction(
                TransactionEntity(
                    userEmail = cleanEmail,
                    type = TransactionEntity.TYPE_COMMISSION,
                    amount = commission,
                    commissionAmount = commission,
                    utrOrRef = "COMM-$ref",
                    title = "15% Commission Credited",
                    subtitle = "15% bonus on Admin credit of ${Formatters.formatInr(amount)}",
                    status = TransactionEntity.STATUS_SUCCESS,
                    paymentMethod = "SYSTEM"
                )
            )
        }

        val smsBody = buildString {
            append("CREDIT SMS ALERT: Your Jio Panal A/C ${Formatters.maskEmail(cleanEmail)} received ${Formatters.formatInr(amount)}")
            if (commission > 0.0) {
                append(" + ${Formatters.formatInr(commission)} (15% Comm)")
            }
            append(" from Admin Vault. Ref: $ref. Avl Bal: ${Formatters.formatInr(updatedUser.walletBalance)}.")
        }
        dao.insertAlert(
            NotificationAlertEntity(
                userEmail = cleanEmail,
                title = "Admin Credit: +${Formatters.formatInr(totalAdded)}",
                smsBody = smsBody,
                amount = totalAdded,
                alertType = "CREDIT"
            )
        )
        dao.insertTicker(
            LiveTickerEntity(
                message = "${Formatters.maskEmail(cleanEmail)} Credited by Admin",
                highlightAmount = "+${Formatters.formatInr(totalAdded)}",
                isCredit = true
            )
        )
        notificationHelper.sendLiveAlert(
            title = "Live Credit SMS • +${Formatters.formatInr(totalAdded)}",
            body = smsBody
        )
        return Result.success(Unit)
    }

    suspend fun sendAdminReplyToUser(userEmail: String, message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        dao.insertSupportMessage(
            SupportMessageEntity(
                userEmail = userEmail,
                senderRole = "SUPPORT",
                message = "[Admin Desk] $clean"
            )
        )
    }
}
