package com.aiyougame.companion.ui.characters

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitForIdle
import androidx.hilt.navigation.testing.HiltTestActivity
import com.aiyougame.companion.data.model.CharacterDto
import com.aiyougame.companion.data.repository.CharactersRepository
import dagger.hilt.android.testing.HiltAndroidRule
import io.mockk.coEvery
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Compose UI tests for CharacterSelectScreen.
 *
 * Verifies:
 * - TopAppBar has all 3 icons (back, cart, chat) with correct content descriptions
 * - Cart icon navigation callback fires on click
 * - Chat icon navigation callback fires on click
 * - Back icon navigation callback fires on click
 *
 * These tests verify the onClick callbacks are wired correctly.
 * The NavController integration is verified separately in AppNavigation tests.
 */
class CharacterSelectScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var charactersRepository: CharactersRepository

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun characterSelectScreen_displaysTopAppBar_withAllThreeIcons() {
        // Given: repository returns a character list
        coEvery { charactersRepository.getCharacters() } returns Result.success(
            listOf(
                CharacterDto(
                    id = "test-id-1",
                    code = "gu_chen",
                    name = "顾晨",
                    description = "温柔学长",
                    price = 5800,
                    thumbnail = null,
                    avatarUrl = "url",
                    isOwned = true
                )
            )
        )

        // When: CharacterSelectScreen is composed
        composeTestRule.setContent {
            CharacterSelectScreen(
                onCharacterSelected = {},
                onNavigateToPurchase = {},
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // Then: all 3 TopAppBar icons are present with correct content descriptions
        composeTestRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("购买角色").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("进入聊天").assertIsDisplayed()
    }

    @Test
    fun characterSelectScreen_cartIcon_callsOnNavigateToPurchase() {
        // Given: repository returns character list
        coEvery { charactersRepository.getCharacters() } returns Result.success(
            listOf(
                CharacterDto(
                    id = "ade85259-2bd0-44e9-a27b-2c7fdb460524",
                    code = "gu_chen",
                    name = "顾晨",
                    description = "温柔学长",
                    price = 5800,
                    thumbnail = null,
                    avatarUrl = "url",
                    isOwned = true
                )
            )
        )

        var capturedId: String? = null
        composeTestRule.setContent {
            CharacterSelectScreen(
                onCharacterSelected = {},
                onNavigateToPurchase = { characterId ->
                    capturedId = characterId
                },
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the cart icon
        composeTestRule.onNodeWithContentDescription("购买角色").performClick()

        // Then: onNavigateToPurchase is called with the hardcoded character ID from the screen
        assert(capturedId == "ade85259-2bd0-44e9-a27b-2c7fdb460524") {
            "Expected cart icon to call onNavigateToPurchase with correct character ID"
        }
    }

    @Test
    fun characterSelectScreen_chatIcon_callsOnCharacterSelected() {
        // Given: repository returns character list
        coEvery { charactersRepository.getCharacters() } returns Result.success(
            listOf(
                CharacterDto(
                    id = "test-id",
                    code = "gu_chen",
                    name = "顾晨",
                    description = "温柔学长",
                    price = 5800,
                    thumbnail = null,
                    avatarUrl = "url",
                    isOwned = true
                )
            )
        )

        var selectedCode: String? = null
        composeTestRule.setContent {
            CharacterSelectScreen(
                onCharacterSelected = { code ->
                    selectedCode = code
                },
                onNavigateToPurchase = {},
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the chat icon (green shortcut to enter chat)
        composeTestRule.onNodeWithContentDescription("进入聊天").performClick()

        // Then: onCharacterSelected is called with "gu_chen"
        assert(selectedCode == "gu_chen") {
            "Expected chat icon to call onCharacterSelected with gu_chen"
        }
    }

    @Test
    fun characterSelectScreen_backIcon_callsOnBack() {
        // Given: repository returns character list
        coEvery { charactersRepository.getCharacters() } returns Result.success(
            listOf(
                CharacterDto(
                    id = "test-id",
                    code = "gu_chen",
                    name = "顾晨",
                    description = "温柔学长",
                    price = 5800,
                    thumbnail = null,
                    avatarUrl = "url",
                    isOwned = true
                )
            )
        )

        var backCalled = false
        composeTestRule.setContent {
            CharacterSelectScreen(
                onCharacterSelected = {},
                onNavigateToPurchase = {},
                onBack = { backCalled = true }
            )
        }

        composeTestRule.waitForIdle()

        // When: user taps the back icon
        composeTestRule.onNodeWithContentDescription("返回").performClick()

        // Then: onBack is called
        assert(backCalled) { "Expected back icon to call onBack" }
    }
}
