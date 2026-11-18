package com.materialchat.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialchat.domain.model.MemoryMessageMetadata
import com.materialchat.domain.model.MemoryReference
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit down/up injection at root coordinates through the production MessageList, including
 * animateItem, bookmark combinedClickable, swipe-to-quote, action rows and chat fling behavior.
 * Semantics only locates/asserts controls; no semantics click action is invoked.
 *
 * These cases protect disclosure delivery, not an alleged reproduction of the phone-only bug.
 * Existing Android performClick tests also inject touch in this pinned Compose version.
 */
@RunWith(AndroidJUnit4::class)
class MessageListDisclosureTouchTest {
    @get:Rule val compose = createComposeRule()

    private enum class Control(val text: String, val expandedDescription: String) {
        Memory("Memory saved", "Hide memory details"),
        Thinking("Thought", "Collapse thinking")
    }

    private enum class ScrollCondition { Still, AfterFling, MidScroll }

    private val memoryJson = Json.encodeToString(
        MemoryMessageMetadata(
            saved = listOf(MemoryReference(id = "touch-memory", label = "Device trace memory"))
        )
    )

    private fun items(): List<MessageUiItem> = (0 until 60).map { index ->
        val assistant = index % 2 == 1
        MessageUiItem(
            message = Message(
                id = "disclosure-$index",
                conversationId = "disclosure-touch",
                role = if (assistant) MessageRole.ASSISTANT else MessageRole.USER,
                content = if (assistant) "Reply $index" else "Question $index",
                thinkingContent = if (assistant) "Reasoning $index.\n\nDetails for response $index." else null,
                memoryMetadata = if (assistant) memoryJson else null,
                isStreaming = false
            ),
            isLastAssistantMessage = index == 59,
            showActions = assistant
        )
    }

    private fun setRealList(state: LazyListState): () -> CoroutineScope {
        lateinit var scope: CoroutineScope
        val messages = items()
        compose.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme {
                MessageList(
                    messages = messages,
                    listState = state,
                    onCopyMessage = {},
                    onRegenerateResponse = {},
                    onBranchFromMessage = {},
                    onRedoWithModel = {},
                    onBookmarkToggle = {},
                    onBookmarkLongPress = {},
                    bookmarkedMessageIds = emptySet(),
                    onNavigatePrevious = {},
                    onNavigateNext = {},
                    onQuoteMessage = {},
                    alwaysShowThinking = false,
                    hapticsEnabled = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return { scope }
    }

    /** Locate a placed, fully visible control with space for its opened content below it. */
    private fun injectDisclosureTap(control: Control): Int {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val candidates = compose.onAllNodesWithText(control.text).fetchSemanticsNodes()
        val clearance = with(compose.density) { 96.dp.toPx() }
        val index = candidates.indices.firstOrNull { index ->
            val node = candidates[index]
            val bounds = node.boundsInRoot
            node.layoutInfo.isPlaced && bounds.width > 0f && bounds.height > 0f &&
                bounds.left >= root.left && bounds.right <= root.right &&
                bounds.top >= root.top && bounds.bottom + clearance <= root.bottom &&
                bounds.height >= node.size.height - 1f &&
                compose.onAllNodesWithText(control.text)[index].isDisplayed()
        }
        assertTrue(
            "A fully visible ${control.name} target must exist: " +
                candidates.map { "${it.boundsInRoot} placed=${it.layoutInfo.isPlaced}" },
            index != null
        )
        val node = candidates[index!!]
        val position = node.boundsInRoot.center - root.topLeft
        compose.onRoot().performTouchInput {
            down(position)
            advanceEventTime(80)
            up()
        }
        return node.id
    }

    private fun exercise(control: Control, condition: ScrollCondition) {
        val state = LazyListState(firstVisibleItemIndex = if (condition == ScrollCondition.AfterFling) 0 else 30)
        val scope = setRealList(state)
        compose.waitForIdle()
        var scrollJob: Job? = null
        try {
            when (condition) {
                ScrollCondition.Still -> assertTrue("List must be still", !state.isScrollInProgress)
                ScrollCondition.AfterFling -> {
                    compose.onRoot().performTouchInput { swipeUp(durationMillis = 150) }
                    compose.waitForIdle()
                    assertTrue("Fling must move the list", state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
                    assertTrue("Fling must settle before the tap", !state.isScrollInProgress)
                }
                ScrollCondition.MidScroll -> {
                    compose.mainClock.autoAdvance = false
                    compose.runOnIdle {
                        scrollJob = scope().launch {
                            state.animateScrollBy(2f, tween(20_000, easing = LinearEasing))
                        }
                    }
                    compose.mainClock.advanceTimeBy(250)
                    assertTrue("Scroll must be in progress before pointer down", state.isScrollInProgress)
                }
            }
            val nodeId = injectDisclosureTap(control)
            // Release the synthetic scroll after injection so it cannot hide a delayed toggle.
            scrollJob?.cancel()
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNode(SemanticsMatcher("the exact tapped disclosure") { it.id == nodeId })
                .assert(hasText(control.text))
                .assert(hasContentDescription(control.expandedDescription))
                .assertIsDisplayed()
            if (control == Control.Memory) {
                compose.onAllNodesWithText("Device trace memory", substring = true)
                    .onFirst().assertIsDisplayed()
            }
        } finally {
            scrollJob?.cancel()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun memoryChipAcceptsRootTouchWhenStill() {
        exercise(Control.Memory, ScrollCondition.Still)
    }

    @Test
    fun memoryChipAcceptsRootTouchAfterFling() {
        exercise(Control.Memory, ScrollCondition.AfterFling)
    }

    @Test
    fun memoryChipAcceptsRootTouchMidScroll() {
        exercise(Control.Memory, ScrollCondition.MidScroll)
    }

    @Test
    fun thinkingHeaderAcceptsRootTouchWhenStill() {
        exercise(Control.Thinking, ScrollCondition.Still)
    }

    @Test
    fun thinkingHeaderAcceptsRootTouchAfterFling() {
        exercise(Control.Thinking, ScrollCondition.AfterFling)
    }

    @Test
    fun thinkingHeaderAcceptsRootTouchMidScroll() {
        exercise(Control.Thinking, ScrollCondition.MidScroll)
    }
}
