package com.materialchat.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.get
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.materialchat.domain.model.MemoryMessageMetadata
import com.materialchat.domain.model.MemoryReference
import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The memory chips must open their panel inside the REAL message list, not only inside a bare
 * [com.materialchat.ui.screens.chat.components.MessageBubble]. The production list adds gesture
 * layers the bubble-only regression never had: bookmark callbacks on every assistant bubble,
 * swipe-to-quote on every bubble, the chat's own fling behaviour, animateItem placement and the
 * action row. A chip tap must survive all of them when the list is still, after a real fling has
 * settled, and while a scroll session is still open.
 */
@RunWith(AndroidJUnit4::class)
class MessageListMemoryChipTest {
    @get:Rule val compose = createComposeRule()

    private val memoryJson = Json.encodeToString(
        MemoryMessageMetadata(saved = listOf(MemoryReference(id = "m1", label = "Prefers tea")))
    )

    private fun item(index: Int): MessageUiItem {
        val assistant = index % 2 == 1
        return MessageUiItem(
            message = Message(
                id = "msg-$index",
                conversationId = "conv-1",
                role = if (assistant) MessageRole.ASSISTANT else MessageRole.USER,
                content = if (assistant) "Reply $index" else "Question $index",
                memoryMetadata = if (assistant) memoryJson else null,
                isStreaming = false
            ),
            isLastAssistantMessage = index == 59,
            showActions = assistant
        )
    }

    private fun setRealList(listState: LazyListState): () -> CoroutineScope {
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme {
                MessageList(
                    messages = (0 until 60).map(::item),
                    listState = listState,
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
                    hapticsEnabled = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return { scope }
    }

    /**
     * Index of the first chip whose whole bounds sit inside the viewport, so the tap can never
     * land on a sliver of a chip that the list has scrolled half out of view.
     */
    private fun fullyVisibleChipIndex(): Int {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val chips = compose.onAllNodesWithText("Memory saved").fetchSemanticsNodes()
        val index = chips.indexOfFirst { chip ->
            val b = chip.boundsInRoot
            b.top >= root.top && b.bottom <= root.bottom && b.left >= root.left && b.right <= root.right
        }
        assertTrue(
            "test precondition: a memory chip must be fully in view; chips=${chips.map { it.boundsInRoot }} root=$root",
            index >= 0
        )
        return index
    }

    private fun tapFullyVisibleMemoryChip(): Int {
        val index = fullyVisibleChipIndex()
        compose.onAllNodesWithText("Memory saved")[index].assertIsDisplayed().performClick()
        return index
    }

    private fun assertPanelOpen(chipIndex: Int) {
        try {
            // The chevron flips the moment the toggle fires, so this separates "the tap never
            // reached the chip" from "the toggle fired but the panel never showed".
            compose.onAllNodesWithText("Memory saved")[chipIndex]
                .assert(hasContentDescription("Hide memory details")) { "chip tapped but its state never toggled" }
            compose.onAllNodesWithText("Prefers tea", substring = true).onFirst().assertIsDisplayed()
        } catch (failure: AssertionError) {
            compose.onRoot().printToLog("MemoryChipTest")
            throw failure
        }
    }

    @Test
    fun memoryChipOpensPanelInTheRealListWhenStill() {
        setRealList(LazyListState(firstVisibleItemIndex = 59))
        compose.waitForIdle()

        val chip = tapFullyVisibleMemoryChip()
        compose.waitForIdle()

        assertPanelOpen(chip)
    }

    @Test
    fun memoryChipOpensPanelAfterARealFlingHasSettled() {
        val listState = LazyListState()
        setRealList(listState)
        compose.waitForIdle()

        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("test precondition: the fling must have moved the list", listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        assertTrue("test precondition: the fling must have settled", !listState.isScrollInProgress)

        val chip = tapFullyVisibleMemoryChip()
        compose.waitForIdle()

        assertPanelOpen(chip)
    }

    @Test
    fun memoryChipOpensPanelWhileTheListScrollIsInProgress() {
        val listState = LazyListState(firstVisibleItemIndex = 30)
        val scope = setRealList(listState)
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        scope().launch {
            listState.animateScrollBy(2f, tween(durationMillis = 20_000, easing = LinearEasing))
        }
        compose.mainClock.advanceTimeBy(250)
        assertTrue("test precondition: list scroll must be in progress", listState.isScrollInProgress)

        val chip = tapFullyVisibleMemoryChip()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertPanelOpen(chip)
    }
}
