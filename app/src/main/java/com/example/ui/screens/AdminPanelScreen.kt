package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.UserAccountEntity
import com.example.ui.JioPanalUiState
import com.example.ui.components.AccountStatusPill
import com.example.ui.components.LuxuryGlassCard
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.EmeraldCredit
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldBright
import com.example.ui.theme.RubyAlert
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    state: JioPanalUiState,
    strings: AppStrings,
    onVerifyPasskey: (String) -> Unit,
    onLockAndExit: () -> Unit,
    onApproveTx: (Long) -> Unit,
    onRejectTx: (Long) -> Unit,
    onSetUserStatus: (String, String) -> Unit,
    onWithdrawAdminPool: (Double, String) -> Unit,
    onCreditUser: (targetEmail: String, amount: Double, includeCommission: Boolean, note: String) -> Unit,
    onUpdateAdminPasskey: (String) -> Unit,
    onSendAdminReply: (String, String) -> Unit
) {
    BackHandler {
        onLockAndExit()
    }

    var passkeyInput by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showWithdrawModal by rememberSaveable { mutableStateOf(false) }
    var showCreditUserModal by rememberSaveable { mutableStateOf(false) }
    var creditPrefillEmail by rememberSaveable { mutableStateOf("") }
    var showPasskeyModal by rememberSaveable { mutableStateOf(false) }
    var replyTargetEmail by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = RoyalGoldBright
                        )
                        Text(strings.adminVaultTitle, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLockAndExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isAdminUnlocked) {
                        IconButton(onClick = { showPasskeyModal = true }) {
                            Icon(Icons.Default.Key, contentDescription = "Change Admin Key")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!state.isAdminUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LuxuryGlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = RoyalGoldBright,
                            modifier = Modifier.height(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Secure Admin Key Verification",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter your Admin Passkey to control user approvals, ₹2,00,000 commission vault, and withdrawals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = passkeyInput,
                            onValueChange = { passkeyInput = it },
                            label = { Text("Admin Passkey") },
                            placeholder = { Text("Default: JIOADMIN2026") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_passkey_input")
                        )

                        if (!state.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onVerifyPasskey(passkeyInput) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("unlock_admin_button")
                        ) {
                            Text("Unlock Admin Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Admin ₹2,00,000 Commission Pool Vault Banner
                LuxuryGlassCard(
                    highlightGold = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.adminCommissionPool,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatInr(state.adminVault.commissionPoolBalance),
                            style = MaterialTheme.typography.displaySmall,
                            color = RoyalGoldBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Commission Rate: ${state.adminVault.commissionRatePercent.toInt()}% • Activation: ₹${state.adminVault.activationAmount.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldCredit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    creditPrefillEmail = state.allUsers.firstOrNull()?.email ?: ""
                                    showCreditUserModal = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldCredit,
                                    contentColor = Color(0xFF04140C)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_add_money_button")
                            ) {
                                Icon(Icons.Default.AddCard, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Money", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Button(
                                onClick = { showWithdrawModal = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalGold,
                                    contentColor = Color(0xFF140F02)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_withdraw_button")
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.adminWithdrawBtn, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Approvals (${state.pendingApprovals.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Users (${state.allUsers.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("All Ledger") }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.pendingApprovals.isEmpty()) {
                                item {
                                    LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "No pending ₹5,000 activation or deposit approvals right now.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(state.pendingApprovals, key = { it.id }) { tx ->
                                    LuxuryGlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "${tx.title} • ${Formatters.formatInr(tx.amount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "User: ${tx.userEmail}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "UTR: ${tx.utrOrRef} • 15% Bonus: ${Formatters.formatInr(tx.commissionAmount)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = EmeraldCredit
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = { onApproveTx(tx.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldCredit),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("approve_tx_${tx.id}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.approveBtn)
                                            }
                                            OutlinedButton(
                                                onClick = { onRejectTx(tx.id) },
                                                border = BorderStroke(1.dp, RubyAlert),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = RubyAlert)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.rejectBtn, color = RubyAlert)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.allUsers, key = { it.email }) { user ->
                                LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.fullName.ifBlank { user.email },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = user.email,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Wallet: ${Formatters.formatInr(user.walletBalance)} • Comm: ${Formatters.formatInr(user.commissionEarned)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EmeraldCredit
                                            )
                                        }
                                        AccountStatusPill(status = user.status, label = user.status)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            UserAccountEntity.STATUS_ACTIVE,
                                            UserAccountEntity.STATUS_INACTIVE,
                                            UserAccountEntity.STATUS_SUSPENDED
                                        ).forEach { targetStatus ->
                                            FilterChip(
                                                selected = user.status == targetStatus,
                                                onClick = { onSetUserStatus(user.email, targetStatus) },
                                                label = { Text(targetStatus, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                creditPrefillEmail = user.email
                                                showCreditUserModal = true
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldCredit,
                                                contentColor = Color(0xFF04140C)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("user_add_money_${user.email}")
                                        ) {
                                            Icon(Icons.Default.AddCard, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add Money", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { replyTargetEmail = user.email },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Support Reply")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.allTransactions, key = { it.id }) { tx ->
                                TransactionRowCard(tx = tx)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWithdrawModal) {
        var amountText by rememberSaveable { mutableStateOf("") }
        var destination by rememberSaveable { mutableStateOf("vishalkumar90@upi") }
        AlertDialog(
            onDismissRequest = { showWithdrawModal = false },
            title = { Text("Withdraw Admin Commission Money") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Available Pool: ${Formatters.formatInr(state.adminVault.commissionPoolBalance)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldCredit
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amountText = it },
                        label = { Text("Withdrawal Amount (INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_withdraw_amount_input")
                    )
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("Destination UPI ID / Bank") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        onWithdrawAdminPool(amt, destination)
                        showWithdrawModal = false
                    },
                    modifier = Modifier.testTag("confirm_admin_withdraw_button")
                ) { Text("Withdraw Now") }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawModal = false }) { Text("Cancel") }
            }
        )
    }

    if (showPasskeyModal) {
        var newPasskey by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasskeyModal = false },
            title = { Text("Change Admin Passkey") },
            text = {
                OutlinedTextField(
                    value = newPasskey,
                    onValueChange = { newPasskey = it },
                    label = { Text("New Admin Key (min 6 chars)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAdminPasskey(newPasskey)
                        showPasskeyModal = false
                    }
                ) { Text("Save Key") }
            },
            dismissButton = {
                TextButton(onClick = { showPasskeyModal = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreditUserModal) {
        var targetEmail by rememberSaveable(creditPrefillEmail) { mutableStateOf(creditPrefillEmail) }
        var amountText by rememberSaveable { mutableStateOf("5000") }
        var includeCommission by rememberSaveable { mutableStateOf(true) }
        var noteText by rememberSaveable { mutableStateOf("") }

        val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
        val bonusPreview = if (includeCommission && parsedAmount > 0.0) {
            parsedAmount * (state.adminVault.commissionRatePercent / 100.0)
        } else {
            0.0
        }

        AlertDialog(
            onDismissRequest = { showCreditUserModal = false },
            title = { Text("Add Money to User Account") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (state.allUsers.isNotEmpty()) {
                        Text(
                            text = "Select Registered User",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.allUsers, key = { it.email }) { u ->
                                FilterChip(
                                    selected = targetEmail.equals(u.email, ignoreCase = true),
                                    onClick = { targetEmail = u.email },
                                    label = { Text(u.email, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = targetEmail,
                        onValueChange = { targetEmail = it },
                        label = { Text("User Gmail Address") },
                        placeholder = { Text("user@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_credit_email_input")
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amountText = it },
                        label = { Text("Amount to Add (INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_credit_amount_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("500", "1000", "5000", "10000").forEach { preset ->
                            FilterChip(
                                selected = amountText == preset,
                                onClick = { amountText = preset },
                                label = { Text("₹$preset", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include ${state.adminVault.commissionRatePercent.toInt()}% Commission",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Total Credit: ${Formatters.formatInr(parsedAmount + bonusPreview)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldCredit
                            )
                        }
                        Switch(
                            checked = includeCommission,
                            onCheckedChange = { includeCommission = it }
                        )
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note / Reason (Optional)") },
                        placeholder = { Text("e.g. Manual Deposit Credit") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_credit_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        onCreditUser(targetEmail, amt, includeCommission, noteText)
                        showCreditUserModal = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldCredit,
                        contentColor = Color(0xFF04140C)
                    ),
                    modifier = Modifier.testTag("confirm_admin_credit_button")
                ) { Text("Credit Wallet", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCreditUserModal = false }) { Text("Cancel") }
            }
        )
    }

    replyTargetEmail?.let { targetEmail ->
        var replyText by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replyTargetEmail = null },
            title = { Text("Reply to $targetEmail") },
            text = {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Admin Support Message") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSendAdminReply(targetEmail, replyText)
                        replyTargetEmail = null
                    }
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { replyTargetEmail = null }) { Text("Cancel") }
            }
        )
    }
}
