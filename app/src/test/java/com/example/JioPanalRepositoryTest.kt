package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.JioPanalDatabase
import com.example.data.model.AdminVaultEntity
import com.example.data.model.UserAccountEntity
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.JioPanalRepository
import com.example.util.NotificationHelper
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JioPanalRepositoryTest {

    private lateinit var db: JioPanalDatabase
    private lateinit var repository: JioPanalRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JioPanalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JioPanalRepository(
            dao = db.dao(),
            prefsRepo = UserPreferencesRepository(context),
            notificationHelper = NotificationHelper(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `activation approval credits 15 percent commission and updates user status`() = runBlocking {
        repository.ensureInitialized()
        val vault = repository.adminVaultFlow.first()!!
        assertEquals(200000.0, vault.commissionPoolBalance, 0.01)
        assertEquals("vishalkumar90@upi", vault.merchantUpiId)
        assertEquals("@Sainishaab9076", vault.telegramHandle)

        val email = "partner@gmail.com"
        val user = repository.completeGmailLogin(email)
        assertEquals(UserAccountEntity.STATUS_INACTIVE, user.status)

        repository.submitActivationOrDeposit(
            email = email,
            amount = 5000.0,
            utrNumber = "UTR4318290412",
            paymentMethod = "UPI",
            isActivation = true
        )

        val pendingUser = repository.observeUser(email).first()!!
        assertEquals(UserAccountEntity.STATUS_PENDING, pendingUser.status)

        val pendingTxs = repository.pendingTransactionsFlow.first()
        assertEquals(1, pendingTxs.size)

        assertTrue(repository.verifyAdminPasskey(AdminVaultEntity.DEFAULT_PASSKEY))
        repository.approveTransaction(pendingTxs.first().id)

        val activeUser = repository.observeUser(email).first()!!
        assertEquals(UserAccountEntity.STATUS_ACTIVE, activeUser.status)
        assertEquals(750.0, activeUser.commissionEarned, 0.01)
        assertEquals(5750.0, activeUser.walletBalance, 0.01)
        assertEquals(5000.0, activeUser.totalDeposited, 0.01)

        val withdrawResult = repository.withdrawAdminCommission(50000.0, "vishalkumar90@upi", email)
        assertTrue(withdrawResult.isSuccess)
        val updatedVault = repository.adminVaultFlow.first()!!
        assertEquals(150750.0, updatedVault.commissionPoolBalance, 0.01)
    }

    @Test
    fun `admin can credit another users account directly with optional 15 percent commission`() = runBlocking {
        repository.ensureInitialized()
        val otherEmail = "friendpartner@gmail.com"

        val resultWithComm = repository.creditUserByAdmin(
            targetEmail = otherEmail,
            amount = 5000.0,
            includeCommission = true,
            note = "Manual Activation Credit"
        )
        assertTrue(resultWithComm.isSuccess)

        val creditedUser = repository.observeUser(otherEmail).first()!!
        assertEquals(UserAccountEntity.STATUS_ACTIVE, creditedUser.status)
        assertEquals(5750.0, creditedUser.walletBalance, 0.01)
        assertEquals(750.0, creditedUser.commissionEarned, 0.01)

        val resultNoComm = repository.creditUserByAdmin(
            targetEmail = otherEmail,
            amount = 1000.0,
            includeCommission = false,
            note = "Direct Bonus"
        )
        assertTrue(resultNoComm.isSuccess)
        val updatedUser = repository.observeUser(otherEmail).first()!!
        assertEquals(6750.0, updatedUser.walletBalance, 0.01)
        assertEquals(750.0, updatedUser.commissionEarned, 0.01)
    }

    @Test
    fun `qr code generation succeeds for merchant upi`() {
        val qr = QrCodeGenerator.generateUpiQr(
            upiId = "vishalkumar90@upi",
            payeeName = "Jio Panal",
            amount = 5000.0,
            note = "JioPanalActivation"
        )
        assertNotNull(qr)
    }
}
