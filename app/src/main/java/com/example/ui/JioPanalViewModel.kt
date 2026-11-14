package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminVaultEntity
import com.example.data.model.LiveTickerEntity
import com.example.data.model.NotificationAlertEntity
import com.example.data.model.PayoutMethodEntity
import com.example.data.model.ReferralEntity
import com.example.data.model.SupportMessageEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserAccountEntity
import com.example.data.prefs.UserPreferences
import com.example.data.repository.JioPanalRepository
import com.example.ui.i18n.AppLanguage
import com.example.util.Formatters
import com.example.util.GoogleAuthHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuthStage {
    SPLASH,
    EMAIL_INPUT,
    BIOMETRIC_LOCK,
    AUTHENTICATED
}

data class JioPanalUiState(
    val isLoading: Boolean = true,
    val isAuthBusy: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val language: AppLanguage = AppLanguage.ENGLISH,
    val authStage: AuthStage = AuthStage.SPLASH,
    val authEmail: String? = null,
    val user: UserAccountEntity? = null,
    val availableBalance: Double = 0.0,
    val transactions: List<TransactionEntity> = emptyList(),
    val payoutMethods: List<PayoutMethodEntity> = emptyList(),
    val alerts: List<NotificationAlertEntity> = emptyList(),
    val unreadAlertCount: Int = 0,
    val supportMessages: List<SupportMessageEntity> = emptyList(),
    val referrals: List<ReferralEntity> = emptyList(),
    val liveTicker: List<LiveTickerEntity> = emptyList(),
    val adminVault: AdminVaultEntity = AdminVaultEntity(),
    val pendingApprovals: List<TransactionEntity> = emptyList(),
    val allUsers: List<UserAccountEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val isAuthorizedAdmin: Boolean = false,
    val isAdminUnlocked: Boolean = false,
    val isPanelRunning: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

private data class SessionAuxState(
    val splashDone: Boolean = false,
    val isAuthBusy: Boolean = false,
    val isSessionUnlocked: Boolean = false,
    val isAdminUnlocked: Boolean = false,
    val isPanelRunning: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class JioPanalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = JioPanalRepository(application.applicationContext)
    private val auxState = MutableStateFlow(SessionAuxState())
    private val authUserFlow: Flow<FirebaseUser?> = repository.observeAuthUser()

    private fun <T> Flow<T>.safeCatch(fallback: T): Flow<T> = catch { err ->
        if (err is CancellationException) throw err
        auxState.value = auxState.value.copy(
            errorMessage = err.localizedMessage ?: "Cloud sync permission error"
        )
        emit(fallback)
    }

    private val userDataFlow = authUserFlow.flatMapLatest { fbUser ->
        if (fbUser == null) {
            flowOf(UserScopedBundle())
        } else {
            val uid = fbUser.uid
            combine(
                repository.observeUser(uid).safeCatch(null),
                repository.observeTransactions(uid).safeCatch(emptyList()),
                repository.observePayoutMethods(uid).safeCatch(emptyList()),
                repository.observeAlerts(uid).safeCatch(emptyList()),
                repository.observeSupportMessages(uid).safeCatch(emptyList()),
                repository.observeReferrals(uid).safeCatch(emptyList()),
                repository.observeLiveTicker().safeCatch(emptyList()),
                repository.observePublicConfig().safeCatch(AdminVaultEntity())
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                UserScopedBundle(
                    user = values[0] as UserAccountEntity?,
                    transactions = values[1] as List<TransactionEntity>,
                    payoutMethods = values[2] as List<PayoutMethodEntity>,
                    alerts = values[3] as List<NotificationAlertEntity>,
                    supportMessages = values[4] as List<SupportMessageEntity>,
                    referrals = values[5] as List<ReferralEntity>,
                    liveTicker = values[6] as List<LiveTickerEntity>,
                    publicConfig = values[7] as AdminVaultEntity
                )
            }
        }
    }

    private val adminBundleFlow = authUserFlow.flatMapLatest { fbUser ->
        val isVerifiedAdmin = fbUser != null &&
            fbUser.isEmailVerified &&
            fbUser.email?.trim()?.lowercase() == AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL
        if (!isVerifiedAdmin) {
            flowOf(AdminBundle())
        } else {
            combine(
                repository.observeAdminVault().safeCatch(AdminVaultEntity()),
                repository.observePendingTransactions().safeCatch(emptyList()),
                repository.observeAllUsers().safeCatch(emptyList()),
                repository.observeAllTransactions().safeCatch(emptyList())
            ) { vault, pending, users, allTx ->
                AdminBundle(
                    vault = vault,
                    pendingApprovals = pending,
                    allUsers = users,
                    allTransactions = allTx
                )
            }
        }
    }

    val uiState: StateFlow<JioPanalUiState> = combine(
        repository.preferencesFlow,
        authUserFlow,
        auxState,
        userDataFlow,
        adminBundleFlow
    ) { prefs, fbUser, aux, userBundle, adminBundle ->
        val currentEmail = fbUser?.email?.trim()?.lowercase()
        val stage = when {
            !aux.splashDone -> AuthStage.SPLASH
            fbUser == null -> AuthStage.EMAIL_INPUT
            prefs.biometricEnabled && !aux.isSessionUnlocked -> AuthStage.BIOMETRIC_LOCK
            else -> AuthStage.AUTHENTICATED
        }
        val isAuthorizedAdmin = fbUser != null &&
            fbUser.isEmailVerified &&
            currentEmail == AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL
        val user = userBundle.user
        val isUserActive = user?.status == UserAccountEntity.STATUS_ACTIVE
        val pendingWithdrawals = userBundle.transactions
            .filter { it.type == TransactionEntity.TYPE_WITHDRAWAL && it.status == TransactionEntity.STATUS_PENDING }
            .sumOf { it.amount }
        val availableBal = ((user?.walletBalance ?: 0.0) - pendingWithdrawals).coerceAtLeast(0.0)
        val mergedVault = if (isAuthorizedAdmin) adminBundle.vault else userBundle.publicConfig

        JioPanalUiState(
            isLoading = false,
            isAuthBusy = aux.isAuthBusy,
            preferences = prefs,
            language = AppLanguage.fromCode(prefs.languageCode),
            authStage = stage,
            authEmail = currentEmail ?: prefs.loggedInEmail,
            user = user,
            availableBalance = availableBal,
            transactions = userBundle.transactions,
            payoutMethods = userBundle.payoutMethods,
            alerts = userBundle.alerts,
            unreadAlertCount = userBundle.alerts.count { !it.isRead },
            supportMessages = userBundle.supportMessages,
            referrals = userBundle.referrals,
            liveTicker = userBundle.liveTicker,
            adminVault = mergedVault,
            pendingApprovals = adminBundle.pendingApprovals,
            allUsers = adminBundle.allUsers,
            allTransactions = adminBundle.allTransactions,
            isAuthorizedAdmin = isAuthorizedAdmin,
            isAdminUnlocked = isAuthorizedAdmin && aux.isAdminUnlocked,
            isPanelRunning = isUserActive && aux.isPanelRunning,
            feedbackMessage = aux.feedbackMessage,
            errorMessage = aux.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = JioPanalUiState()
    )

    fun finishSplash() {
        auxState.value = auxState.value.copy(splashDone = true)
    }

    fun trySilentGoogleSignIn(activityContext: Context) {
        if (auxState.value.isAuthBusy) return
        viewModelScope.launch {
            val res = GoogleAuthHelper.signInSilentIfAuthorized(activityContext)
            val fbUser = res.getOrNull()
            if (fbUser != null) {
                repository.syncSignedInUser(fbUser)
                auxState.value = auxState.value.copy(isSessionUnlocked = false, errorMessage = null)
            }
        }
    }

    fun signInWithGoogle(activityContext: Context, referralCode: String = "") {
        if (auxState.value.isAuthBusy) return
        auxState.value = auxState.value.copy(isAuthBusy = true, errorMessage = null)
        viewModelScope.launch {
            val authRes = GoogleAuthHelper.signInInteractive(activityContext)
            authRes.onSuccess { fbUser ->
                val syncRes = repository.syncSignedInUser(fbUser, referralCode)
                syncRes.onSuccess {
                    auxState.value = auxState.value.copy(
                        isAuthBusy = false,
                        isSessionUnlocked = true,
                        errorMessage = null,
                        feedbackMessage = null
                    )
                }.onFailure { err ->
                    auxState.value = auxState.value.copy(
                        isAuthBusy = false,
                        errorMessage = err.localizedMessage ?: "Failed to sync user profile"
                    )
                }
            }.onFailure { err ->
                auxState.value = auxState.value.copy(
                    isAuthBusy = false,
                    errorMessage = err.localizedMessage ?: "Google Sign-In failed"
                )
            }
        }
    }

    fun unlockWithBiometricSuccess() {
        auxState.value = auxState.value.copy(isSessionUnlocked = true, errorMessage = null)
    }

    fun unlockWithPin(pin: String): Boolean {
        return if (pin == uiState.value.preferences.securityPin) {
            auxState.value = auxState.value.copy(isSessionUnlocked = true, errorMessage = null)
            true
        } else {
            auxState.value = auxState.value.copy(errorMessage = "Incorrect 4-digit PIN (Default: 1515)")
            false
        }
    }

    fun logout(context: Context) {
        viewModelScope.launch {
            repository.logout(context)
            auxState.value = auxState.value.copy(
                splashDone = true,
                isAuthBusy = false,
                isSessionUnlocked = false,
                isAdminUnlocked = false,
                isPanelRunning = false,
                feedbackMessage = null,
                errorMessage = null
            )
        }
    }

    fun toggleRunPanal() {
        val user = uiState.value.user
        if (user?.status != UserAccountEntity.STATUS_ACTIVE) {
            auxState.value = auxState.value.copy(
                isPanelRunning = false,
                errorMessage = "RUN Panal Locked: Account activation (₹5,000 + Admin Approval) required first."
            )
            return
        }
        val nextRunning = !auxState.value.isPanelRunning
        auxState.value = auxState.value.copy(
            isPanelRunning = nextRunning,
            errorMessage = null,
            feedbackMessage = if (nextRunning) "RUN Panal Started • Live Recorded Feed Active" else "RUN Panal Paused"
        )
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguageCode(language.code) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricEnabled(enabled)
            auxState.value = auxState.value.copy(
                feedbackMessage = if (enabled) "Biometric lock enabled" else "Biometric lock disabled"
            )
        }
    }

    fun updateSecurityPin(newPin: String) {
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
            auxState.value = auxState.value.copy(errorMessage = "PIN must be exactly 4 digits")
            return
        }
        viewModelScope.launch {
            repository.setSecurityPin(newPin)
            auxState.value = auxState.value.copy(feedbackMessage = "Security PIN updated")
        }
    }

    fun updateProfile(fullName: String, phone: String, city: String, businessName: String) {
        val uid = uiState.value.user?.userId ?: return
        viewModelScope.launch {
            try {
                repository.updateProfile(uid, fullName, phone, city, businessName)
                auxState.value = auxState.value.copy(feedbackMessage = "Personal details synced to cloud")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Update failed")
            }
        }
    }

    fun submitActivationOrDeposit(amount: Double, utr: String, paymentMethod: String, isActivation: Boolean) {
        val user = uiState.value.user ?: return
        val cleanUtr = utr.trim()
        if (cleanUtr.length < 6) {
            auxState.value = auxState.value.copy(errorMessage = "Enter a valid UTR / Reference number (min 6 characters)")
            return
        }
        if (amount <= 0.0) {
            auxState.value = auxState.value.copy(errorMessage = "Enter a valid deposit amount")
            return
        }
        viewModelScope.launch {
            try {
                repository.submitActivationOrDeposit(user, amount, cleanUtr, paymentMethod, isActivation)
                auxState.value = auxState.value.copy(
                    errorMessage = null,
                    feedbackMessage = if (isActivation) {
                        "₹5,000 activation sent to Admin! Pending approval."
                    } else {
                        "Deposit sent to Admin for approval."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Failed to submit deposit")
            }
        }
    }

    fun addPayoutMethod(
        methodType: String,
        label: String,
        upiId: String,
        bankName: String,
        accountNumber: String,
        ifscCode: String,
        holderName: String
    ) {
        val user = uiState.value.user ?: return
        if (methodType == "UPI" && !upiId.contains("@")) {
            auxState.value = auxState.value.copy(errorMessage = "Enter a valid UPI ID (e.g. name@okicici)")
            return
        }
        if (methodType == "BANK" && (accountNumber.length < 6 || ifscCode.length < 4)) {
            auxState.value = auxState.value.copy(errorMessage = "Enter valid bank account number and IFSC code")
            return
        }
        viewModelScope.launch {
            try {
                repository.addPayoutMethod(user, methodType, label.ifBlank { methodType }, upiId, bankName, accountNumber, ifscCode, holderName)
                auxState.value = auxState.value.copy(feedbackMessage = "Payout account added")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Failed to save payout account")
            }
        }
    }

    fun setDefaultPayoutMethod(id: String) {
        val uid = uiState.value.user?.userId ?: return
        viewModelScope.launch {
            try {
                repository.setDefaultPayoutMethod(uid, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Failed to update default account")
            }
        }
    }

    fun deletePayoutMethod(id: String) {
        viewModelScope.launch {
            try {
                repository.deletePayoutMethod(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Failed to delete payout account")
            }
        }
    }

    fun requestUserWithdrawal(amount: Double, destinationLabel: String) {
        val user = uiState.value.user ?: return
        viewModelScope.launch {
            val result = repository.requestUserWithdrawal(user, amount, destinationLabel)
            result.onSuccess {
                auxState.value = auxState.value.copy(feedbackMessage = "Withdrawal request sent to Admin! Pending approval.")
            }.onFailure { err ->
                auxState.value = auxState.value.copy(errorMessage = err.message ?: "Withdrawal failed")
            }
        }
    }

    fun addReferral(friendName: String, friendEmail: String) {
        val user = uiState.value.user ?: return
        if (friendName.isBlank() || !friendEmail.contains("@")) {
            auxState.value = auxState.value.copy(errorMessage = "Enter friend's name and valid email")
            return
        }
        viewModelScope.launch {
            try {
                repository.addReferral(user, friendName, friendEmail)
                auxState.value = auxState.value.copy(feedbackMessage = "Referral recorded! 15% bonus activates on deposit.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Failed to log referral")
            }
        }
    }

    fun sendSupportMessage(text: String) {
        val user = uiState.value.user ?: return
        viewModelScope.launch {
            try {
                repository.sendSupportMessage(user, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Failed to send support message")
            }
        }
    }

    fun markAlertsRead() {
        val uid = uiState.value.user?.userId ?: return
        viewModelScope.launch {
            try {
                repository.markAlertsRead(uid)
            } catch (_: Exception) {
            }
        }
    }

    // Admin Controls
    fun verifyAdminPasskey(passkey: String) {
        if (!uiState.value.isAuthorizedAdmin) {
            auxState.value = auxState.value.copy(
                isAdminUnlocked = false,
                errorMessage = "Access denied: Only authorized Admin Gmail can open Admin Vault."
            )
            return
        }
        viewModelScope.launch {
            val ok = repository.verifyAdminPasskey(passkey)
            if (ok) {
                auxState.value = auxState.value.copy(
                    isAdminUnlocked = true,
                    errorMessage = null,
                    feedbackMessage = "Admin Control Vault Unlocked"
                )
            } else {
                auxState.value = auxState.value.copy(errorMessage = "Incorrect Admin Passkey")
            }
        }
    }

    fun lockAdminPanel() {
        auxState.value = auxState.value.copy(isAdminUnlocked = false)
    }

    fun updateAdminPasskey(newKey: String) {
        if (newKey.trim().length < 6) {
            auxState.value = auxState.value.copy(errorMessage = "Admin Passkey must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            try {
                repository.updateAdminPasskey(newKey)
                auxState.value = auxState.value.copy(feedbackMessage = "Admin Passkey updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Passkey update failed")
            }
        }
    }

    fun approvePendingTx(txId: String) {
        viewModelScope.launch {
            val res = repository.approveTransaction(txId)
            res.onSuccess {
                auxState.value = auxState.value.copy(feedbackMessage = "Request approved and synced across devices!")
            }.onFailure { err ->
                auxState.value = auxState.value.copy(errorMessage = err.localizedMessage ?: "Approval failed")
            }
        }
    }

    fun rejectPendingTx(txId: String) {
        viewModelScope.launch {
            val res = repository.rejectTransaction(txId)
            res.onSuccess {
                auxState.value = auxState.value.copy(feedbackMessage = "Request declined.")
            }.onFailure { err ->
                auxState.value = auxState.value.copy(errorMessage = err.localizedMessage ?: "Decline failed")
            }
        }
    }

    fun setUserStatusByAdmin(userId: String, status: String) {
        viewModelScope.launch {
            try {
                repository.setUserStatusByAdmin(userId, status)
                auxState.value = auxState.value.copy(feedbackMessage = "User status updated to $status")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Status update failed")
            }
        }
    }

    fun withdrawAdminCommission(amount: Double, destination: String) {
        if (destination.isBlank()) {
            auxState.value = auxState.value.copy(errorMessage = "Enter a destination UPI or Bank account")
            return
        }
        viewModelScope.launch {
            val result = repository.withdrawAdminCommission(amount, destination)
            result.onSuccess {
                auxState.value = auxState.value.copy(feedbackMessage = "Admin commission withdrawal settled!")
            }.onFailure { err ->
                auxState.value = auxState.value.copy(errorMessage = err.message ?: "Admin withdrawal failed")
            }
        }
    }

    fun creditUserByAdmin(
        targetEmail: String,
        amount: Double,
        includeCommission: Boolean,
        deductFromAdminPool: Boolean,
        note: String
    ) {
        viewModelScope.launch {
            val result = repository.creditUserByAdmin(
                targetEmail = targetEmail,
                amount = amount,
                includeCommission = includeCommission,
                deductFromAdminPool = deductFromAdminPool,
                note = note
            )
            result.onSuccess {
                auxState.value = auxState.value.copy(
                    errorMessage = null,
                    feedbackMessage = "Credited ${Formatters.formatInr(amount)} to ${targetEmail.trim().lowercase()}"
                )
            }.onFailure { err ->
                auxState.value = auxState.value.copy(
                    errorMessage = err.message ?: "Failed to credit user account"
                )
            }
        }
    }

    fun sendAdminSupportReply(userId: String, userEmail: String, message: String) {
        viewModelScope.launch {
            try {
                repository.sendAdminReplyToUser(userId, userEmail, message)
                auxState.value = auxState.value.copy(feedbackMessage = "Reply sent to $userEmail")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                auxState.value = auxState.value.copy(errorMessage = e.localizedMessage ?: "Reply failed")
            }
        }
    }

    fun showMessage(message: String) {
        auxState.value = auxState.value.copy(feedbackMessage = message)
    }

    fun clearFeedback() {
        auxState.value = auxState.value.copy(feedbackMessage = null, errorMessage = null)
    }

    private data class UserScopedBundle(
        val user: UserAccountEntity? = null,
        val transactions: List<TransactionEntity> = emptyList(),
        val payoutMethods: List<PayoutMethodEntity> = emptyList(),
        val alerts: List<NotificationAlertEntity> = emptyList(),
        val supportMessages: List<SupportMessageEntity> = emptyList(),
        val referrals: List<ReferralEntity> = emptyList(),
        val liveTicker: List<LiveTickerEntity> = emptyList(),
        val publicConfig: AdminVaultEntity = AdminVaultEntity()
    )

    private data class AdminBundle(
        val vault: AdminVaultEntity = AdminVaultEntity(),
        val pendingApprovals: List<TransactionEntity> = emptyList(),
        val allUsers: List<UserAccountEntity> = emptyList(),
        val allTransactions: List<TransactionEntity> = emptyList()
    )
}
