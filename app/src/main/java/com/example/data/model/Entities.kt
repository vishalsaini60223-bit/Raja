package com.example.data.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue

private fun DocumentSnapshot.estimateMillis(field: String, fallback: Long = 0L): Long {
    val ts = getTimestamp(field, ServerTimestampBehavior.ESTIMATE)
    return ts?.toDate()?.time ?: fallback
}

private fun DocumentSnapshot.safeDouble(field: String, default: Double = 0.0): Double {
    return (get(field) as? Number)?.toDouble() ?: default
}

private fun DocumentSnapshot.safeInt(field: String, default: Int = 0): Int {
    return (get(field) as? Number)?.toInt() ?: default
}

data class UserAccountEntity(
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val phone: String = "",
    val city: String = "",
    val businessName: String = "",
    val status: String = STATUS_INACTIVE,
    val walletBalance: Double = 0.0,
    val commissionEarned: Double = 0.0,
    val totalDeposited: Double = 0.0,
    val referralCode: String = "",
    val referredBy: String = "",
    val lastLoginAt: Long = 0L,
    val loginCount: Int = 1,
    val isLoggedIn: Boolean = false,
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "email" to email,
        "fullName" to fullName,
        "phone" to phone,
        "city" to city,
        "businessName" to businessName,
        "status" to status,
        "walletBalance" to walletBalance,
        "commissionEarned" to commissionEarned,
        "totalDeposited" to totalDeposited,
        "referralCode" to referralCode,
        "referredBy" to referredBy,
        "loginCount" to loginCount,
        "isLoggedIn" to isLoggedIn,
        "lastLoginAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val STATUS_INACTIVE = "INACTIVE"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"

        fun fromSnapshot(doc: DocumentSnapshot): UserAccountEntity? {
            if (!doc.exists()) return null
            val email = doc.getString("email") ?: return null
            return UserAccountEntity(
                userId = doc.getString("userId") ?: doc.id,
                email = email,
                fullName = doc.getString("fullName") ?: "",
                phone = doc.getString("phone") ?: "",
                city = doc.getString("city") ?: "",
                businessName = doc.getString("businessName") ?: "",
                status = doc.getString("status") ?: STATUS_INACTIVE,
                walletBalance = doc.safeDouble("walletBalance"),
                commissionEarned = doc.safeDouble("commissionEarned"),
                totalDeposited = doc.safeDouble("totalDeposited"),
                referralCode = doc.getString("referralCode") ?: "",
                referredBy = doc.getString("referredBy") ?: "",
                lastLoginAt = doc.estimateMillis("lastLoginAt"),
                loginCount = doc.safeInt("loginCount", 1),
                isLoggedIn = doc.getBoolean("isLoggedIn") ?: false,
                createdAt = doc.estimateMillis("createdAt")
            )
        }
    }
}

