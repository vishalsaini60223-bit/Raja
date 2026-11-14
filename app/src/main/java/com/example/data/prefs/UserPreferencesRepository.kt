package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jio_panal_prefs")

data class UserPreferences(
    val loggedInEmail: String? = null,
    val isDarkMode: Boolean = true,
    val languageCode: String = "en",
    val biometricEnabled: Boolean = true,
    val securityPin: String = "1515"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val LOGGED_IN_EMAIL = stringPreferencesKey("logged_in_email")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SECURITY_PIN = stringPreferencesKey("security_pin")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            loggedInEmail = prefs[Keys.LOGGED_IN_EMAIL],
            isDarkMode = prefs[Keys.DARK_MODE] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            securityPin = prefs[Keys.SECURITY_PIN] ?: "1515"
        )
    }

    suspend fun setLoggedInEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.LOGGED_IN_EMAIL)
            } else {
                prefs[Keys.LOGGED_IN_EMAIL] = email.trim().lowercase()
            }
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setSecurityPin(pin: String) {
        context.dataStore.edit { it[Keys.SECURITY_PIN] = pin }
    }
}
