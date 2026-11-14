package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun `splash transitions to google sign in screen with language switcher`() {
        // Advance past 1300ms splash
        composeRule.mainClock.advanceTimeBy(1700)
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("sign_in_google_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Verified Gmail Cloud Login").assertIsDisplayed()
        composeRule.onNodeWithTag("referral_code_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in_google_button").performScrollTo().assertIsDisplayed()

        // Switch language live to Hinglish
        composeRule.onNodeWithTag("lang_chip_hg").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Luxury Partner Commission Vault • 15% Guaranteed").assertIsDisplayed()
    }
}
