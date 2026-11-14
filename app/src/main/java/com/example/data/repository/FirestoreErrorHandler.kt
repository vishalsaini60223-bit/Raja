package com.example.data.repository

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    GET,
    LIST,
    WRITE
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handleFirestoreError(
        error: Throwable,
        operationType: OperationType,
        path: String?,
        auth: FirebaseAuth
    ): Nothing {
        if (error is CancellationException) throw error
        val user = auth.currentUser
        val authJson = JSONObject().apply {
            put("userId", user?.uid ?: JSONObject.NULL)
            put("email", user?.email ?: JSONObject.NULL)
            put("emailVerified", user?.isEmailVerified ?: false)
            put("isAnonymous", user?.isAnonymous ?: false)
            put("providerIds", JSONArray(user?.providerData?.map { it.providerId } ?: emptyList<String>()))
        }
        val payload = JSONObject().apply {
            put("error", error.message ?: error.javaClass.simpleName)
            put("operationType", operationType.name.lowercase())
            put("path", path ?: JSONObject.NULL)
            put("authInfo", authJson)
        }
        Log.e(TAG, payload.toString(), error)
        throw error
    }
}

private val directExecutor = java.util.concurrent.Executor { it.run() }

suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener(directExecutor) { result ->
        if (cont.isActive) cont.resume(result)
    }.addOnFailureListener(directExecutor) { e ->
        if (cont.isActive) cont.resumeWithException(e)
    }.addOnCanceledListener(directExecutor) {
        if (cont.isActive) cont.cancel()
    }
}
