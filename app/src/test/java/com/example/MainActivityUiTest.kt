package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `password login, admin vault lock, and active-gated run panal work cleanly`() {
        // 1. Advance past 1300ms splash
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()

        // 2. Non-admin Gmail + Password login -> Admin Vault hidden & RUN Panal blocked while INACTIVE
        composeRule.onNodeWithTag("gmail_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gmail_input").performTextInput("vishal@gmail.com")
        composeRule.onNodeWithTag("password_input").performScrollTo().performTextInput("secret123")
        composeRule.onNodeWithTag("login_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("nav_activate"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("admin_vault_button").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("run_panal_button"))
        composeRule.onNodeWithTag("run_panal_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("run_panal_locked_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("close_locked_dialog_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("run_panal_live_feed").assertDoesNotExist()

        composeRule.onNodeWithTag("nav_activate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Account Activation & Pay").assertIsDisplayed()

        // Navigate to Profile, confirm Admin button hidden, and Sign Out
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_open_admin_button").assertDoesNotExist()
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("logout_button"))
        composeRule.onNodeWithTag("logout_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("gmail_input"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Authorized Admin Gmail + Password login -> Admin Vault visible & unlockable
        composeRule.onNodeWithTag("gmail_input").performScrollTo().performTextInput("vishalsaini60223@gmail.com")
        composeRule.onNodeWithTag("password_input").performScrollTo().performTextInput("admin123")
        composeRule.onNodeWithTag("login_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("admin_vault_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("admin_vault_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_passkey_input").performScrollTo().performTextInput("JIOADMIN2026")
        composeRule.onNodeWithTag("unlock_admin_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("admin_withdraw_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Credit ₹5,000 to vishalsaini60223@gmail.com so account becomes ACTIVE
        composeRule.onNodeWithTag("admin_add_money_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_credit_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_admin_credit_button").performClick()
        composeRule.waitForIdle()

        // Return to Home and start RUN Panal on now-ACTIVE account
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("dashboard_lazy_column"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("run_panal_button"))
        composeRule.onNodeWithTag("run_panal_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("STOP")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("run_panal_live_feed"))
        composeRule.onNodeWithTag("run_panal_live_feed").assertIsDisplayed()
    }
}
