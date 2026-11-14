package com.example.util

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.example.data.repository.awaitTask
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    suspend fun signInInteractive(activityContext: Context): Result<FirebaseUser> {
        val credentialManager = CredentialManager.create(activityContext)
        val serverClientId = activityContext.getString(R.string.default_web_client_id)
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return executeCredentialRequest(credentialManager, activityContext, request)
    }

    suspend fun signInSilentIfAuthorized(activityContext: Context): Result<FirebaseUser?> {
        val existing = Firebase.auth.currentUser
        if (existing != null) return Result.success(existing)

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val serverClientId = activityContext.getString(R.string.default_web_client_id)
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val res = executeCredentialRequest(credentialManager, activityContext, request)
            if (res.isSuccess) Result.success(res.getOrNull()) else Result.success(null)
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (_: GetCredentialCancellationException) {
            Result.success(null)
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in skipped: ${e.message}")
            Result.success(null)
        }
    }

    private suspend fun executeCredentialRequest(
        credentialManager: CredentialManager,
        activityContext: Context,
        request: GetCredentialRequest
    ): Result<FirebaseUser> {
        return try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleToken.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCred).awaitTask()
                val firebaseUser = authResult.user
                    ?: return Result.failure(IllegalStateException("Google Sign-In did not return a Firebase user."))
                Result.success(firebaseUser)
            } else {
                Result.failure(IllegalStateException("Unsupported credential type returned."))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("Google Sign-In cancelled."))
        } catch (_: NoCredentialException) {
            Result.failure(
                IllegalStateException("No Google account found on device. Add a Gmail account in Android Settings and try again.")
            )
        } catch (e: Exception) {
            Log.e(TAG, "CredentialManager error", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Google Sign-In failed. Check SHA-1 or Play Services."))
        }
    }

    suspend fun signOutGoogle(context: Context) {
        withContext(NonCancellable) {
            try {
                Firebase.auth.signOut()
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing credential state", e)
            }
        }
    }
}
