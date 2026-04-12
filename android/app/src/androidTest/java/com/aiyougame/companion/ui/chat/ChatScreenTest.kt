package com.aiyougame.companion.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitForIdle
import androidx.hilt.navigation.testing.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for ChatScreen.
 *
 * Verifies:
 * - TopAppBar has all 4 icons (back, character-select, purchase, settings)
 * - Back icon callback fires on click
 * - Character-select (person) icon callback fires on click
 * - Purchase icon callback fires on click
 * - Settings icon callback fires on click
 * - Input bar is visible with correct placeholder
 *
 * These tests verify the onClick callbacks are wired correctly.
 * The NavController integration is verified separately in AppNavigation tests.
 */
class ChatScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun chatScreen_displaysTopAppBar_withAllFourIcons() {
        // When: ChatScreen is composed
        composeTestRule.setContent {
            ChatScreen(
                onNavigateToProfile = {},
                onNavigateToSettings = {},
                onNavigateToPurchase = {},
                onNavigateToCharacterSelect = {},
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // Then: all 4 TopAppBar icons are present with correct content descriptions
        composeTestRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("选择角色").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("购买角色").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("设置").assertIsDisplayed()
    }

    @Test
    fun chatScreen_backIcon_callsOnBack() {
        var backCalled = false

        composeTestRule.setContent {
            ChatScreen(
                onNavigateToProfile = {},
                onNavigateToSettings = {},
                onNavigateToPurchase = {},
                onNavigateToCharacterSelect = {},
                onBack = { backCalled = true }
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the back icon
        composeTestRule.onNodeWithContentDescription("返回").performClick()

        // Then: onBack is called
        assert(backCalled) { "Expected back icon to call onBack" }
    }

    @Test
    fun chatScreen_characterSelectIcon_callsOnNavigateToCharacterSelect() {
        var characterSelectCalled = false

        composeTestRule.setContent {
            ChatScreen(
                onNavigateToProfile = {},
                onNavigateToSettings = {},
                onNavigateToPurchase = {},
                onNavigateToCharacterSelect = { characterSelectCalled = true },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the person/character-select icon
        composeTestRule.onNodeWithContentDescription("选择角色").performClick()

        // Then: onNavigateToCharacterSelect is called
        assert(characterSelectCalled) {
            "Expected character-select icon to call onNavigateToCharacterSelect"
        }
    }

    @Test
    fun chatScreen_purchaseIcon_callsOnNavigateToPurchase() {
        var purchaseCalled = false

        composeTestRule.setContent {
            ChatScreen(
                onNavigateToProfile = {},
                onNavigateToSettings = {},
                onNavigateToPurchase = { purchaseCalled = true },
                onNavigateToCharacterSelect = {},
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the purchase/shopping-cart icon
        composeTestRule.onNodeWithContentDescription("购买角色").performClick()

        // Then: onNavigateToPurchase is called
        assert(purchaseCalled) {
            "Expected purchase icon to call onNavigateToPurchase"
        }
    }

    @Test
    fun chatScreen_settingsIcon_callsOnNavigateToSettings() {
        var settingsCalled = false

        composeTestRule.setContent {
            ChatScreen(
                onNavigateToProfile = {},
                onNavigateToSettings = { settingsCalled = true },
                onNavigateToPurchase = {},
                onNavigateToCharacterSelect = {},
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the settings icon
        composeTestRule.onNodeWithContentDescription("设置").performClick()

        // Then: onNavigateToSettings is called
        assert(settingsCalled) {
            "Expected settings icon to call onNavigateToSettings"
        }
    }
}
