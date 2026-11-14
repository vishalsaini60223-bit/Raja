package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AdminVaultEntity
import com.example.data.model.LiveTickerEntity
import com.example.data.model.NotificationAlertEntity
import com.example.data.model.PayoutMethodEntity
import com.example.data.model.ReferralEntity
import com.example.data.model.SupportMessageEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JioPanalDao {

    // Users
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun observeUser(email: String): Flow<UserAccountEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUser(email: String): UserAccountEntity?

    @Query("SELECT * FROM users ORDER BY isLoggedIn DESC, lastLoginAt DESC, createdAt DESC")
    fun observeAllUsers(): Flow<List<UserAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserAccountEntity)

    @Update
    suspend fun updateUser(user: UserAccountEntity)

    // Transactions
    @Query("SELECT * FROM transactions WHERE userEmail = :email ORDER BY createdAt DESC")
    fun observeTransactions(email: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePendingTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    // Payout Methods (UPI / Bank)
    @Query("SELECT * FROM payout_methods WHERE userEmail = :email ORDER BY isDefault DESC, createdAt DESC")
    fun observePayoutMethods(email: String): Flow<List<PayoutMethodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayoutMethod(method: PayoutMethodEntity): Long

    @Query("UPDATE payout_methods SET isDefault = CASE WHEN id = :methodId THEN 1 ELSE 0 END WHERE userEmail = :email")
    suspend fun setDefaultPayoutMethod(email: String, methodId: Long)

    @Query("DELETE FROM payout_methods WHERE id = :methodId")
    suspend fun deletePayoutMethod(methodId: Long)

    // Notification & SMS-Style Alerts
    @Query("SELECT * FROM notification_alerts WHERE userEmail = :email ORDER BY createdAt DESC")
    fun observeAlerts(email: String): Flow<List<NotificationAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: NotificationAlertEntity): Long

    @Query("UPDATE notification_alerts SET isRead = 1 WHERE userEmail = :email")
    suspend fun markAllAlertsRead(email: String)

    // Support Chat
    @Query("SELECT * FROM support_messages WHERE userEmail = :email ORDER BY createdAt ASC")
    fun observeSupportMessages(email: String): Flow<List<SupportMessageEntity>>

    @Query("SELECT * FROM support_messages ORDER BY createdAt DESC")
    fun observeAllSupportMessages(): Flow<List<SupportMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupportMessage(message: SupportMessageEntity): Long

    // Referrals
    @Query("SELECT * FROM referrals WHERE referrerEmail = :email ORDER BY createdAt DESC")
    fun observeReferrals(email: String): Flow<List<ReferralEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferral(referral: ReferralEntity): Long

    // Live Ticker
    @Query("SELECT * FROM live_ticker ORDER BY createdAt DESC LIMIT 12")
    fun observeLiveTicker(): Flow<List<LiveTickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicker(ticker: LiveTickerEntity): Long

    // Admin Vault
    @Query("SELECT * FROM admin_vault WHERE id = 'ADMIN_SINGLETON' LIMIT 1")
    fun observeAdminVault(): Flow<AdminVaultEntity?>

    @Query("SELECT * FROM admin_vault WHERE id = 'ADMIN_SINGLETON' LIMIT 1")
    suspend fun getAdminVault(): AdminVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdminVault(vault: AdminVaultEntity)
}
