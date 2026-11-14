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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.PayoutMethodEntity
import com.example.data.model.TransactionEntity
import com.example.ui.JioPanalUiState
import com.example.ui.components.LuxuryGlassCard
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.EmeraldCredit
import com.example.util.Formatters

@Composable
fun TransactionsAndAccountsScreen(
    state: JioPanalUiState,
    strings: AppStrings,
    initialTab: Int = 0,
    onAddPayoutMethod: (
        methodType: String,
        label: String,
        upiId: String,
        bankName: String,
        accountNumber: String,
        ifscCode: String,
        holderName: String
    ) -> Unit,
    onSetDefaultPayout: (Long) -> Unit,
    onDeletePayout: (Long) -> Unit,
    onWithdrawWallet: (Double, String) -> Unit
) {
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showWithdrawDialog by rememberSaveable { mutableStateOf(false) }

    val filteredTx = state.transactions.filter { tx ->
        val filterMatch = when (selectedFilter) {
            "CREDITS" -> tx.type == TransactionEntity.TYPE_COMMISSION || tx.type == TransactionEntity.TYPE_REFERRAL
            "DEPOSITS" -> tx.type == TransactionEntity.TYPE_DEPOSIT || tx.type == TransactionEntity.TYPE_ACTIVATION
            "WITHDRAWALS" -> tx.type == TransactionEntity.TYPE_WITHDRAWAL || tx.type == TransactionEntity.TYPE_ADMIN_WITHDRAWAL
            "PENDING" -> tx.status == TransactionEntity.STATUS_PENDING
            else -> true
        }
        val queryMatch = searchQuery.isBlank() ||
            tx.title.contains(searchQuery, ignoreCase = true) ||
            tx.utrOrRef.contains(searchQuery, ignoreCase = true) ||
            tx.subtitle.contains(searchQuery, ignoreCase = true)
        filterMatch && queryMatch
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.transactionsTitle) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Add Account & Payout") }
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Offline Transaction Ledger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EmeraldCredit.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = EmeraldCredit,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = strings.offlineReadyBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldCredit,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search UTR, title, or reference...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filters = listOf(
                            "ALL" to "All",
                            "CREDITS" to "15% Credits",
                            "DEPOSITS" to "Deposits",
                            "PENDING" to "Pending",
                            "WITHDRAWALS" to "Withdrawals"
                        )
                        items(filters) { (key, label) ->
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = { selectedFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (filteredTx.isEmpty()) {
                    item {
                        LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No matching offline transactions found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredTx, key = { it.id }) { tx ->
                        TransactionRowCard(tx = tx)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LuxuryGlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Withdrawable Balance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatInr(state.user?.walletBalance ?: 0.0),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { showWithdrawDialog = true },
                                enabled = (state.user?.walletBalance ?: 0.0) > 0.0 && state.payoutMethods.isNotEmpty(),
                                modifier = Modifier.testTag("user_withdraw_button")
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Withdraw")
                            }
                        }
                        if (state.payoutMethods.isEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add your UPI ID or Bank Account below to enable instant withdrawals.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.addAccountTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddAccountDialog = true },
                            modifier = Modifier.testTag("open_add_account_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Account")
                        }
                    }
                }

                if (state.payoutMethods.isEmpty()) {
                    item {
                        LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No UPI or Bank Account Added",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap 'Add Account' to save your personal UPI ID or Bank Account for payouts.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.payoutMethods, key = { it.id }) { method ->
                        PayoutMethodCard(
                            method = method,
                            onSetDefault = { onSetDefaultPayout(method.id) },
                            onDelete = { onDeletePayout(method.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddAccountDialog) {
        AddPayoutAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onSave = { type, label, upi, bank, accNo, ifsc, holder ->
                onAddPayoutMethod(type, label, upi, bank, accNo, ifsc, holder)
                showAddAccountDialog = false
            }
        )
    }

    if (showWithdrawDialog) {
        val defaultMethod = state.payoutMethods.firstOrNull { it.isDefault } ?: state.payoutMethods.firstOrNull()
        val destLabel = if (defaultMethod?.methodType == "UPI") {
            "UPI (${defaultMethod.upiId})"
        } else {
            "Bank (${defaultMethod?.bankName} • ${defaultMethod?.accountNumber?.takeLast(4)})"
        }
        var withdrawAmt by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("Withdraw to $destLabel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Available: ${Formatters.formatInr(state.user?.walletBalance ?: 0.0)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldCredit
                    )
                    OutlinedTextField(
                        value = withdrawAmt,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) withdrawAmt = it },
                        label = { Text("Amount (INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = withdrawAmt.toDoubleOrNull() ?: 0.0
                        onWithdrawWallet(amt, destLabel)
                        showWithdrawDialog = false
                    }
                ) {
                    Text("Confirm Payout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PayoutMethodCard(
    method: PayoutMethodEntity,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    LuxuryGlassCard(
        highlightGold = method.isDefault,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (method.methodType == "UPI") Icons.Default.QrCode else Icons.Default.AccountBalance,
                    contentDescription = method.methodType,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = method.label.ifBlank { method.methodType },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (method.isDefault) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = EmeraldCredit.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, EmeraldCredit.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "DEFAULT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldCredit,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (method.methodType == "UPI") {
                        Text(
                            text = method.upiId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "${method.bankName} • ${method.accountNumber} (${method.ifscCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row {
                if (!method.isDefault) {
                    IconButton(onClick = onSetDefault) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Set Default")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Account",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPayoutAccountDialog(
    onDismiss: () -> Unit,
    onSave: (
        methodType: String,
        label: String,
        upiId: String,
        bankName: String,
        accountNumber: String,
        ifscCode: String,
        holderName: String
    ) -> Unit
) {
    var type by rememberSaveable { mutableStateOf("UPI") }
    var label by rememberSaveable { mutableStateOf("") }
    var upiId by rememberSaveable { mutableStateOf("") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var ifsc by rememberSaveable { mutableStateOf("") }
    var holderName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add UPI or Bank Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "UPI",
                        onClick = { type = "UPI" },
                        label = { Text("UPI ID") }
                    )
                    FilterChip(
                        selected = type == "BANK",
                        onClick = { type = "BANK" },
                        label = { Text("Bank Account") }
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Account Nickname (e.g. Primary)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == "UPI") {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("Your UPI ID (e.g. name@okaxis)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_upi_input")
                    )
                } else {
                    OutlinedTextField(
                        value = holderName,
                        onValueChange = { holderName = it },
                        label = { Text("Account Holder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ifsc,
                        onValueChange = { ifsc = it },
                        label = { Text("IFSC Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(type, label, upiId, bankName, accountNumber, ifsc, holderName)
                },
                modifier = Modifier.testTag("save_payout_account_button")
            ) {
                Text("Save Account")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
