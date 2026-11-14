package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.components.LuxuryGlassCard
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.ActivateAndPayScreen
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GmailPasswordAuthScreen
import com.example.ui.screens.LuxurySplashScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReferralAndSupportScreen
import com.example.ui.screens.TransactionsAndAccountsScreen
import com.example.ui.theme.EmeraldCredit
import com.example.ui.theme.JioPanalTheme
import com.example.util.Formatters
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object ActivateRoute
@Serializable data class LedgerRoute(val initialTab: Int = 0)
@Serializable object SupportRoute
@Serializable object ProfileRoute
@Serializable object AdminRoute

private data class TopNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val routeObj: Any,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JioPanalApp(viewModel: JioPanalViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = stringsFor(state.language)
    val snackbarHostState = remember { SnackbarHostState() }
    var showAlertsSheet by rememberSaveable { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(state.authStage) {
        if (state.authStage == AuthStage.AUTHENTICATED &&
            !askedNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            askedNotificationPermission = true
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.feedbackMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    JioPanalTheme(darkTheme = state.preferences.isDarkMode) {
        when (state.authStage) {
            AuthStage.SPLASH -> {
                LuxurySplashScreen(onFinished = { viewModel.finishSplash() })
            }

            AuthStage.EMAIL_INPUT -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        GmailPasswordAuthScreen(
                            strings = strings,
                            selectedLanguage = state.language,
                            errorMessage = state.errorMessage,
                            onLanguageSelected = { viewModel.setLanguage(it) },
                            onLoginWithPassword = { email, pass, ref ->
                                viewModel.loginWithPassword(email, pass, ref)
                            }
                        )
                    }
                }
            }

            AuthStage.BIOMETRIC_LOCK -> {
                BiometricLockScreen(
                    email = state.preferences.loggedInEmail ?: "",
                    strings = strings,
                    errorMessage = state.errorMessage,
                    onBiometricUnlocked = { viewModel.unlockWithBiometricSuccess() },
                    onPinUnlock = { pin -> viewModel.unlockWithPin(pin) },
                    onSignOut = { viewModel.logout() }
                )
            }

            AuthStage.AUTHENTICATED -> {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                val isAdminScreen = currentDestination?.hasRoute<AdminRoute>() == true

                val navItems = listOf(
                    TopNavItem(strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, HomeRoute, "nav_home"),
                    TopNavItem(strings.navActivate, Icons.Filled.Bolt, Icons.Outlined.Bolt, ActivateRoute, "nav_activate"),
                    TopNavItem(strings.navLedger, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, LedgerRoute(0), "nav_ledger"),
                    TopNavItem(strings.navSupport, Icons.Filled.SupportAgent, Icons.Outlined.SupportAgent, SupportRoute, "nav_support"),
                    TopNavItem(strings.navProfile, Icons.Filled.Person, Icons.Outlined.Person, ProfileRoute, "nav_profile")
                )

                fun navigateTopLevel(route: Any) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets.safeDrawing,
                        bottomBar = {
                            if (!useRail && !isAdminScreen) {
                                NavigationBar {
                                    navItems.forEach { item ->
                                        val selected = when (item.routeObj) {
                                            is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                            is ActivateRoute -> currentDestination?.hasRoute<ActivateRoute>() == true
                                            is LedgerRoute -> currentDestination?.hasRoute<LedgerRoute>() == true
                                            is SupportRoute -> currentDestination?.hasRoute<SupportRoute>() == true
                                            is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                            else -> false
                                        }
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(item.routeObj) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label, maxLines = 1) },
                                            modifier = Modifier.testTag(item.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useRail && !isAdminScreen) {
                                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    navItems.forEach { item ->
                                        val selected = when (item.routeObj) {
                                            is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                            is ActivateRoute -> currentDestination?.hasRoute<ActivateRoute>() == true
                                            is LedgerRoute -> currentDestination?.hasRoute<LedgerRoute>() == true
                                            is SupportRoute -> currentDestination?.hasRoute<SupportRoute>() == true
                                            is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                            else -> false
                                        }
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(item.routeObj) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) }
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = HomeRoute,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    composable<HomeRoute> {
                                        DashboardScreen(
                                            state = state,
                                            strings = strings,
                                            onNavigateActivate = { navigateTopLevel(ActivateRoute) },
                                            onNavigateAccounts = {
                                                navController.navigate(LedgerRoute(initialTab = 1)) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            onNavigateLedger = { navigateTopLevel(LedgerRoute(0)) },
                                            onNavigateSupport = { navigateTopLevel(SupportRoute) },
                                            onOpenAdminVault = {
                                                navController.navigate(AdminRoute) { launchSingleTop = true }
                                            },
                                            onOpenAlertsSheet = {
                                                viewModel.markAlertsRead()
                                                showAlertsSheet = true
                                            },
                                            onToggleDarkMode = {
                                                viewModel.setDarkMode(!state.preferences.isDarkMode)
                                            },
                                            onToggleRunPanal = {
                                                viewModel.toggleRunPanal()
                                            }
                                        )
                                    }

                                    composable<ActivateRoute> {
                                        ActivateAndPayScreen(
                                            state = state,
                                            strings = strings,
                                            onSubmitPayment = { amt, utr, method, isAct ->
                                                viewModel.submitActivationOrDeposit(amt, utr, method, isAct)
                                            },
                                            onShowToast = { msg -> viewModel.showMessage(msg) }
                                        )
                                    }

                                    composable<LedgerRoute> { entry ->
                                        val route = entry.toRoute<LedgerRoute>()
                                        TransactionsAndAccountsScreen(
                                            state = state,
                                            strings = strings,
                                            initialTab = route.initialTab,
                                            onAddPayoutMethod = { type, label, upi, bank, acc, ifsc, holder ->
                                                viewModel.addPayoutMethod(type, label, upi, bank, acc, ifsc, holder)
                                            },
                                            onSetDefaultPayout = { viewModel.setDefaultPayoutMethod(it) },
                                            onDeletePayout = { viewModel.deletePayoutMethod(it) },
                                            onWithdrawWallet = { amt, dest -> viewModel.requestUserWithdrawal(amt, dest) }
                                        )
                                    }

                                    composable<SupportRoute> {
                                        ReferralAndSupportScreen(
                                            state = state,
                                            strings = strings,
                                            onAddReferral = { name, email -> viewModel.addReferral(name, email) },
                                            onSendSupportMessage = { viewModel.sendSupportMessage(it) },
                                            onShowToast = { msg -> viewModel.showMessage(msg) }
                                        )
                                    }

                                    composable<ProfileRoute> {
                                        ProfileScreen(
                                            state = state,
                                            strings = strings,
                                            onSaveProfile = { name, phone, city, biz ->
                                                viewModel.updateProfile(name, phone, city, biz)
                                            },
                                            onToggleDarkMode = { viewModel.setDarkMode(it) },
                                            onSelectLanguage = { viewModel.setLanguage(it) },
                                            onToggleBiometric = { viewModel.setBiometricEnabled(it) },
                                            onUpdatePin = { viewModel.updateSecurityPin(it) },
                                            onOpenAlertsSheet = {
                                                viewModel.markAlertsRead()
                                                showAlertsSheet = true
                                            },
                                            onOpenAdminVault = {
                                                navController.navigate(AdminRoute) { launchSingleTop = true }
                                            },
                                            onLogout = { viewModel.logout() }
                                        )
                                    }

                                    composable<AdminRoute> {
                                        AdminPanelScreen(
                                            state = state,
                                            strings = strings,
                                            onVerifyPasskey = { viewModel.verifyAdminPasskey(it) },
                                            onLockAndExit = {
                                                viewModel.lockAdminPanel()
                                                navController.popBackStack()
                                            },
                                            onApproveTx = { viewModel.approvePendingTx(it) },
                                            onRejectTx = { viewModel.rejectPendingTx(it) },
                                            onSetUserStatus = { email, status -> viewModel.setUserStatusByAdmin(email, status) },
                                            onWithdrawAdminPool = { amt, dest -> viewModel.withdrawAdminCommission(amt, dest) },
                                            onCreditUser = { email, amt, includeComm, deductPool, note ->
                                                viewModel.creditUserByAdmin(email, amt, includeComm, deductPool, note)
                                            },
                                            onUpdateAdminPasskey = { viewModel.updateAdminPasskey(it) },
                                            onSendAdminReply = { email, msg -> viewModel.sendAdminSupportReply(email, msg) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showAlertsSheet) {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = { showAlertsSheet = false },
                            sheetState = sheetState
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = EmeraldCredit
                                    )
                                    Text(
                                        text = "Live Deposit & Credit SMS Alerts",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (state.alerts.isEmpty()) {
                                        item {
                                            Text(
                                                text = "No live alerts yet.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        items(state.alerts, key = { it.id }) { alert ->
                                            LuxuryGlassCard(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = alert.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldCredit
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = alert.smsBody,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = Formatters.formatDateTime(alert.createdAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
