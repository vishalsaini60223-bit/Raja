package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserAccountEntity(
    @PrimaryKey val email: String,
    val fullName: String = "",
    val phone: String = "",
    val city: String = "",
    val businessName: String = "",
    val status: String = STATUS_INACTIVE, // INACTIVE, PENDING, ACTIVE, SUSPENDED
    val walletBalance: Double = 0.0,
    val commissionEarned: Double = 0.0,
    val totalDeposited: Double = 0.0,
    val referralCode: String = "",
    val referredBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_INACTIVE = "INACTIVE"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val type: String, // ACTIVATION, DEPOSIT, COMMISSION_CREDIT, WITHDRAWAL, REFERRAL_BONUS, ADMIN_WITHDRAWAL
    val amount: Double,
    val commissionAmount: Double = 0.0,
    val utrOrRef: String,
    val title: String,
    val subtitle: String,
    val status: String, // PENDING, SUCCESS, REJECTED
    val paymentMethod: String = "UPI",
    val createdAt: Long = System.currentTimeMillis()
) {
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
    }
}

@Entity(tableName = "payout_methods")
data class PayoutMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val methodType: String, // UPI or BANK
    val label: String,
    val upiId: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val holderName: String = "",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_alerts")
data class NotificationAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val title: String,
    val smsBody: String,
    val amount: Double = 0.0,
    val alertType: String = "CREDIT", // CREDIT, DEPOSIT, SECURITY, SYSTEM
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "support_messages")
data class SupportMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userEmail: String,
    val senderRole: String, // USER or SUPPORT
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "referrals")
data class ReferralEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val referrerEmail: String,
    val friendName: String,
    val friendEmail: String,
    val status: String, // ACTIVE or PENDING
    val commissionEarned: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "live_ticker")
data class LiveTickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val message: String,
    val highlightAmount: String,
    val isCredit: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admin_vault")
data class AdminVaultEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val adminPasskey: String = DEFAULT_PASSKEY,
    val commissionPoolBalance: Double = INITIAL_COMMISSION_POOL,
    val commissionRatePercent: Double = 15.0,
    val activationAmount: Double = 5000.0,
    val merchantUpiId: String = "vishalkumar90@upi",
    val telegramHandle: String = "@Sainishaab9076",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = "ADMIN_SINGLETON"
        const val DEFAULT_PASSKEY = "JIOADMIN2026"
        const val INITIAL_COMMISSION_POOL = 200000.0
    }
}
