package com.materialchat.assistant.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AssistantSpeechQueueTest {
    @Test fun `generation queues while speech runs and final close drains in order`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val spoken = mutableListOf<String>()
        val queue = AssistantSpeechQueue(this) {
            spoken += it
            if (it == "First") gate.await()
        }
        queue.enqueue("First")
        runCurrent()
        queue.enqueue("Final")
        queue.finish()
        runCurrent()
        assertEquals(listOf("First"), spoken)
        assertFalse(queue.job.isCompleted)
        gate.complete(Unit)
        queue.job.join()
        assertEquals(listOf("First", "Final"), spoken)
    }

    @Test fun `interrupt cancels active speech and discards queued speech`() = runTest {
        val spoken = mutableListOf<String>()
        var released = false
        val queue = AssistantSpeechQueue(this) {
            spoken += it
            try { awaitCancellation() } finally { released = true }
        }
        queue.enqueue("Active")
        queue.enqueue("Discard")
        runCurrent()
        queue.cancel()
        queue.job.join()
        assertTrue(released)
        assertEquals(listOf("Active"), spoken)
    }

    @Test fun `long chunks preserve unicode and backend bounds`() = runTest {
        val spoken = mutableListOf<String>()
        val queue = AssistantSpeechQueue(this) { spoken += it }
        val text = "a".repeat(3499) + "\uD83D\uDE00" + "b".repeat(4000)
        queue.enqueue(text)
        queue.finish()
        queue.job.join()
        assertEquals(text, spoken.joinToString(""))
        assertTrue(spoken.all { it.length <= 3500 && !it.last().isHighSurrogate() })
    }
}
