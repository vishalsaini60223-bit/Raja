package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.UserAccountEntity
import com.example.ui.JioPanalUiState
import com.example.ui.components.AccountStatusPill
import com.example.ui.components.LuxuryGlassCard
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings

@Composable
fun ProfileScreen(
    state: JioPanalUiState,
    strings: AppStrings,
    onSaveProfile: (name: String, phone: String, city: String, business: String) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onUpdatePin: (String) -> Unit,
    onOpenAlertsSheet: () -> Unit,
    onOpenAdminVault: () -> Unit,
    onLogout: () -> Unit
) {
    val user = state.user
    var fullName by rememberSaveable(user?.email) { mutableStateOf(user?.fullName ?: "") }
    var phone by rememberSaveable(user?.email) { mutableStateOf(user?.phone ?: "") }
    var city by rememberSaveable(user?.email) { mutableStateOf(user?.city ?: "") }
    var business by rememberSaveable(user?.email) { mutableStateOf(user?.businessName ?: "") }
    var showPinDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LuxuryGlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.fullName?.ifBlank { "Jio Panal Partner" } ?: "Partner",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user?.email ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AccountStatusPill(
                        status = user?.status ?: UserAccountEntity.STATUS_INACTIVE,
                        label = when (user?.status) {
                            UserAccountEntity.STATUS_ACTIVE -> strings.statusActive
                            UserAccountEntity.STATUS_PENDING -> strings.statusPending
                            else -> strings.statusInactive
                        }
                    )
                }
            }
        }

        // Personal Details Form
        item {
            LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Personal & Business Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City / State") },
                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = business,
                    onValueChange = { business = it },
                    label = { Text("Business / Panel Name") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onSaveProfile(fullName, phone, city, business) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_profile_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.saveProfileBtn, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Language & Appearance Settings
        item {
            LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = strings.languageLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.DarkMode, contentDescription = null)
                        Text(strings.darkModeLabel, style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = state.preferences.isDarkMode,
                        onCheckedChange = onToggleDarkMode
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Text(strings.biometricLockLabel, style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = state.preferences.biometricEnabled,
                        onCheckedChange = onToggleBiometric
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change PIN")
                    }
                    OutlinedButton(
                        onClick = onOpenAlertsSheet,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Alerts (${state.alerts.size})")
                    }
                }
            }
        }

        // Admin Vault Entry & Sign Out
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.isAuthorizedAdmin) {
                    Button(
                        onClick = onOpenAdminVault,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("profile_open_admin_button")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.adminVaultTitle, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.logoutBtn, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showPinDialog) {
        var newPin by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Update 4-Digit Security PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New 4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePin(newPin)
                        showPinDialog = false
                    }
                ) { Text("Update PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}
