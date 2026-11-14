package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.UserAccountEntity
import com.example.ui.JioPanalUiState
import com.example.ui.components.AccountStatusPill
import com.example.ui.components.LuxuryGlassCard
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.EmeraldCredit
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldBright
import com.example.util.Formatters
import com.example.util.QrCodeGenerator

@Composable
fun ActivateAndPayScreen(
    state: JioPanalUiState,
    strings: AppStrings,
    onSubmitPayment: (amount: Double, utr: String, method: String, isActivation: Boolean) -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val vault = state.adminVault
    val user = state.user
    val merchantUpi = vault.merchantUpiId // vishalkumar90@upi
    val activationFee = vault.activationAmount // 5000.0

    var isActivationMode by rememberSaveable { mutableStateOf(true) }
    var customAmountText by rememberSaveable { mutableStateOf("5000") }
    var selectedGateway by rememberSaveable { mutableStateOf("UPI") }
    var utrInput by rememberSaveable { mutableStateOf("") }

    val payAmount = if (isActivationMode) activationFee else (customAmountText.toDoubleOrNull() ?: 0.0)
    val estimatedCommission = payAmount * (vault.commissionRatePercent / 100.0)

    val qrBitmap = remember(merchantUpi, payAmount) {
        QrCodeGenerator.generateUpiQr(
            upiId = merchantUpi,
            payeeName = "Jio Panal",
            amount = if (payAmount > 0) payAmount else activationFee,
            note = if (isActivationMode) "JioPanalActivation" else "JioPanalDeposit"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.activateAccountTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instant UPI / QR Gateway • 15% Commission",
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

        // Mode Switcher: Activation (₹5,000) vs Custom Deposit
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = isActivationMode,
                    onClick = { isActivationMode = true },
                    label = { Text("Activate Account (₹5,000)") },
                    leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = !isActivationMode,
                    onClick = { isActivationMode = false },
                    label = { Text("Gateway Deposit") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // QR Code + Merchant UPI Card
        item {
            LuxuryGlassCard(highlightGold = true, modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isActivationMode) strings.activationAmountLabel else "Deposit Amount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatters.formatInr(payAmount),
                        style = MaterialTheme.typography.displaySmall,
                        color = RoyalGoldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = EmeraldCredit.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "+${Formatters.formatInr(estimatedCommission)} (15% Commission Bonus)",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldCredit,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    if (!isActivationMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customAmountText,
                            onValueChange = { if (it.all { ch -> ch.isDigit() || ch == '.' }) customAmountText = it },
                            label = { Text("Enter Deposit Amount (INR)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scannable QR Frame
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(2.dp, RoyalGold),
                        modifier = Modifier.size(210.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "UPI QR Code for $merchantUpi",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "QR",
                                    tint = Color.Black,
                                    modifier = Modifier.size(120.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scan QR with GPay, PhonePe, Paytm or BHIM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Copyable Merchant UPI ID
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Official Merchant UPI ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = merchantUpi,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Merchant UPI ID", merchantUpi))
                                    onShowToast("UPI ID copied: $merchantUpi")
                                },
                                modifier = Modifier.testTag("copy_upi_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Launch Installed UPI App Gateway Intent
                    OutlinedButton(
                        onClick = {
                            val uriString = QrCodeGenerator.buildUpiUri(
                                upiId = merchantUpi,
                                payeeName = "Jio Panal",
                                amount = if (payAmount > 0) payAmount else activationFee,
                                note = if (isActivationMode) "JioPanalActivation" else "JioPanalDeposit"
                            )
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onShowToast("No UPI app detected on device. Scan QR or enter UTR below.")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("launch_upi_intent_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActivationMode) strings.payViaUpiApp else "Pay ${Formatters.formatInr(payAmount)} via UPI App",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Payment Gateway UTR Submission Form
        item {
            LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldCredit
                    )
                    Text(
                        text = "Payment Gateway Checkout & Verification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("UPI", "QR Pay", "IMPS / Card").forEach { mode ->
                        FilterChip(
                            selected = selectedGateway == mode,
                            onClick = { selectedGateway = mode },
                            label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = utrInput,
                    onValueChange = { utrInput = it },
                    label = { Text(strings.submitUtrLabel) },
                    placeholder = { Text("e.g. 431829041289") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("utr_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onSubmitPayment(payAmount, utrInput, selectedGateway, isActivationMode)
                        if (utrInput.trim().length >= 6) {
                            utrInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_utr_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.submitPaymentBtn, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "After submission, Admin manually verifies your UTR and activates your panel + credits your 15% commission bonus live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
