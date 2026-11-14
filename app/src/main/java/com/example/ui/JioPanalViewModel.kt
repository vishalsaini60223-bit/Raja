package com.example.ui

import android.app.Application
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuthStage {
    SPLASH,
    EMAIL_INPUT,
    OTP_VERIFY,
    BIOMETRIC_LOCK,
    AUTHENTICATED
}

data class JioPanalUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val language: AppLanguage = AppLanguage.ENGLISH,
    val authStage: AuthStage = AuthStage.SPLASH,
    val pendingEmail: String = "",
    val pendingReferralCode: String = "",
    val activeOtpCode: String = "",
    val user: UserAccountEntity? = null,
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
    val isAdminUnlocked: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

private data class SessionAuxState(
    val splashDone: Boolean = false,
    val pendingEmail: String = "",
    val pendingReferralCode: String = "",
    val activeOtpCode: String = "",
    val isOtpStep: Boolean = false,
    val isSessionUnlocked: Boolean = false,
    val isAdminUnlocked: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class JioPanalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = JioPanalRepository(application.applicationContext)
    private val auxState = MutableStateFlow(SessionAuxState())

    private val userDataFlow = repository.preferencesFlow.flatMapLatest { prefs ->
        val email = prefs.loggedInEmail
        if (email.isNullOrBlank()) {
            flowOf(UserScopedBundle())
        } else {
            combine(
                repository.observeUser(email),
                repository.observeTransactions(email),
                repository.observePayoutMethods(email),
                repository.observeAlerts(email),
                repository.observeSupportMessages(email),
                repository.observeReferrals(email)
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                UserScopedBundle(
                    user = values[0] as UserAccountEntity?,
                    transactions = values[1] as List<TransactionEntity>,
                    payoutMethods = values[2] as List<PayoutMethodEntity>,
                    alerts = values[3] as List<NotificationAlertEntity>,
                    supportMessages = values[4] as List<SupportMessageEntity>,
                    referrals = values[5] as List<ReferralEntity>
                )
            }
        }
    }

    private val adminBundleFlow = combine(
        repository.adminVaultFlow,
        repository.pendingTransactionsFlow,
        repository.allUsersFlow,
        repository.allTransactionsFlow,
        repository.liveTickerFlow
    ) { vault, pending, users, allTx, ticker ->
        AdminBundle(
            vault = vault ?: AdminVaultEntity(),
            pendingApprovals = pending,
            allUsers = users,
            allTransactions = allTx,
            liveTicker = ticker
        )
    }

    val uiState: StateFlow<JioPanalUiState> = combine(
        repository.preferencesFlow,
        auxState,
        userDataFlow,
        adminBundleFlow
    ) { prefs, aux, userBundle, adminBundle ->
        val stage = when {
            !aux.splashDone -> AuthStage.SPLASH
            prefs.loggedInEmail.isNullOrBlank() -> {
                if (aux.isOtpStep) AuthStage.OTP_VERIFY else AuthStage.EMAIL_INPUT
            }
            prefs.biometricEnabled && !aux.isSessionUnlocked -> AuthStage.BIOMETRIC_LOCK
            else -> AuthStage.AUTHENTICATED
        }
        JioPanalUiState(
            isLoading = false,
            preferences = prefs,
            language = AppLanguage.fromCode(prefs.languageCode),
            authStage = stage,
            pendingEmail = aux.pendingEmail,
            pendingReferralCode = aux.pendingReferralCode,
            activeOtpCode = aux.activeOtpCode,
            user = userBundle.user,
            transactions = userBundle.transactions,
            payoutMethods = userBundle.payoutMethods,
            alerts = userBundle.alerts,
            unreadAlertCount = userBundle.alerts.count { !it.isRead },
            supportMessages = userBundle.supportMessages,
            referrals = userBundle.referrals,
            liveTicker = adminBundle.liveTicker,
            adminVault = adminBundle.vault,
            pendingApprovals = adminBundle.pendingApprovals,
            allUsers = adminBundle.allUsers,
            allTransactions = adminBundle.allTransactions,
            isAdminUnlocked = aux.isAdminUnlocked,
            feedbackMessage = aux.feedbackMessage,
            errorMessage = aux.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = JioPanalUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
    }

    fun finishSplash() {
        auxState.value = auxState.value.copy(splashDone = true)
    }

    fun requestGmailOtp(email: String, referralCode: String) {
        val clean = email.trim().lowercase()
        if (!clean.contains("@") || (!clean.endsWith("@gmail.com") && !clean.contains("."))) {
            auxState.value = auxState.value.copy(errorMessage = "Please enter a valid Gmail address (e.g. name@gmail.com)")
            return
        }
        val otp = repository.generateSixDigitOtp(clean)
        auxState.value = auxState.value.copy(
            pendingEmail = clean,
            pendingReferralCode = referralCode.trim().uppercase(),
            activeOtpCode = otp,
            isOtpStep = true,
            errorMessage = null,
            feedbackMessage = null
        )
    }

    fun resendOtp() {
        val email = auxState.value.pendingEmail
        if (email.isBlank()) return
        val otp = repository.generateSixDigitOtp(email)
        auxState.value = auxState.value.copy(
            activeOtpCode = otp,
            errorMessage = null,
            feedbackMessage = "New 6-digit OTP dispatched to $email"
        )
    }

    fun backToEmailInput() {
        auxState.value = auxState.value.copy(
            isOtpStep = false,
            activeOtpCode = "",
            errorMessage = null
        )
    }

    fun verifyOtpAndLogin(enteredOtp: String) {
        val expected = auxState.value.activeOtpCode
        if (enteredOtp.trim() != expected || expected.isBlank()) {
            auxState.value = auxState.value.copy(errorMessage = "Invalid 6-digit OTP. Please check your notification/OTP code.")
            return
        }
        val email = auxState.value.pendingEmail
        val refCode = auxState.value.pendingReferralCode
        viewModelScope.launch {
            auxState.value = auxState.value.copy(
                isSessionUnlocked = true,
                errorMessage = null
            )
            repository.completeGmailLogin(email, refCode)
            auxState.value = auxState.value.copy(
                isOtpStep = false,
                activeOtpCode = "",
                isSessionUnlocked = true,
                errorMessage = null,
                feedbackMessage = "Welcome to Jio Panal!"
            )
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

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            auxState.value = auxState.value.copy(
                splashDone = true,
                isOtpStep = false,
                isSessionUnlocked = false,
                isAdminUnlocked = false,
                feedbackMessage = "Signed out securely"
            )
        }
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
        val email = uiState.value.user?.email ?: return
        viewModelScope.launch {
            repository.updateProfile(email, fullName, phone, city, businessName)
            auxState.value = auxState.value.copy(feedbackMessage = "Personal details saved offline & synced")
        }
    }

    fun submitActivationOrDeposit(amount: Double, utr: String, paymentMethod: String, isActivation: Boolean) {
        val email = uiState.value.user?.email ?: return
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
            repository.submitActivationOrDeposit(email, amount, cleanUtr, paymentMethod, isActivation)
            auxState.value = auxState.value.copy(
                errorMessage = null,
                feedbackMessage = if (isActivation) {
                    "₹5,000 activation submitted! Pending Admin approval."
                } else {
                    "Deposit submitted for Admin approval."
                }
            )
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
        val email = uiState.value.user?.email ?: return
        if (methodType == "UPI" && !upiId.contains("@")) {
            auxState.value = auxState.value.copy(errorMessage = "Enter a valid UPI ID (e.g. name@okicici)")
            return
        }
        if (methodType == "BANK" && (accountNumber.length < 6 || ifscCode.length < 4)) {
            auxState.value = auxState.value.copy(errorMessage = "Enter valid bank account number and IFSC code")
            return
        }
        viewModelScope.launch {
            repository.addPayoutMethod(email, methodType, label.ifBlank { methodType }, upiId, bankName, accountNumber, ifscCode, holderName)
            auxState.value = auxState.value.copy(feedbackMessage = "Payout account added")
        }
    }

    fun setDefaultPayoutMethod(id: Long) {
        val email = uiState.value.user?.email ?: return
        viewModelScope.launch { repository.setDefaultPayoutMethod(email, id) }
    }

    fun deletePayoutMethod(id: Long) {
        viewModelScope.launch { repository.deletePayoutMethod(id) }
    }

    fun requestUserWithdrawal(amount: Double, destinationLabel: String) {
        val email = uiState.value.user?.email ?: return
        viewModelScope.launch {
            val result = repository.requestUserWithdrawal(email, amount, destinationLabel)
            result.onSuccess {
                auxState.value = auxState.value.copy(feedbackMessage = "Withdrawal processed to $destinationLabel")
            }.onFailure { err ->
                auxState.value = auxState.value.copy(errorMessage = err.message ?: "Withdrawal failed")
            }
        }
    }

    fun addReferral(friendName: String, friendEmail: String) {
        val email = uiState.value.user?.email ?: return
        if (friendName.isBlank() || !friendEmail.contains("@")) {
            auxState.value = auxState.value.copy(errorMessage = "Enter friend's name and valid email")
            return
        }
        viewModelScope.launch {
            repository.addReferral(email, friendName, friendEmail)
            auxState.value = auxState.value.copy(feedbackMessage = "Referral recorded! 15% bonus activates on deposit.")
        }
    }

    fun sendSupportMessage(text: String) {
        val email = uiState.value.user?.email ?: return
        viewModelScope.launch {
            repository.sendSupportMessage(email, text)
        }
    }

    fun markAlertsRead() {
        val email = uiState.value.user?.email ?: return
        viewModelScope.launch { repository.markAlertsRead(email) }
    }

    // Admin Controls
    fun verifyAdminPasskey(passkey: String) {
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
            repository.updateAdminPasskey(newKey)
            auxState.value = auxState.value.copy(feedbackMessage = "Admin Passkey updated")
        }
    }

    fun approvePendingTx(txId: Long) {
        viewModelScope.launch {
            repository.approveTransaction(txId)
            auxState.value = auxState.value.copy(feedbackMessage = "Approved! User activated & 15% commission credited.")
        }
    }

    fun rejectPendingTx(txId: Long) {
        viewModelScope.launch {
            repository.rejectTransaction(txId)
            auxState.value = auxState.value.copy(feedbackMessage = "Request declined.")
        }
    }

    fun setUserStatusByAdmin(email: String, status: String) {
        viewModelScope.launch {
            repository.setUserStatusByAdmin(email, status)
            auxState.value = auxState.value.copy(feedbackMessage = "Updated $email to $status")
        }
    }

    fun withdrawAdminCommission(amount: Double, destination: String) {
        if (destination.isBlank()) {
            auxState.value = auxState.value.copy(errorMessage = "Enter a destination UPI or Bank account")
            return
        }
        viewModelScope.launch {
            val result = repository.withdrawAdminCommission(amount, destination, uiState.value.user?.email)
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
                    feedbackMessage = "Credited ${com.example.util.Formatters.formatInr(amount)} to ${targetEmail.trim().lowercase()}"
                )
            }.onFailure { err ->
                auxState.value = auxState.value.copy(
                    errorMessage = err.message ?: "Failed to credit user account"
                )
            }
        }
    }

    fun sendAdminSupportReply(userEmail: String, message: String) {
        viewModelScope.launch {
            repository.sendAdminReplyToUser(userEmail, message)
            auxState.value = auxState.value.copy(feedbackMessage = "Reply sent to $userEmail")
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
        val referrals: List<ReferralEntity> = emptyList()
    )

    private data class AdminBundle(
        val vault: AdminVaultEntity = AdminVaultEntity(),
        val pendingApprovals: List<TransactionEntity> = emptyList(),
        val allUsers: List<UserAccountEntity> = emptyList(),
        val allTransactions: List<TransactionEntity> = emptyList(),
        val liveTicker: List<LiveTickerEntity> = emptyList()
    )
}
