package com.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    private fun readDisplayedOtpCode(): String {
        var otpCode = ""
        val nodes = composeRule.onAllNodesWithText("Security Code:", substring = true)
            .fetchSemanticsNodes()
        for (node in nodes) {
            val textList = node.config.getOrNull(SemanticsProperties.Text)
            val fullText = textList?.joinToString(" ") { it.text } ?: ""
            val match = Regex("Security Code: (\\d{6})").find(fullText)
            if (match != null) {
                otpCode = match.groupValues[1]
                break
            }
        }
        check(otpCode.length == 6) { "Expected 6-digit OTP in preview, got '$otpCode'" }
        return otpCode
    }

    @Test
    fun `full login and panel navigation works without auto-back or crash`() {
        // 1. Advance past 1300ms splash
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()

        // 2. Non-admin Gmail login -> Admin Vault must be hidden
        composeRule.onNodeWithTag("gmail_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gmail_input").performTextInput("vishal@gmail.com")
        composeRule.onNodeWithTag("send_otp_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("otp_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("otp_input").performTextInput(readDisplayedOtpCode())
        composeRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("nav_activate"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Non-admin user must NOT see Admin Vault button
        composeRule.onNodeWithTag("admin_vault_button").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_activate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Account Activation & Pay").assertIsDisplayed()

        // Navigate to Ledger, Support, Profile & verify Admin button is hidden, then Sign Out
        composeRule.onNodeWithTag("nav_ledger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_support").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("telegram_support_button").assertIsDisplayed()
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

        // 3. Authorized Admin Gmail login -> Admin Vault must be visible & unlockable
        composeRule.onNodeWithTag("gmail_input").performScrollTo().performTextInput("vishalsaini60223@gmail.com")
        composeRule.onNodeWithTag("send_otp_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("otp_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("otp_input").performTextInput(readDisplayedOtpCode())
        composeRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("admin_vault_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("admin_vault_button").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_lazy_column")
            .performScrollToNode(hasTestTag("profile_open_admin_button"))
        composeRule.onNodeWithTag("profile_open_admin_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_passkey_input").performScrollTo().performTextInput("JIOADMIN2026")
        composeRule.onNodeWithTag("unlock_admin_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("admin_withdraw_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("admin_withdraw_button").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_add_money_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_credit_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_admin_credit_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_credit_tab").performClick()
        composeRule.waitForIdle()
    }
}
