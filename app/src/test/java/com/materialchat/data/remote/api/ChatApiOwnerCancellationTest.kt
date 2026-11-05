package com.materialchat.data.remote.api

import com.materialchat.domain.model.Message
import com.materialchat.domain.model.MessageRole
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import com.materialchat.domain.model.ReasoningEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A provider stream must be cancellable on its own.
 *
 * Stop used to cancel every in-flight HTTP call in the process, so an arena
 * battle, fusion run, title generation, or image request that happened to be
 * streaming was aborted by an unrelated Stop tap. This test keeps two requests
 * open against a real socket server, cancels one owner while the other is
 * mid-stream, and requires the untouched request to deliver its full answer.
 */
class ChatApiOwnerCancellationTest {

    private val server = ChunkedSseServer()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `cancelling one conversation does not abort another conversation stream`() = runBlocking {
        val apiClient = ChatApiClient(okHttpClient = OkHttpClient())

        val stopped = async(Dispatchers.IO) {
            runCatching {
                apiClient.streamOpenAiChat(
                    baseUrl = server.baseUrlFor("stopped"),
                    model = "model-a",
                    messages = listOf(userMessage()),
                    apiKey = "key",
                    reasoningEffort = ReasoningEffort.NONE,
                    ownerId = "conversation-stopped"
                ).toList()
            }
        }
        val kept = async(Dispatchers.IO) {
            apiClient.streamOpenAiChat(
                baseUrl = server.baseUrlFor("kept"),
                model = "model-b",
                messages = listOf(userMessage()),
                apiKey = "key",
                reasoningEffort = ReasoningEffort.NONE,
                ownerId = "conversation-kept"
            ).toList()
        }

        assertTrue("stopped stream must open", server.awaitOpen("/stopped"))
        assertTrue("kept stream must open", server.awaitOpen("/kept"))

        // Both requests are open and mid-stream here.
        apiClient.cancelStreaming("conversation-stopped")

        server.release("/kept")
        val keptEvents = withTimeout(10_000) { kept.await() }
        val keptText = keptEvents.filterIsInstance<StreamingEvent.Content>()
            .joinToString("") { it.content }

        assertEquals("the untouched conversation must get its whole answer", "firstcomplete", keptText)
        assertTrue(
            "the untouched conversation must finish normally",
            keptEvents.any { it is StreamingEvent.Done }
        )
        stopped.cancel()
    }

    @Test
    fun `cancelling a conversation does not abort a generated image request`() = runBlocking {
        val apiClient = ChatApiClient(okHttpClient = OkHttpClient())

        val chat = async(Dispatchers.IO) {
            runCatching {
                apiClient.streamOpenAiChat(
                    baseUrl = server.baseUrlFor("stopped"),
                    model = "model-a",
                    messages = listOf(userMessage()),
                    apiKey = "key",
                    reasoningEffort = ReasoningEffort.NONE,
                    ownerId = "conversation-stopped"
                ).toList()
            }
        }
        val image = async(Dispatchers.IO) {
            apiClient.generateImage(
                provider = Provider(
                    id = "p",
                    name = "p",
                    type = ProviderType.OPENAI_COMPATIBLE,
                    baseUrl = server.baseUrlFor("images"),
                    defaultModel = "gpt-image-1",
                    requiresApiKey = true,
                    isActive = true
                ),
                prompt = "a cat",
                model = "gpt-image-1",
                apiKey = "key"
            )
        }

        assertTrue("chat stream must open", server.awaitOpen("/stopped"))
        assertTrue("image request must open", server.awaitOpen("/images"))

        apiClient.cancelStreaming("conversation-stopped")
        server.release("/images")

        val imageResult = withTimeout(10_000) { image.await() }
        assertTrue(
            "image generation must survive an unrelated Stop: ${imageResult.exceptionOrNull()}",
            imageResult.isSuccess
        )
        chat.cancel()
    }

    private fun userMessage() = Message(
        conversationId = "c",
        role = MessageRole.USER,
        content = "hi"
    )

    /**
     * Real loopback HTTP/1.1 server with chunked responses. The first chunk is
     * flushed and then the response is held open until released, so cancelling
     * an OkHttp call actually breaks the socket (unlike an in-memory pipe).
     */
    private class ChunkedSseServer : AutoCloseable {
        private val socket = ServerSocket(0, 16, java.net.InetAddress.getLoopbackAddress())
        private val opened = ConcurrentHashMap<String, CountDownLatch>()
        private val released = ConcurrentHashMap<String, CountDownLatch>()
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val acceptor = Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                sockets.add(client)
                Thread { serve(client) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }

        init { acceptor.start() }

        fun baseUrlFor(pathPrefix: String) = "http://127.0.0.1:${socket.localPort}/$pathPrefix"

        fun awaitOpen(prefix: String) = latch(opened, prefix).await(10, TimeUnit.SECONDS)

        fun release(prefix: String) = latch(released, prefix).countDown()

        private fun latch(map: ConcurrentHashMap<String, CountDownLatch>, key: String) =
            map.getOrPut(key) { CountDownLatch(1) }

        private fun serve(client: Socket) {
            runCatching {
                val input = BufferedInputStream(client.getInputStream())
                val requestLine = readLine(input) ?: return
                val path = requestLine.split(' ').getOrNull(1).orEmpty()
                val prefix = "/" + path.trim('/').substringBefore('/')
                val isImage = path.contains("images/generations")
                while (true) {
                    val header = readLine(input) ?: break
                    if (header.isEmpty()) break
                }

                val output: OutputStream = client.getOutputStream()
                output.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " +
                            (if (isImage) "application/json" else "text/event-stream") +
                            "\r\nTransfer-Encoding: chunked\r\n\r\n"
                        ).toByteArray()
                )
                output.flush()

                if (!isImage) writeChunk(output, sseChunk("first"))
                latch(opened, prefix).countDown()
                latch(released, prefix).await(20, TimeUnit.SECONDS)

                if (isImage) {
                    writeChunk(output, """{"created":1,"data":[{"b64_json":"aGVsbG8="}]}""")
                } else {
                    writeChunk(output, sseChunk("complete"))
                    writeChunk(output, "data: [DONE]\n\n")
                }
                output.write("0\r\n\r\n".toByteArray())
                output.flush()
            }.onFailure {
                // Cancellation closes the socket; expected for the stopped stream.
            }
        }

        private fun writeChunk(output: OutputStream, body: String) {
            val bytes = body.toByteArray()
            output.write("${Integer.toHexString(bytes.size)}\r\n".toByteArray())
            output.write(bytes)
            output.write("\r\n".toByteArray())
            output.flush()
        }

        private fun readLine(input: BufferedInputStream): String? {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1) return line.takeIf { it.isNotEmpty() }?.toString()
                if (c == '\n'.code) return line.toString().trimEnd('\r')
                line.append(c.toChar())
            }
        }

        private fun sseChunk(text: String) =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$text\"}}]}\n\n"

        override fun close() {
            sockets.forEach { runCatching { it.close() } }
            runCatching { socket.close() }
        }
    }
}
