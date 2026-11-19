package com.materialchat.ui.screens.chat.components

import com.materialchat.assistant.ui.AssistantCopy
import com.materialchat.domain.model.ProviderHttpException
import com.materialchat.domain.model.StreamingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

/**
 * The errored bubble names the failure class, not the provider's text: 401, 403, 429, other
 * 4xx, 5xx, connectivity and the generic case each get a distinct, sentence-case line that
 * ends by telling the user what to do.
 */
class ChatErrorCopyTest {

    private val classes = listOf(
        401 to ChatErrorCopy.UNAUTHORIZED,
        403 to ChatErrorCopy.FORBIDDEN,
        429 to ChatErrorCopy.RATE_LIMITED,
        404 to ChatErrorCopy.REJECTED,
        500 to ChatErrorCopy.SERVER_ERROR,
        503 to ChatErrorCopy.SERVER_ERROR
    )

    @Test
    fun `each typed status maps to its class line and never to the provider text`() {
        classes.forEach { (status, expected) ->
            val state = StreamingState.Error(error = ProviderHttpException(status, "raw provider body $status"))
            assertEquals("status $status", expected, ChatErrorCopy.notice(state))
            assertEquals("snackbar $status", expected, ChatErrorCopy.snackbar(state))
        }
    }

    @Test
    fun `the four named classes are distinct from each other and from the generic line`() {
        val lines = listOf(
            ChatErrorCopy.UNAUTHORIZED,
            ChatErrorCopy.FORBIDDEN,
            ChatErrorCopy.RATE_LIMITED,
            ChatErrorCopy.SERVER_ERROR,
            ChatErrorCopy.REJECTED,
            ChatErrorCopy.NO_CONNECTION,
            ChatErrorCopy.REQUEST_FAILED
        )
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test
    fun `every line is sentence case and ends with a period`() {
        listOf(
            ChatErrorCopy.UNAUTHORIZED,
            ChatErrorCopy.FORBIDDEN,
            ChatErrorCopy.RATE_LIMITED,
            ChatErrorCopy.SERVER_ERROR,
            ChatErrorCopy.REJECTED,
            ChatErrorCopy.NO_CONNECTION,
            ChatErrorCopy.REQUEST_FAILED
        ).forEach { line ->
            assertTrue("starts upper: $line", line.first().isUpperCase())
            assertTrue("ends with period: $line", line.endsWith("."))
            assertTrue("at most two sentences: $line", line.count { it == '.' } <= 2)
            // No Title Case: within each sentence only the first word and the API acronym
            // start upper; the second sentence of a two-sentence line starts upper by rule.
            line.removeSuffix(".").split(". ").forEach { sentence ->
                val words = sentence.split(' ')
                assertTrue("sentence case: $line", words.drop(1).none { it.first().isUpperCase() && it != "API" })
            }
        }
    }

    @Test
    fun `connectivity is recognised from an IOException anywhere in the cause chain`() {
        val direct = StreamingState.Error(error = ConnectException("refused"))
        assertEquals(ChatErrorCopy.NO_CONNECTION, ChatErrorCopy.notice(direct))
        assertEquals(ChatErrorCopy.NO_CONNECTION, ChatErrorCopy.snackbar(direct))

        val wrapped = StreamingState.Error(error = RuntimeException("outer", IOException("timeout")))
        assertEquals(ChatErrorCopy.NO_CONNECTION, ChatErrorCopy.notice(wrapped))
    }

    @Test
    fun `an unclassified failure shows the generic line in the bubble and keeps its text in the snackbar`() {
        val state = StreamingState.Error(error = IllegalStateException("The provider returned an empty response"))
        assertEquals(ChatErrorCopy.REQUEST_FAILED, ChatErrorCopy.notice(state))
        assertEquals("The provider returned an empty response", ChatErrorCopy.snackbar(state))

        val blank = StreamingState.Error(error = IllegalStateException(""))
        assertEquals(ChatErrorCopy.REQUEST_FAILED, ChatErrorCopy.snackbar(blank))
    }

    @Test
    fun `a failed action classifies a typed status or connectivity and otherwise uses its own line`() {
        val typed = RuntimeException("wrapper", ProviderHttpException(429, "raw provider body"))
        assertEquals(ChatErrorCopy.RATE_LIMITED, ChatErrorCopy.action(typed, ChatErrorCopy.COULD_NOT_BRANCH))
        assertEquals(ChatErrorCopy.UNAUTHORIZED, ChatErrorCopy.action(ProviderHttpException(401, "x"), ChatErrorCopy.COULD_NOT_REDO))
        assertEquals(ChatErrorCopy.SERVER_ERROR, ChatErrorCopy.action(ProviderHttpException(502, "x"), ChatErrorCopy.COULD_NOT_REDO))

        val offline = IllegalStateException("outer", ConnectException("refused"))
        assertEquals(ChatErrorCopy.NO_CONNECTION, ChatErrorCopy.action(offline, ChatErrorCopy.COULD_NOT_EXPORT))

        val raw = IllegalStateException("SQLiteConstraintException: UNIQUE constraint failed")
        val line = ChatErrorCopy.action(raw, ChatErrorCopy.COULD_NOT_UPDATE_BOOKMARK)
        assertEquals(ChatErrorCopy.COULD_NOT_UPDATE_BOOKMARK, line)
        assertTrue(line, !line.contains("SQLite"))
        assertEquals(ChatErrorCopy.COULD_NOT_RUN_FUSION, ChatErrorCopy.action(null, ChatErrorCopy.COULD_NOT_RUN_FUSION))
    }

    @Test
    fun `the shared lines and the retry label match the assistant notice model`() {
        assertEquals(AssistantCopy.RATE_LIMITED, ChatErrorCopy.RATE_LIMITED)
        assertEquals(AssistantCopy.NO_CONNECTION, ChatErrorCopy.NO_CONNECTION)
        assertEquals(AssistantCopy.REQUEST_FAILED, ChatErrorCopy.REQUEST_FAILED)
        assertEquals(AssistantCopy.AFTER_ERROR, ChatErrorCopy.TRY_AGAIN)
        assertNotEquals("Retry", ChatErrorCopy.TRY_AGAIN)
    }
}
