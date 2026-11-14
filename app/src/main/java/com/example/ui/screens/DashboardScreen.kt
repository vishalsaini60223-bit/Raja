package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TransactionEntity
import com.example.data.model.UserAccountEntity
import com.example.ui.JioPanalUiState
import com.example.ui.components.AccountStatusPill
import com.example.ui.components.CommissionRibbon
import com.example.ui.components.LiveDepositCreditTicker
import com.example.ui.components.LuxuryGlassCard
import com.example.ui.components.QuickActionTile
import com.example.ui.components.SmsAlertPreviewBanner
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberPending
import com.example.ui.theme.EmeraldCredit
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldBright
import com.example.ui.theme.RubyAlert
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    state: JioPanalUiState,
    strings: AppStrings,
    onNavigateActivate: () -> Unit,
    onNavigateAccounts: () -> Unit,
    onNavigateLedger: () -> Unit,
    onNavigateSupport: () -> Unit,
    onOpenAdminVault: () -> Unit,
    onOpenAlertsSheet: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleRunPanal: () -> Unit
) {
    val user = state.user
    val isUserActive = user?.status == UserAccountEntity.STATUS_ACTIVE
    val statusLabel = when (user?.status) {
        UserAccountEntity.STATUS_ACTIVE -> strings.statusActive
        UserAccountEntity.STATUS_PENDING -> strings.statusPending
        UserAccountEntity.STATUS_SUSPENDED -> strings.statusSuspended
        else -> strings.statusInactive
    }

    var liveClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var feedIndex by remember { mutableIntStateOf(0) }
    var showActivationRequiredDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isPanelRunning, state.transactions.size) {
        if (state.isPanelRunning && isUserActive) {
            var tick = 0
            while (true) {
                liveClockMillis = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
                tick++
                if (tick % 3 == 0 && state.transactions.isNotEmpty()) {
                    feedIndex = (feedIndex + 1) % state.transactions.size
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0A0D14),
                        border = BorderStroke(1.dp, RoyalGold),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_jio_panal_fg),
                            contentDescription = "Jio Panal",
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user?.fullName?.ifBlank { user.email } ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (state.preferences.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = strings.darkModeLabel,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenAlertsSheet,
                        modifier = Modifier.testTag("notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.unreadAlertCount > 0) {
                                    Badge { Text("${state.unreadAlertCount}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Live Alerts"
                            )
                        }
                    }
                    if (state.isAuthorizedAdmin) {
                        IconButton(
                            onClick = onOpenAdminVault,
                            modifier = Modifier.testTag("admin_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = strings.adminVaultTitle,
                                tint = RoyalGoldBright
                            )
                        }
                    }
                }
            }
        }

        // Live Recorded Transaction Feed (when RUN Panal is active)
        if (state.isPanelRunning && isUserActive) {
            item {
                val activeTx = state.transactions.getOrNull(
                    if (state.transactions.isNotEmpty()) feedIndex % state.transactions.size else 0
                )
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = EmeraldCredit.copy(alpha = 0.14f),
                    border = BorderStroke(1.2.dp, EmeraldCredit.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_panal_live_feed")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "● RUN PANAL • LIVE RECORDED LEDGER",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldCredit,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = Formatters.formatDateTime(liveClockMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activeTx != null) {
                            val isCredit = activeTx.type == TransactionEntity.TYPE_COMMISSION ||
                                activeTx.type == TransactionEntity.TYPE_REFERRAL ||
                                activeTx.type == TransactionEntity.TYPE_DEPOSIT ||
                                activeTx.type == TransactionEntity.TYPE_ACTIVATION
                            Text(
                                text = "${activeTx.title} • ${(if (isCredit) "+" else "-") + Formatters.formatInr(activeTx.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCredit) EmeraldCredit else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${activeTx.subtitle} • Status: ${activeTx.status} • ${Formatters.formatDateTime(activeTx.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Account ACTIVE • Wallet: ${Formatters.formatInr(user?.walletBalance ?: 0.0)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldCredit
                            )
                            Text(
                                text = "Live feed ready. Your recorded deposits, 15% commission credits & withdrawals will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Live Deposit & Credit Scrolling Ticker
        item {
            LiveDepositCreditTicker(
                items = state.liveTicker,
                prefixLabel = strings.liveTickerPrefix
            )
        }

        // Luxury Hero Wallet Card
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.2.dp, RoyalGold.copy(alpha = 0.7f)),
                color = Color(0xFF0E131F),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.img_luxury_banner),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.55f),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xD9090B10),
                                        Color(0xA6121621)
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CommissionRibbon(strings.commissionBadge)
                            AccountStatusPill(
                                status = user?.status ?: UserAccountEntity.STATUS_INACTIVE,
                                label = statusLabel
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.totalBalance,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = Formatters.formatInr(user?.walletBalance ?: 0.0),
                            style = MaterialTheme.typography.displaySmall,
                            color = RoyalGoldBright,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.07f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = strings.commissionEarned,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = Formatters.formatInr(user?.commissionEarned ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldCredit,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.07f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = strings.totalDeposited,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = Formatters.formatInr(user?.totalDeposited ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (user?.status != UserAccountEntity.STATUS_ACTIVE) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onNavigateActivate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalGold,
                                    contentColor = Color(0xFF140F02)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("hero_activate_button")
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (user?.status == UserAccountEntity.STATUS_PENDING) {
                                        "Activation Pending Approval (₹5,000)"
                                    } else {
                                        "Activate Account • ₹5,000 UPI / QR"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // RUN PANAL Control Card (Works only when Account is ACTIVE)
        item {
            LuxuryGlassCard(
                highlightGold = isUserActive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isPanelRunning && isUserActive) "Jio Panal Running Live" else "RUN Panal Control",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUserActive) EmeraldCredit else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isUserActive) {
                                "Active ID verified • Live recorded credits, deposits & payout feed"
                            } else {
                                "Locked: Activate account (₹5,000 + Admin Approval) to run panel"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (isUserActive) {
                                onToggleRunPanal()
                            } else {
                                showActivationRequiredDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                !isUserActive -> MaterialTheme.colorScheme.surfaceVariant
                                state.isPanelRunning -> RubyAlert
                                else -> EmeraldCredit
                            },
                            contentColor = when {
                                !isUserActive -> MaterialTheme.colorScheme.onSurfaceVariant
                                state.isPanelRunning -> Color.White
                                else -> Color(0xFF04140C)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("run_panal_button")
                    ) {
                        Icon(
                            imageVector = when {
                                !isUserActive -> Icons.Default.Lock
                                state.isPanelRunning -> Icons.Default.Stop
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                !isUserActive -> "RUN Panal"
                                state.isPanelRunning -> "STOP"
                                else -> "RUN Panal"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick Actions Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    icon = Icons.Default.QrCode2,
                    label = "Activate ₹5K",
                    accent = RoyalGoldBright,
                    onClick = onNavigateActivate,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Default.AccountBalance,
                    label = "Add Account",
                    accent = EmeraldCredit,
                    onClick = onNavigateAccounts,
                    modifier = Modifier.weight(1f)
                )
                QuickActionTile(
                    icon = Icons.Default.GroupAdd,
                    label = "Refer 15%",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onNavigateSupport,
                    modifier = Modifier.weight(1f)
                )
                if (state.isAuthorizedAdmin) {
                    QuickActionTile(
                        icon = Icons.Default.AdminPanelSettings,
                        label = "Admin Key",
                        accent = AmberPending,
                        onClick = onOpenAdminVault,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    QuickActionTile(
                        icon = Icons.AutoMirrored.Filled.Send,
                        label = "Telegram",
                        accent = AmberPending,
                        onClick = onNavigateSupport,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Latest Live Credit / Deposit SMS Alert Banner
        state.alerts.firstOrNull()?.let { latestAlert ->
            item {
                SmsAlertPreviewBanner(
                    title = latestAlert.title,
                    body = latestAlert.smsBody,
                    onClick = onOpenAlertsSheet
                )
            }
        }

        // Dual Highlight Cards: Telegram + 24/7 Support & Admin Commission Pool Info
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LuxuryGlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateSupport
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Telegram Support",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@Sainishaab9076 • 24/7 Chat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.isAuthorizedAdmin) {
                    LuxuryGlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAdminVault
                    ) {
                        Icon(
                            imageVector = Icons.Default.CurrencyRupee,
                            contentDescription = null,
                            tint = EmeraldCredit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Admin Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Secure Key • Withdraw & Approve",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LuxuryGlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateLedger
                    ) {
                        Icon(
                            imageVector = Icons.Default.CurrencyRupee,
                            contentDescription = null,
                            tint = EmeraldCredit
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "15% Payouts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Offline Ledger & Instant Withdraw",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Offline Transaction History Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = strings.transactionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = EmeraldCredit.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = EmeraldCredit,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = strings.offlineReadyBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldCredit
                            )
                        }
                    }
                }
                TextButton(onClick = onNavigateLedger) {
                    Text("View All")
                }
            }
        }

        if (state.transactions.isEmpty()) {
            item {
                LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Activate your account with ₹5,000 via vishalkumar90@upi to start earning 15% commission!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.transactions.take(5), key = { it.id }) { tx ->
                TransactionRowCard(tx = tx)
            }
        }
    }

    if (showActivationRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showActivationRequiredDialog = false },
            title = { Text("Account Activation Required") },
            text = {
                Text(
                    text = "RUN Panal sirf ACTIVE accounts par chalta hai. Pehle ₹5,000 se account activate karein aur Admin approval ke baad try karein.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showActivationRequiredDialog = false
                        onNavigateActivate()
                    },
                    modifier = Modifier.testTag("activate_from_locked_dialog")
                ) {
                    Text("Activate ₹5,000")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showActivationRequiredDialog = false },
                    modifier = Modifier.testTag("close_locked_dialog_button")
                ) {
                    Text("Close")
                }
            },
            modifier = Modifier.testTag("run_panal_locked_dialog")
        )
    }
}

@Composable
fun TransactionRowCard(tx: TransactionEntity) {
    val isCredit = tx.type == TransactionEntity.TYPE_COMMISSION ||
        tx.type == TransactionEntity.TYPE_REFERRAL ||
        tx.type == TransactionEntity.TYPE_DEPOSIT ||
        tx.type == TransactionEntity.TYPE_ACTIVATION

    val statusColor = when (tx.status) {
        TransactionEntity.STATUS_SUCCESS -> EmeraldCredit
        TransactionEntity.STATUS_PENDING -> AmberPending
        else -> RubyAlert
    }

    LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = tx.title,
                    tint = statusColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tx.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatters.formatDateTime(tx.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isCredit) "+" else "-") + Formatters.formatInr(tx.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCredit) EmeraldCredit else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = tx.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
