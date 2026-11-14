package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AdminVaultEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.UserAccountEntity
import com.example.util.QrCodeGenerator
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JioPanalRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun `multi-device sync flow, activation approval, withdrawal queue, and credit work across users and admin`() = runEmulatorBlocking {
        // 1. User A signs in on Phone A
        val fbUserA = signInVerifiedUser("partnera@gmail.com")
        val syncedA = repository.syncSignedInUser(fbUserA).getOrThrow()
        assertEquals(UserAccountEntity.STATUS_INACTIVE, syncedA.status)
        assertEquals(1, syncedA.loginCount)

        // User A cannot self-activate or tamper balance
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_USERS).document(fbUserA.uid)
                .update("status", UserAccountEntity.STATUS_ACTIVE)
                .awaitTask()
        }
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_USERS).document(fbUserA.uid)
                .update("walletBalance", 50000.0)
                .awaitTask()
        }

        // User A submits ₹5,000 activation request
        repository.submitActivationOrDeposit(
            user = syncedA,
            amount = 5000.0,
            utrNumber = "UTR88776655",
            paymentMethod = "UPI",
            isActivation = true
        )
        val pendingA = repository.observeUser(fbUserA.uid).first { it?.status == UserAccountEntity.STATUS_PENDING }!!
        assertEquals(UserAccountEntity.STATUS_PENDING, pendingA.status)

        // 2. Admin signs in on Phone B and sees User A + Pending Activation live!
        auth.signOut()
        val fbAdmin = signInVerifiedAdmin()
        repository.syncSignedInUser(fbAdmin).getOrThrow()

        assertTrue(repository.verifyAdminPasskey(AdminVaultEntity.DEFAULT_PASSKEY))

        val visibleUsers = repository.observeAllUsers().first { list -> list.any { it.email == "partnera@gmail.com" } }
        assertTrue(visibleUsers.any { it.email == "partnera@gmail.com" })

        val pendingQueue = repository.observePendingTransactions().first { it.isNotEmpty() }
        val activationTx = pendingQueue.first { it.userId == fbUserA.uid && it.type == TransactionEntity.TYPE_ACTIVATION }

        // Admin approves User A's activation
        val approveRes = repository.approveTransaction(activationTx.id)
        assertTrue(approveRes.isSuccess)

        val activatedA = repository.observeUser(fbUserA.uid).first { it?.status == UserAccountEntity.STATUS_ACTIVE }!!
        assertEquals(5750.0, activatedA.walletBalance, 0.01)
        assertEquals(750.0, activatedA.commissionEarned, 0.01)

        // 3. Switch back to User A and request ₹1,000 withdrawal
        auth.signOut()
        auth.signInWithEmailAndPassword("partnera@gmail.com", "Pass@1234").awaitTask()
        val wdRes1 = repository.requestUserWithdrawal(activatedA, 1000.0, "partnera@upi")
        assertTrue(wdRes1.isSuccess)

        // Cannot exceed available balance
        val overWd = repository.requestUserWithdrawal(activatedA, 5000.0, "partnera@upi")
        assertTrue(overWd.isFailure)

        // 4. Switch to Admin and reject ₹1,000 withdrawal -> then approve a ₹500 withdrawal
        auth.signOut()
        auth.signInWithEmailAndPassword(AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL, "Pass@1234").awaitTask()
        val wdPending1 = repository.observePendingTransactions().first { it.isNotEmpty() }.first()
        assertTrue(repository.rejectTransaction(wdPending1.id).isSuccess)
        assertEquals(5750.0, repository.observeUser(fbUserA.uid).first()!!.walletBalance, 0.01)

        // User A creates second withdrawal ₹500
        auth.signOut()
        auth.signInWithEmailAndPassword("partnera@gmail.com", "Pass@1234").awaitTask()
        assertTrue(repository.requestUserWithdrawal(activatedA, 500.0, "partnera@upi").isSuccess)

        // Admin approves ₹500 withdrawal and directly credits User A ₹1,000 without commission
        auth.signOut()
        auth.signInWithEmailAndPassword(AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL, "Pass@1234").awaitTask()
        val wdPending2 = repository.observePendingTransactions().first { it.isNotEmpty() }.first()
        assertTrue(repository.approveTransaction(wdPending2.id).isSuccess)
        assertEquals(5250.0, repository.observeUser(fbUserA.uid).first()!!.walletBalance, 0.01)

        val creditRes = repository.creditUserByAdmin(
            targetEmail = "partnera@gmail.com",
            amount = 1000.0,
            includeCommission = false,
            deductFromAdminPool = true,
            note = "Admin Cloud Bonus"
        )
        assertTrue(creditRes.isSuccess)
        assertEquals(6250.0, repository.observeUser(fbUserA.uid).first()!!.walletBalance, 0.01)
    }

    @Test
    fun `security rules block unauthenticated reads, cross-user access, and spoofed admin email`() = runEmulatorBlocking {
        // Seed User A
        val userA = signInVerifiedUser("alice@gmail.com")
        repository.syncSignedInUser(userA).getOrThrow()

        // Unauthenticated read fails
        auth.signOut()
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_USERS).document(userA.uid).get().awaitTask()
        }
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID).get().awaitTask()
        }

        // Cross-user User B cannot read User A or list all users
        val userB = signInVerifiedUser("bob@gmail.com")
        repository.syncSignedInUser(userB).getOrThrow()
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_USERS).document(userA.uid).get().awaitTask()
        }
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_USERS).get().awaitTask()
        }
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_TRANSACTIONS).get().awaitTask()
        }

        // Unverified spoofed Admin email MUST fail Admin vault check & all-users list!
        auth.signOut()
        signInUnverifiedUser(AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL)
        assertFalse(repository.verifyAdminPasskey(AdminVaultEntity.DEFAULT_PASSKEY))
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_USERS).get().awaitTask()
        }
        assertPermissionDenied {
            db.collection(JioPanalRepository.COL_ADMIN_CONFIG).document(AdminVaultEntity.SINGLETON_ID)
                .set(mapOf("adminPasskey" to "HACKED", "updatedAt" to FieldValue.serverTimestamp()))
                .awaitTask()
        }
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
