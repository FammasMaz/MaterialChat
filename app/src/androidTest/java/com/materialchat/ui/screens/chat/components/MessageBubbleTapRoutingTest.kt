package com.materialchat.ui.screens.chat.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialchat.domain.model.MemoryMessageMetadata
import com.materialchat.domain.model.MemoryReference
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.ui.screens.chat.MessageUiItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bubble-level tap router lives on the bubble Surface's own node and ignores whether the
 * chip consumed the up (see BubbleTapRouter). Exactly-once delivery is therefore explicit:
 * when the chip's own clickable handles the tap the bubble must not route it a second time,
 * otherwise every working tap toggles twice and nothing ever opens. These tests pin that
 * contract for the memory chips and the thinking header, on a bubble with the bookmark
 * `combinedClickable` present (the layer that ripples on the user's phone).
 */
@RunWith(AndroidJUnit4::class)
class MessageBubbleTapRoutingTest {
    @get:Rule val compose = createComposeRule()

    private val memoryJson = Json.encodeToString(
        MemoryMessageMetadata(saved = listOf(MemoryReference(id = "m1", label = "Prefers tea")))
    )

    private fun assistantMessage(index: Int, thinking: String? = null) = Message(
        id = "assistant-$index",
        conversationId = "conv-1",
        role = MessageRole.ASSISTANT,
        content = "Reply $index",
        thinkingContent = thinking,
        thinkingDurationMs = thinking?.let { 1200L },
        memoryMetadata = memoryJson,
        isStreaming = false
    )

    private fun setBubbles(thinking: String? = null) {
        compose.setContent {
            MaterialTheme {
                LazyColumn(state = LazyListState(), modifier = Modifier.fillMaxSize()) {
                    items(items = (0 until 3).toList(), key = { it }) { index ->
                        MessageBubble(
                            messageItem = MessageUiItem(message = assistantMessage(index, thinking)),
                            onCopy = {},
                            onBookmarkToggle = {},
                            hapticsEnabled = false
                        )
                    }
                }
            }
        }
    }

    @Test
    fun memoryChipTogglesExactlyOncePerTap() {
        setBubbles()
        compose.onAllNodesWithText("Memory saved").onFirst().assertIsDisplayed().performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithContentDescription("Hide memory details").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Prefers tea").onFirst().assertIsDisplayed()

        compose.onAllNodesWithText("Memory saved").onFirst().performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithContentDescription("Hide memory details").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun thinkingHeaderTogglesExactlyOncePerTap() {
        setBubbles(thinking = "Reasoning about tea.")
        compose.onAllNodesWithContentDescription("Expand thinking").onFirst().assertIsDisplayed().performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithContentDescription("Collapse thinking").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onAllNodesWithContentDescription("Collapse thinking").onFirst().performClick()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithContentDescription("Expand thinking").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
