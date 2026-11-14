package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.AdminVaultEntity
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.JioPanalRepository
import com.example.data.repository.awaitTask
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.initialize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import java.net.HttpURLConnection
import java.net.URL

abstract class FirestoreEmulatorTestBase {

    protected lateinit var context: Context
    protected lateinit var auth: FirebaseAuth
    protected lateinit var db: FirebaseFirestore
    protected lateinit var repository: JioPanalRepository

    protected lateinit var projectId: String
    protected lateinit var databaseId: String

    @Before
    fun setUpFirebaseEmulators() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            Firebase.initialize(context)
        }
        projectId = Firebase.app.options.projectId ?: "gen-lang-client-0728630889"
        databaseId = context.getString(R.string.firestore_database_id)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance(databaseId)

        if (!emulatorsBound) {
            auth.useEmulator("127.0.0.1", 9099)
            db.useEmulator("127.0.0.1", 8085)
            db.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(memoryCacheSettings {})
            }
            emulatorsBound = true
        }

        repository = JioPanalRepository(
            db = db,
            auth = auth,
            prefsRepo = UserPreferencesRepository(context),
            notificationHelper = NotificationHelper(context)
        )

        clearEmulators()
    }

    @After
    fun tearDownFirebaseEmulators() {
        auth.signOut()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Runs suspend emulator test logic on Dispatchers.IO while pumping Robolectric's Main Looper
     * so internal Firebase Auth/Firestore Handler callbacks never deadlock the main thread.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    protected fun <T> runEmulatorBlocking(block: suspend CoroutineScope.() -> T): T {
        val deferred = CompletableDeferred<Result<T>>()
        CoroutineScope(Dispatchers.IO).launch {
            val res = try {
                Result.success(block())
            } catch (t: Throwable) {
                Result.failure(t)
            }
            deferred.complete(res)
        }
        val deadline = System.currentTimeMillis() + 90_000L
        while (!deferred.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for Firebase emulator operation after 90s")
            }
            Thread.sleep(15)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return deferred.getCompleted().getOrThrow()
    }

    protected fun clearEmulators() {
        auth.signOut()
        shadowOf(Looper.getMainLooper()).idle()
        httpRequest(
            method = "DELETE",
            url = "http://127.0.0.1:8085/emulator/v1/projects/$projectId/databases/$databaseId/documents"
        )
        httpRequest(
            method = "DELETE",
            url = "http://127.0.0.1:9099/emulator/v1/projects/$projectId/accounts"
        )
    }

    protected suspend fun signInVerifiedUser(email: String, password: String = "Pass@1234"): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).awaitTask()
        val user = result.user!!
        verifyEmulatorAccount(user.uid)
        user.getIdToken(true).awaitTask()
        return user
    }

    protected suspend fun signInUnverifiedUser(email: String, password: String = "Pass@1234"): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).awaitTask()
        return result.user!!
    }

    protected suspend fun signInVerifiedAdmin(): FirebaseUser =
        signInVerifiedUser(AdminVaultEntity.AUTHORIZED_ADMIN_EMAIL)

    protected suspend fun assertPermissionDenied(block: suspend () -> Unit) {
        var threwDenied = false
        try {
            block()
        } catch (e: FirebaseFirestoreException) {
            threwDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        } catch (e: Exception) {
            threwDenied = e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                (e.cause is FirebaseFirestoreException &&
                    (e.cause as FirebaseFirestoreException).code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
        assertTrue("Expected Firestore PERMISSION_DENIED exception", threwDenied)
    }

    private fun verifyEmulatorAccount(uid: String) {
        val body = """{"localId":"$uid","emailVerified":true}"""
        val code = httpRequest(
            method = "POST",
            url = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/$projectId/accounts:update",
            jsonBody = body
        )
        assertTrue("Expected Auth emulator verify HTTP 200, got $code", code in 200..299)
    }

    private fun httpRequest(method: String, url: String, jsonBody: String? = null): Int {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer owner")
            connectTimeout = 5000
            readTimeout = 5000
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        @Volatile
        private var emulatorsBound = false
    }
}
