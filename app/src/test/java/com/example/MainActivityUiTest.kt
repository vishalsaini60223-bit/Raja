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

    @Test
    fun `full login and panel navigation works without auto-back or crash`() {
        // 1. Advance past 1300ms splash
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()

        // 2. Gmail input & Send OTP
        composeRule.onNodeWithTag("gmail_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("gmail_input").performTextInput("vishal@gmail.com")
        composeRule.onNodeWithTag("send_otp_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 3. Read OTP from the Security Code preview and verify login
        composeRule.onNodeWithTag("otp_input").performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithTag("otp_input").performTextInput(otpCode)
        composeRule.onNodeWithTag("verify_otp_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("admin_vault_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 4. Dashboard should now be visible and stay stable
        composeRule.onNodeWithTag("admin_vault_button").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_activate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Account Activation & Pay").assertIsDisplayed()

        // 5. Navigate to Ledger, Support, Profile
        composeRule.onNodeWithTag("nav_ledger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_support").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("telegram_support_button").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()

        // 6. Open Admin Vault & unlock with JIOADMIN2026
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
    }
}