data class TransactionEntity(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val type: String = TYPE_DEPOSIT,
    val amount: Double = 0.0,
    val commissionAmount: Double = 0.0,
    val utrOrRef: String = "",
    val title: String = "",
    val subtitle: String = "",
    val status: String = STATUS_PENDING,
    val paymentMethod: String = "UPI",
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userEmail" to userEmail,
        "type" to type,
        "amount" to amount,
        "commissionAmount" to commissionAmount,
        "utrOrRef" to utrOrRef,
        "title" to title,
        "subtitle" to subtitle,
        "status" to status,
        "paymentMethod" to paymentMethod,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val TYPE_ACTIVATION = "ACTIVATION"
        const val TYPE_DEPOSIT = "DEPOSIT"
        const val TYPE_COMMISSION = "COMMISSION_CREDIT"
        const val TYPE_WITHDRAWAL = "WITHDRAWAL"
        const val TYPE_REFERRAL = "REFERRAL_BONUS"
        const val TYPE_ADMIN_WITHDRAWAL = "ADMIN_WITHDRAWAL"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_REJECTED = "REJECTED"

        fun fromSnapshot(doc: DocumentSnapshot): TransactionEntity? {
            if (!doc.exists()) return null
            return TransactionEntity(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                userEmail = doc.getString("userEmail") ?: "",
                type = doc.getString("type") ?: TYPE_DEPOSIT,
                amount = doc.safeDouble("amount"),
                commissionAmount = doc.safeDouble("commissionAmount"),
                utrOrRef = doc.getString("utrOrRef") ?: "",
                title = doc.getString("title") ?: "",
                subtitle = doc.getString("subtitle") ?: "",
                status = doc.getString("status") ?: STATUS_PENDING,
                paymentMethod = doc.getString("paymentMethod") ?: "UPI",
                createdAt = doc.estimateMillis("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class PayoutMethodEntity(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val methodType: String = "UPI",
    val label: String = "",
    val upiId: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val holderName: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userEmail" to userEmail,
        "methodType" to methodType,
        "label" to label,
        "upiId" to upiId,
        "bankName" to bankName,
        "accountNumber" to accountNumber,
        "ifscCode" to ifscCode,
        "holderName" to holderName,
        "isDefault" to isDefault,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): PayoutMethodEntity? {
            if (!doc.exists()) return null
            return PayoutMethodEntity(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                userEmail = doc.getString("userEmail") ?: "",
                methodType = doc.getString("methodType") ?: "UPI",
                label = doc.getString("label") ?: "",
                upiId = doc.getString("upiId") ?: "",
                bankName = doc.getString("bankName") ?: "",
                accountNumber = doc.getString("accountNumber") ?: "",
                ifscCode = doc.getString("ifscCode") ?: "",
                holderName = doc.getString("holderName") ?: "",
                isDefault = doc.getBoolean("isDefault") ?: false,
                createdAt = doc.estimateMillis("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class NotificationAlertEntity(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val title: String = "",
    val smsBody: String = "",
    val amount: Double = 0.0,
    val alertType: String = "CREDIT",
    val isRead: Boolean = false,
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userEmail" to userEmail,
        "title" to title,
        "smsBody" to smsBody,
        "amount" to amount,
        "alertType" to alertType,
        "isRead" to isRead,
        "createdAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): NotificationAlertEntity? {
            if (!doc.exists()) return null
            return NotificationAlertEntity(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                userEmail = doc.getString("userEmail") ?: "",
                title = doc.getString("title") ?: "",
                smsBody = doc.getString("smsBody") ?: "",
                amount = doc.safeDouble("amount"),
                alertType = doc.getString("alertType") ?: "CREDIT",
                isRead = doc.getBoolean("isRead") ?: false,
                createdAt = doc.estimateMillis("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class SupportMessageEntity(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val senderRole: String = "USER",
    val message: String = "",
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userEmail" to userEmail,
        "senderRole" to senderRole,
        "message" to message,
        "createdAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): SupportMessageEntity? {
            if (!doc.exists()) return null
            return SupportMessageEntity(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                userEmail = doc.getString("userEmail") ?: "",
                senderRole = doc.getString("senderRole") ?: "USER",
                message = doc.getString("message") ?: "",
                createdAt = doc.estimateMillis("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class ReferralEntity(
    val id: String = "",
    val referrerId: String = "",
    val referrerEmail: String = "",
    val friendName: String = "",
    val friendEmail: String = "",
    val status: String = "PENDING",
    val commissionEarned: Double = 0.0,
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "referrerId" to referrerId,
        "referrerEmail" to referrerEmail,
        "friendName" to friendName,
        "friendEmail" to friendEmail,
        "status" to status,
        "commissionEarned" to commissionEarned,
        "createdAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): ReferralEntity? {
            if (!doc.exists()) return null
            return ReferralEntity(
                id = doc.id,
                referrerId = doc.getString("referrerId") ?: "",
                referrerEmail = doc.getString("referrerEmail") ?: "",
                friendName = doc.getString("friendName") ?: "",
                friendEmail = doc.getString("friendEmail") ?: "",
                status = doc.getString("status") ?: "PENDING",
                commissionEarned = doc.safeDouble("commissionEarned"),
                createdAt = doc.estimateMillis("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class LiveTickerEntity(
    val id: String = "",
    val message: String = "",
    val highlightAmount: String = "",
    val isCredit: Boolean = true,
    val createdAt: Long = 0L
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "message" to message,
        "highlightAmount" to highlightAmount,
        "isCredit" to isCredit,
        "createdAt" to FieldValue.serverTimestamp()
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): LiveTickerEntity? {
            if (!doc.exists()) return null
            return LiveTickerEntity(
                id = doc.id,
                message = doc.getString("message") ?: "",
                highlightAmount = doc.getString("highlightAmount") ?: "",
                isCredit = doc.getBoolean("isCredit") ?: true,
                createdAt = doc.estimateMillis("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class AdminVaultEntity(
    val id: String = SINGLETON_ID,
    val adminPasskey: String = DEFAULT_PASSKEY,
    val commissionPoolBalance: Double = INITIAL_COMMISSION_POOL,
    val commissionRatePercent: Double = 15.0,
    val activationAmount: Double = 5000.0,
    val merchantUpiId: String = "vishalkumar90@upi",
    val telegramHandle: String = "@Sainishaab9076",
    val updatedAt: Long = 0L
) {
    fun toAdminMap(): Map<String, Any> = mapOf(
        "adminPasskey" to adminPasskey,
        "commissionPoolBalance" to commissionPoolBalance,
        "commissionRatePercent" to commissionRatePercent,
        "activationAmount" to activationAmount,
        "merchantUpiId" to merchantUpiId,
        "telegramHandle" to telegramHandle,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toPublicConfigMap(): Map<String, Any> = mapOf(
        "commissionRatePercent" to commissionRatePercent,
        "activationAmount" to activationAmount,
        "merchantUpiId" to merchantUpiId,
        "telegramHandle" to telegramHandle,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val SINGLETON_ID = "ADMIN_SINGLETON"
        const val PUBLIC_CONFIG_ID = "PUBLIC_SETTINGS"
        const val DEFAULT_PASSKEY = "JIOADMIN2026"
        const val INITIAL_COMMISSION_POOL = 200000.0
        const val AUTHORIZED_ADMIN_EMAIL = "vishalsaini60223@gmail.com"

        fun fromAdminSnapshot(doc: DocumentSnapshot): AdminVaultEntity? {
            if (!doc.exists()) return null
            return AdminVaultEntity(
                id = doc.id,
                adminPasskey = doc.getString("adminPasskey") ?: DEFAULT_PASSKEY,
                commissionPoolBalance = doc.safeDouble("commissionPoolBalance", INITIAL_COMMISSION_POOL),
                commissionRatePercent = doc.safeDouble("commissionRatePercent", 15.0),
                activationAmount = doc.safeDouble("activationAmount", 5000.0),
                merchantUpiId = doc.getString("merchantUpiId") ?: "vishalkumar90@upi",
                telegramHandle = doc.getString("telegramHandle") ?: "@Sainishaab9076",
                updatedAt = doc.estimateMillis("updatedAt")
            )
        }

        fun fromPublicSnapshot(doc: DocumentSnapshot): AdminVaultEntity? {
            if (!doc.exists()) return null
            return AdminVaultEntity(
                id = doc.id,
                adminPasskey = "",
                commissionPoolBalance = INITIAL_COMMISSION_POOL,
                commissionRatePercent = doc.safeDouble("commissionRatePercent", 15.0),
                activationAmount = doc.safeDouble("activationAmount", 5000.0),
                merchantUpiId = doc.getString("merchantUpiId") ?: "vishalkumar90@upi",
                telegramHandle = doc.getString("telegramHandle") ?: "@Sainishaab9076",
                updatedAt = doc.estimateMillis("updatedAt")
            )
        }
    }
}
