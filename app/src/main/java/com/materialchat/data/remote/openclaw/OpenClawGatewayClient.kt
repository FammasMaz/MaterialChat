package com.materialchat.data.remote.openclaw

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.materialchat.data.remote.api.StreamingEvent
import com.materialchat.data.remote.openclaw.dto.*
import com.materialchat.data.remote.sse.SseEventParser
import com.materialchat.domain.model.openclaw.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * OpenClaw Gateway client supporting both WebSocket and HTTP SSE protocols.
 *
 * Features:
 * - WebSocket lifecycle management with connect/disconnect
 * - Request/response correlation for RPC-style calls
 * - Real-time event streaming via SharedFlow
 * - HTTP SSE fallback for chat streaming
 * - Auto-reconnect with exponential backoff
 * - Network change detection and re-connection
 * - Connection latency tracking
 * - Self-signed certificate support
 */
class OpenClawGatewayClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val sseEventParser: SseEventParser,
    private val connectivityManager: ConnectivityManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    companion object {
        private const val TAG = "OpenClawGateway"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Extracts text from a content field that can be either a plain string
         * or an array of content blocks: [{"type":"text","text":"..."}]
         */
        fun extractTextContent(content: JsonElement?): String? {
            if (content == null) return null
            if (content is JsonPrimitive && content.isString) return content.content
            if (content is JsonArray) {
                return content
                    .filterIsInstance<JsonObject>()
                    .filter { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
                    .joinToString("")
                    .ifEmpty { null }
            }
            return null
        }
    }

    // ========== State ==========

    private val _connectionState = MutableStateFlow<GatewayConnectionState>(GatewayConnectionState.Disconnected)
    val connectionState: StateFlow<GatewayConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private var webSocket: WebSocket? = null
    private var currentConfig: OpenClawConfig? = null
    private var currentToken: String? = null
    private var connId: String? = null

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ResponseFrame>>()
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private val shouldReconnect = AtomicBoolean(false)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // ========== Connection Lifecycle ==========

    /**
     * Connects to the OpenClaw Gateway via WebSocket.
     */
    fun connect(config: OpenClawConfig, token: String) {
        if (_connectionState.value is GatewayConnectionState.Connecting ||
            _connectionState.value is GatewayConnectionState.Connected) {
            Log.d(TAG, "Already connected or connecting, ignoring")
            return
        }

        currentConfig = config
        currentToken = token
        shouldReconnect.set(true)
        reconnectAttempt = 0

        doConnect(config, token)
        registerNetworkCallback()
    }

    /**
     * Disconnects from the gateway.
     */
    fun disconnect() {
        shouldReconnect.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        unregisterNetworkCallback()

        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        connId = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        _connectionState.value = GatewayConnectionState.Disconnected
        _latencyMs.value = null
    }

    private fun doConnect(config: OpenClawConfig, token: String) {
        // Close any existing WebSocket to prevent duplicates
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        _connectionState.value = GatewayConnectionState.Connecting

        val client = if (config.allowSelfSignedCerts) {
            createInsecureClient(okHttpClient)
        } else {
            okHttpClient
        }

        val request = Request.Builder()
            .url(config.webSocketUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                // Wait for connect.challenge event — handshake initiated by server
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, token)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                // Don't reconnect for intentional or permanent closures
                val permanent = code == 1000 || code == 1008 || code == 1003
                handleDisconnect("Closed: $code $reason", reconnect = !permanent)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                handleDisconnect(t.message ?: "Connection failed", cause = t)
            }
        })
    }

    private fun handleMessage(text: String, token: String) {
        try {
            val frame = json.decodeFromString<GenericFrame>(text)

            when (frame.type) {
                "event" -> handleEvent(frame, token)
                "res" -> handleResponse(frame)
                else -> Log.w(TAG, "Unknown frame type: ${frame.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}")
        }
    }

    private fun handleEvent(frame: GenericFrame, token: String) {
        when (frame.event) {
            "connect.challenge" -> {
                // Only send handshake if not already connected
                if (_connectionState.value !is GatewayConnectionState.Connected) {
                    sendConnectHandshake(token)
                }
            }
            "tick" -> {
                val ts = frame.payload?.get("ts")?.jsonPrimitive?.longOrNull
                    ?: System.currentTimeMillis()
                scope.launch { _events.emit(GatewayEvent.Tick(ts)) }
            }
            "shutdown" -> {
                val reason = frame.payload?.get("reason")?.jsonPrimitive?.contentOrNull ?: "unknown"
                val restartMs = frame.payload?.get("restartExpectedMs")?.jsonPrimitive?.longOrNull
                scope.launch { _events.emit(GatewayEvent.ShutdownEvent(reason, restartMs)) }
            }
            "chat" -> {
                try {
                    val payload = frame.payload?.let {
                        json.decodeFromJsonElement<ChatEventPayload>(it)
                    } ?: return
                    val toolCalls = payload.message?.toolCalls?.map { tc ->
                        ToolCallInfo(
                            name = tc.name ?: "unknown",
                            arguments = tc.arguments,
                            result = tc.result
                        )
                    }
                    scope.launch {
                        _events.emit(
                            GatewayEvent.ChatEvent(
                                runId = payload.runId ?: "",
                                sessionKey = payload.sessionKey ?: "",
                                seq = payload.seq,
                                state = payload.state ?: "delta",
                                content = extractTextContent(payload.message?.content),
                                errorMessage = payload.errorMessage,
                                toolCalls = toolCalls
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse chat event: ${e.message}")
                }
            }
            "agent" -> {
                try {
                    val payload = frame.payload?.let {
                        json.decodeFromJsonElement<AgentEventPayload>(it)
                    } ?: return
                    scope.launch {
                        _events.emit(
                            GatewayEvent.AgentEvent(
                                runId = payload.runId ?: "",
                                seq = payload.seq,
                                stream = payload.stream ?: "unknown",
                                data = payload.data?.toString()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse agent event: ${e.message}")
                }
            }
            else -> {
                scope.launch {
                    _events.emit(
                        GatewayEvent.UnknownEvent(
                            eventName = frame.event ?: "unknown",
                            payload = frame.payload?.toString()
                        )
                    )
                }
            }
        }
    }

    private fun handleResponse(frame: GenericFrame) {
        val id = frame.id ?: return

        // Check if it's the connect response
        val deferred = pendingRequests.remove(id)
        if (deferred != null) {
            val responseFrame = ResponseFrame(
                id = id,
                ok = frame.ok ?: false,
                payload = frame.payload,
                error = frame.error
            )
            deferred.complete(responseFrame)
        }
    }

    private fun sendConnectHandshake(token: String) {
        val id = UUID.randomUUID().toString()
        val connectParams = ConnectParams(
            client = ClientInfo(),
            auth = ConnectAuth(token = token)
        )

        val paramsJson = json.encodeToJsonElement(connectParams).jsonObject
        val frame = RequestFrame(id = id, method = "connect", params = paramsJson)
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        webSocket?.send(json.encodeToString(frame))

        scope.launch {
            try {
                val response = withTimeout(10_000L) { deferred.await() }
                if (response.ok) {
                    val hello = response.payload?.let {
                        json.decodeFromJsonElement<HelloOkPayload>(it)
                    }
                    connId = hello?.server?.connId
                    reconnectAttempt = 0
                    _connectionState.value = GatewayConnectionState.Connected(
                        connId = connId ?: "",
                        protocol = hello?.protocol ?: 3
                    )
                    Log.i(TAG, "Connected to gateway: connId=$connId, version=${hello?.server?.version}")
                } else {
                    val errorMsg = response.error?.message ?: "Connect rejected"
                    val retryable = response.error?.retryable ?: false
                    _connectionState.value = GatewayConnectionState.Error(
                        message = errorMsg,
                        isRetryable = retryable
                    )
                    // Stop reconnecting on permanent rejection
                    if (!retryable) {
                        shouldReconnect.set(false)
                        webSocket?.close(1000, "Handshake rejected")
                        webSocket = null
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _connectionState.value = GatewayConnectionState.Error(
                    message = "Connection handshake timed out",
                    isRetryable = true
                )
                scheduleReconnect()
            }
        }
    }

    // ========== RPC Methods ==========

    /**
     * Sends a request to the gateway and waits for the response.
     */
    suspend fun sendRequest(method: String, params: JsonObject? = null): ResponseFrame {
        val ws = webSocket ?: throw IOException("Not connected to gateway")
        val id = UUID.randomUUID().toString()
        val frame = RequestFrame(id = id, method = method, params = params)
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        ws.send(json.encodeToString(frame))

        return try {
            withTimeout(30_000L) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(id)
            throw IOException("Request timed out: $method")
        }
    }

    /** Get gateway status. */
    suspend fun getStatus(): GatewayStatus {
        val response = sendRequest("status")
        if (!response.ok) throw IOException(response.error?.message ?: "Failed to get status")

        val payload = response.payload?.let {
            json.decodeFromJsonElement<StatusPayload>(it)
        }
        return GatewayStatus(
            isOnline = payload?.online ?: true,
            version = payload?.version ?: "",
            uptime = payload?.uptime ?: "",
            agentId = payload?.agentId ?: "main",
            activeChannels = payload?.activeChannels ?: 0,
            activeSessions = payload?.activeSessions ?: 0,
            connId = connId ?: ""
        )
    }

    /** List active sessions. */
    suspend fun listSessions(): List<OpenClawSession> {
        val response = sendRequest("sessions.list")
        if (!response.ok) throw IOException(response.error?.message ?: "Failed to list sessions")

        val payload = response.payload?.let {
            json.decodeFromJsonElement<SessionListPayload>(it)
        }
        return payload?.sessions?.map { s ->
            OpenClawSession(
                key = s.key,
                agentId = s.agentId ?: "main",
                channelType = s.channel?.let { parseChannelType(it) },
                label = s.label,
                startedAt = s.startedAt ?: 0L,
                lastActivity = s.lastActivity ?: 0L,
                messageCount = s.messageCount ?: 0,
                title = s.title
            )
        } ?: emptyList()
    }

    /** List connected channels. */
    suspend fun listChannels(): List<OpenClawChannel> {
        val response = sendRequest("channels.status")
        if (!response.ok) throw IOException(response.error?.message ?: "Failed to list channels")

        val payload = response.payload?.let {
            json.decodeFromJsonElement<ChannelStatusPayload>(it)
        }
        return payload?.channels?.map { c ->
            OpenClawChannel(
                type = parseChannelType(c.type ?: "unknown"),
                isConnected = c.connected,
                accountId = c.accountId,
                displayName = c.displayName ?: c.type ?: "Unknown",
                lastActivity = c.lastActivity
            )
        } ?: emptyList()
    }

    /** Send a chat message via WebSocket. */
    suspend fun sendChatMessage(
        sessionKey: String?,
        message: String,
        thinking: String = "enabled"
    ): String {
        val effectiveSessionKey = if (sessionKey.isNullOrEmpty()) UUID.randomUUID().toString() else sessionKey
        val params = buildJsonObject {
            put("sessionKey", effectiveSessionKey)
            put("message", message)
            put("thinking", thinking)
            put("idempotencyKey", UUID.randomUUID().toString())
        }
        val response = sendRequest("chat.send", params)
        if (!response.ok) throw IOException(response.error?.message ?: "Failed to send message")

        return response.payload?.get("runId")?.jsonPrimitive?.contentOrNull
            ?: response.payload?.get("sessionKey")?.jsonPrimitive?.contentOrNull
            ?: ""
    }

    /** Abort an active chat run. */
    suspend fun abortChat(sessionKey: String, runId: String? = null) {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            runId?.let { put("runId", it) }
        }
        sendRequest("chat.abort", params)
    }

    /** Get chat history for a session. */
    suspend fun getChatHistory(sessionKey: String, limit: Int = 100): List<OpenClawChatMessage> {
        val params = buildJsonObject {
            put("sessionKey", sessionKey)
            put("limit", limit)
        }
        val response = sendRequest("chat.history", params)
        if (!response.ok) throw IOException(response.error?.message ?: "Failed to get history")

        val payload = response.payload?.let {
            json.decodeFromJsonElement<ChatHistoryPayload>(it)
        }
        return payload?.messages?.map { m ->
            OpenClawChatMessage(
                role = parseRole(m.role),
                content = extractTextContent(m.content) ?: "",
                thinkingContent = m.thinking,
                toolCalls = m.toolCalls?.map { tc ->
                    ToolCallInfo(tc.name ?: "unknown", tc.arguments, tc.result)
                } ?: emptyList(),
                timestamp = m.ts ?: 0L,
                runId = m.runId
            )
        } ?: emptyList()
    }

    /** Delete a session. */
    suspend fun deleteSession(sessionKey: String) {
        val params = buildJsonObject {
            put("key", sessionKey)
            put("deleteTranscript", true)
        }
        val response = sendRequest("sessions.delete", params)
        if (!response.ok) throw IOException(response.error?.message ?: "Failed to delete session")
    }

    // ========== HTTP SSE Fallback ==========

    /**
     * Streams a chat response via HTTP SSE (POST /v1/chat/completions).
     * Used as a fallback when WebSocket is not available or for simple one-shot chat.
     */
    fun streamChatHttp(
        config: OpenClawConfig,
        token: String,
        message: String,
        sessionKey: String? = null
    ): Flow<StreamingEvent> = callbackFlow {
        val cancelled = AtomicBoolean(false)

        val requestBody = buildJsonObject {
            put("model", "openclaw:${config.agentId}")
            put("stream", true)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", message)
                }
            }
        }

        val url = "${config.httpUrl.trimEnd('/')}/v1/chat/completions"
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .apply {
                addHeader("x-openclaw-agent-id", config.agentId)
                sessionKey?.let { addHeader("x-openclaw-session-key", it) }
            }
            .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val client = if (config.allowSelfSignedCerts) {
            createInsecureClient(okHttpClient)
        } else {
            okHttpClient
        }

        val call = client.newCall(httpRequest)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cancelled.get()) trySend(StreamingEvent.fromException(e))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        trySend(StreamingEvent.fromHttpError(resp.code, errorBody))
                        close()
                        return
                    }

                    trySend(StreamingEvent.Connected)

                    val body = resp.body ?: run {
                        trySend(StreamingEvent.Error("Empty response body"))
                        close()
                        return
                    }

                    try {
                        body.source().inputStream().bufferedReader().use { reader ->
                            reader.lineSequence().forEach { line ->
                                if (cancelled.get()) return@forEach
                                val event = sseEventParser.parseOpenAiEvent(line)
                                if (event != null && !cancelled.get()) {
                                    trySend(event)
                                    if (event is StreamingEvent.Done || event is StreamingEvent.Error) {
                                        return@forEach
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!cancelled.get()) trySend(StreamingEvent.fromException(e))
                    } finally {
                        close()
                    }
                }
            }
        })

        awaitClose {
            cancelled.set(true)
            call.cancel()
        }
    }

    // ========== Reconnection ==========

    private fun handleDisconnect(reason: String, cause: Throwable? = null, reconnect: Boolean = true) {
        webSocket = null
        connId = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()

        _connectionState.value = GatewayConnectionState.Error(
            message = reason,
            cause = cause,
            isRetryable = reconnect
        )

        if (reconnect && shouldReconnect.get()) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delay = (INITIAL_RECONNECT_DELAY_MS * (1L shl minOf(reconnectAttempt, 5)))
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
            Log.d(TAG, "Reconnecting in ${delay}ms (attempt ${reconnectAttempt + 1})")
            delay(delay)
            reconnectAttempt++

            val config = currentConfig
            val token = currentToken
            if (config != null && token != null && shouldReconnect.get()) {
                doConnect(config, token)
            }
        }
    }

    // ========== Network Change Detection ==========

    private fun registerNetworkCallback() {
        if (connectivityManager == null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available — checking connection")
                if (_connectionState.value !is GatewayConnectionState.Connected &&
                    _connectionState.value !is GatewayConnectionState.Connecting &&
                    shouldReconnect.get()
                ) {
                    reconnectJob?.cancel()
                    reconnectAttempt = 0
                    val config = currentConfig
                    val token = currentToken
                    if (config != null && token != null) {
                        doConnect(config, token)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                connectivityManager?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback: ${e.message}")
            }
        }
        networkCallback = null
    }

    // ========== Helpers ==========

    private fun parseChannelType(type: String): ChannelType {
        return try {
            ChannelType.valueOf(type.uppercase())
        } catch (e: IllegalArgumentException) {
            ChannelType.UNKNOWN
        }
    }

    private fun parseRole(role: String?): OpenClawChatRole {
        return when (role?.lowercase()) {
            "user" -> OpenClawChatRole.USER
            "assistant" -> OpenClawChatRole.ASSISTANT
            "system" -> OpenClawChatRole.SYSTEM
            "tool" -> OpenClawChatRole.TOOL
            else -> OpenClawChatRole.ASSISTANT
        }
    }

    private fun createInsecureClient(baseClient: OkHttpClient): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return baseClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /** Release resources. */
    fun release() {
        disconnect()
        scope.cancel()
    }
}

