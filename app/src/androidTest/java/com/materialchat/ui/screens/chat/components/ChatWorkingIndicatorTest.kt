package com.materialchat.ui.screens.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.MessageUiItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pre-first-token state of an assistant bubble is the "working on your answer" surface
 * ported from the assistant overlay, not the old three-dot indicator.
 */
@RunWith(AndroidJUnit4::class)
class ChatWorkingIndicatorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun streamingBubbleWithoutTokensShowsWorkingSurface() {
        val message = Message(
            id = "assistant-1",
            conversationId = "conv-1",
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        compose.setContent {
            MaterialTheme {
                MessageBubble(
                    messageItem = MessageUiItem(message = message),
                    onCopy = {},
                    hapticsEnabled = false
                )
            }
        }

        compose.onNodeWithContentDescription("$CHAT_WORKING_TITLE. $CHAT_WORKING_STATUS").assertIsDisplayed()
        compose.onNodeWithText(CHAT_WORKING_TITLE).assertIsDisplayed()
        compose.onNodeWithText(CHAT_WORKING_STATUS).assertIsDisplayed()
    }
}
