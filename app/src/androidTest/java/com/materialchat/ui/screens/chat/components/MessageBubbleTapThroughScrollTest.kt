package com.materialchat.ui.screens.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the long-chat tap-through bug: while the message list's scroll is in
 * progress (fling tail, overscroll, programmatic auto-scroll) the LazyColumn consumes every
 * pointer-down in the Initial pass, so controls inside a bubble never saw the tap. A memory
 * chip must still toggle when tapped during an in-progress scroll, and of course when the
 * list is still.
 */
@RunWith(AndroidJUnit4::class)
class MessageBubbleTapThroughScrollTest {
    @get:Rule val compose = createComposeRule()

    private val memoryJson = Json.encodeToString(
        MemoryMessageMetadata(saved = listOf(MemoryReference(id = "m1", label = "Prefers tea")))
    )

    private fun assistantMessage(index: Int) = Message(
        id = "assistant-$index",
        conversationId = "conv-1",
        role = MessageRole.ASSISTANT,
        content = "Reply $index",
        memoryMetadata = memoryJson,
        isStreaming = false
    )

    private fun setLongChat(listState: LazyListState): () -> CoroutineScope {
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items = (0 until 60).toList(), key = { it }) { index ->
                        MessageBubble(
                            messageItem = MessageUiItem(message = assistantMessage(index)),
                            onCopy = {},
                            hapticsEnabled = false
                        )
                    }
                }
            }
        }
        return { scope }
    }

    @Test
    fun memoryChipTogglesWhileListScrollIsInProgress() {
        val listState = LazyListState()
        val scope = setLongChat(listState)
        compose.waitForIdle()

        // A slow programmatic scroll keeps the scroll session open, exactly like the tail of a
        // fling or the chat's own auto-scroll: the list now consumes every pointer-down.
        compose.mainClock.autoAdvance = false
        scope().launch {
            listState.animateScrollBy(2f, tween(durationMillis = 20_000, easing = LinearEasing))
        }
        compose.mainClock.advanceTimeBy(250)
        assertTrue("test precondition: list scroll must be in progress", listState.isScrollInProgress)

        compose.onAllNodesWithText("Memory saved").onFirst().performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }

        compose.onAllNodesWithContentDescription("Hide memory details").onFirst().assertIsDisplayed()
    }

    @Test
    fun memoryChipTogglesWhenListIsStill() {
        setLongChat(LazyListState())
        compose.waitForIdle()

        compose.onAllNodesWithText("Memory saved").onFirst().performClick()
        compose.waitForIdle()

        compose.onAllNodesWithContentDescription("Hide memory details").onFirst().assertIsDisplayed()
    }
}
