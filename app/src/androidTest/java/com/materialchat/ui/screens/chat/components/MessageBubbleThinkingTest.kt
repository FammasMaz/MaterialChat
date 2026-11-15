package com.materialchat.ui.screens.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.MessageUiItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A completed assistant message's reasoning must be visibly present and reachable: the
 * collapsed header shows a preview line, tapping it expands the full reasoning, and the
 * "always show thinking" preference takes effect even when it arrives after the bubble
 * composed (it is collected asynchronously, so for a reopened chat that is the normal order).
 */
@RunWith(AndroidJUnit4::class)
class MessageBubbleThinkingTest {
    @get:Rule val compose = createComposeRule()

    private val thinking = "The user wants a sorting algorithm.\nQuicksort is the natural pick here."

    private fun completedMessage() = Message(
        id = "assistant-1",
        conversationId = "conv-1",
        role = MessageRole.ASSISTANT,
        content = "Use quicksort.",
        thinkingContent = thinking,
        thinkingDurationMs = 3_000L,
        isStreaming = false
    )

    @Test
    fun collapsedHeaderShowsPreviewAndTapExpandsReasoning() {
        compose.setContent {
            MaterialTheme {
                MessageBubble(
                    messageItem = MessageUiItem(message = completedMessage()),
                    onCopy = {},
                    alwaysShowThinking = false,
                    hapticsEnabled = false
                )
            }
        }

        compose.onNodeWithText("Thought for 3s").assertIsDisplayed()
        compose.onNodeWithText("The user wants a sorting algorithm.").assertIsDisplayed()
        compose.onNodeWithText(thinking).assertDoesNotExist()

        compose.onNodeWithContentDescription("Expand thinking").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(thinking).assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse thinking").assertIsDisplayed()
    }

    @Test
    fun preferenceArrivingAfterCompositionExpandsCompletedReasoning() {
        var alwaysShow by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                MessageBubble(
                    messageItem = MessageUiItem(message = completedMessage()),
                    onCopy = {},
                    alwaysShowThinking = alwaysShow,
                    hapticsEnabled = false
                )
            }
        }
        compose.onNodeWithText(thinking).assertDoesNotExist()

        alwaysShow = true
        compose.waitForIdle()

        compose.onNodeWithText(thinking).assertIsDisplayed()
    }
}
