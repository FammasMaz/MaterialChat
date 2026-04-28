package com.materialchat.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.materialchat.BuildConfig
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.domain.model.ProviderType
import com.materialchat.di.StandardClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class NativeAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPreferences: EncryptedPreferences,
    @StandardClient private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private val authFallbackClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .dns(cascadingDohDns())
            .build()
    }

    suspend fun authenticate(
        type: ProviderType,
        onStatus: (String) -> Unit = {}
    ): NativeAuthCredential = when (type) {
        ProviderType.CODEX_NATIVE -> authenticateOpenAiCodex(onStatus)
        ProviderType.GITHUB_COPILOT_NATIVE -> authenticateGitHubCopilot(onStatus)
        ProviderType.ANTIGRAVITY_NATIVE -> authenticateAntigravity(onStatus)
        else -> throw IllegalArgumentException("${type.displayName} does not support native OAuth")
    }

    suspend fun getFreshCredential(providerId: String, type: ProviderType): NativeAuthCredential? = withContext(Dispatchers.IO) {
        val stored = encryptedPreferences.getApiKey(providerId)
        val credential = NativeAuthCredential.decodeOrNull(stored) ?: return@withContext null
        if (!type.isNativeAuth || !credential.isExpired() || credential.refreshToken.isNullOrBlank()) {
            return@withContext credential
        }

        val refreshed = when (type) {
            ProviderType.CODEX_NATIVE -> refreshOpenAiCredential(credential)
            ProviderType.ANTIGRAVITY_NATIVE -> refreshGoogleCredential(credential, antigravity = true)
            else -> credential
        }
        encryptedPreferences.setApiKey(providerId, NativeAuthCredential.encode(refreshed))
        refreshed
    }

    suspend fun storeCredential(providerId: String, credential: NativeAuthCredential) {
        encryptedPreferences.setApiKey(providerId, NativeAuthCredential.encode(credential))
    }

    private suspend fun authenticateGitHubCopilot(
        onStatus: (String) -> Unit
    ): NativeAuthCredential = withContext(Dispatchers.IO) {
        val deviceBody = buildJsonObject {
            put("client_id", GITHUB_COPILOT_CLIENT_ID)
            put("scope", GITHUB_COPILOT_SCOPE)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val deviceRequest = Request.Builder()
            .url("https://github.com/login/device/code")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", COPILOT_USER_AGENT)
            .post(deviceBody)
            .build()

        val deviceData = executeJson(deviceRequest)
        val verificationUri = deviceData.string("verification_uri")
            ?: throw IOException("GitHub did not return a verification URL")
        val userCode = deviceData.string("user_code")
            ?: throw IOException("GitHub did not return a user code")
        val deviceCode = deviceData.string("device_code")
            ?: throw IOException("GitHub did not return a device code")
        var intervalSeconds = deviceData.double("interval")?.roundToLong()?.coerceAtLeast(5L) ?: 5L

        onStatus("Open $verificationUri and enter code $userCode")
        openBrowser(verificationUri)

        while (true) {
            delay((intervalSeconds + GITHUB_POLLING_SAFETY_SECONDS) * 1000L)
            val tokenBody = buildJsonObject {
                put("client_id", GITHUB_COPILOT_CLIENT_ID)
                put("device_code", deviceCode)
                put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val tokenRequest = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", COPILOT_USER_AGENT)
                .post(tokenBody)
                .build()

            val tokenData = executeJson(tokenRequest)
            val accessToken = tokenData.string("access_token")
            if (!accessToken.isNullOrBlank()) {
                onStatus("GitHub Copilot authentication complete")
                return@withContext NativeAuthCredential(
                    providerType = ProviderType.GITHUB_COPILOT_NATIVE.name,
                    accessToken = accessToken,
                    expiryDate = 0L
                )
            }

            when (val error = tokenData.string("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalSeconds += 5L
                "expired_token" -> throw IOException("GitHub device code expired. Please try again.")
                "access_denied" -> throw IOException("GitHub authorization was denied.")
                null -> Unit
                else -> throw IOException("GitHub OAuth error: $error")
            }
        }
        throw IOException("GitHub OAuth flow ended unexpectedly")
    }

    private suspend fun authenticateOpenAiCodex(
        onStatus: (String) -> Unit
    ): NativeAuthCredential = withContext(Dispatchers.IO) {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = codeChallenge(codeVerifier)
        val state = randomUrlSafe(24)
        val redirectUri = "http://localhost:$OPENAI_CALLBACK_PORT$OPENAI_CALLBACK_PATH"
        val authUrl = buildUrl("https://auth.openai.com/oauth/authorize", mapOf(
            "response_type" to "code",
            "client_id" to OPENAI_CLIENT_ID,
            "redirect_uri" to redirectUri,
            "scope" to OPENAI_SCOPES.joinToString(" "),
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
            "originator" to OPENAI_ORIGINATOR
        ))

        onStatus("Complete OpenAI Codex sign-in in Chrome")
        val credential = waitForOpenAiCredential(OPENAI_CALLBACK_PORT, OPENAI_CALLBACK_PATH, state, {
            openBrowser(authUrl)
        }) { code ->
            onStatus("Authorization received. Exchanging OpenAI tokens...")
            exchangeOpenAiAuthorizationCode(code, codeVerifier, redirectUri)
        }

        onStatus("OpenAI Codex authentication complete")
        credential
    }

    private suspend fun authenticateAntigravity(
        onStatus: (String) -> Unit
    ): NativeAuthCredential = withContext(Dispatchers.IO) {
        requireAntigravityOauthConfig()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = codeChallenge(codeVerifier)
        val state = randomUrlSafe(24)
        val redirectUri = "http://localhost:$ANTIGRAVITY_CALLBACK_PORT$ANTIGRAVITY_CALLBACK_PATH"
        val authUrl = buildUrl("https://accounts.google.com/o/oauth2/v2/auth", mapOf(
            "client_id" to ANTIGRAVITY_CLIENT_ID,
            "redirect_uri" to redirectUri,
            "scope" to ANTIGRAVITY_SCOPES.joinToString(" "),
            "access_type" to "offline",
            "response_type" to "code",
            "prompt" to "consent",
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state
        ))

        onStatus("Complete Google Antigravity sign-in in your browser")
        val code = waitForLoopbackCode(ANTIGRAVITY_CALLBACK_PORT, ANTIGRAVITY_CALLBACK_PATH, state) {
            openBrowser(authUrl)
        }

        val formBody = FormBody.Builder()
            .add("code", code)
            .add("client_id", ANTIGRAVITY_CLIENT_ID)
            .add("client_secret", ANTIGRAVITY_CLIENT_SECRET)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .add("code_verifier", codeVerifier)
            .build()
        val request = Request.Builder()
            .url(GOOGLE_TOKEN_URL)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        val tokenData = executeJson(request)
        val accessToken = tokenData.string("access_token")
        val refreshToken = tokenData.string("refresh_token")
        val idToken = tokenData.string("id_token")
        val expiresIn = tokenData.double("expires_in") ?: 3600.0
        val email = runCatching { fetchGoogleEmail(accessToken.orEmpty()) }.getOrNull()

        val credential = NativeAuthCredential(
            providerType = ProviderType.ANTIGRAVITY_NATIVE.name,
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            email = email,
            expiryDate = System.currentTimeMillis() + (expiresIn * 1000).toLong(),
            clientId = ANTIGRAVITY_CLIENT_ID,
            clientSecret = ANTIGRAVITY_CLIENT_SECRET,
            tokenUri = GOOGLE_TOKEN_URL
        )
        val projectId = discoverAntigravityProject(credential) ?: ANTIGRAVITY_FALLBACK_PROJECT_ID

        onStatus("Antigravity authentication complete")
        credential.copy(projectId = projectId)
    }

    private suspend fun refreshOpenAiCredential(credential: NativeAuthCredential): NativeAuthCredential {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", credential.refreshToken.orEmpty())
            .add("client_id", OPENAI_CLIENT_ID)
            .build()
        val request = Request.Builder()
            .url("https://auth.openai.com/oauth/token")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        val tokenData = executeJson(request)
        val expiresIn = tokenData.double("expires_in") ?: 3600.0
        val accessToken = tokenData.string("access_token") ?: credential.accessToken
        val idToken = tokenData.string("id_token") ?: credential.idToken
        val accessClaims = parseJwtPayload(accessToken)
        val idClaims = parseJwtPayload(idToken)
        val accountId = accessClaims?.get("https://api.openai.com/auth")?.jsonObject
            ?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull
            ?: idClaims?.get("https://api.openai.com/auth")?.jsonObject
                ?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull
            ?: credential.accountId
        return credential.copy(
            accessToken = accessToken,
            refreshToken = tokenData.string("refresh_token") ?: credential.refreshToken,
            idToken = idToken,
            accountId = accountId,
            expiryDate = System.currentTimeMillis() + (expiresIn * 1000).toLong()
        )
    }

    private suspend fun refreshGoogleCredential(
        credential: NativeAuthCredential,
        antigravity: Boolean
    ): NativeAuthCredential {
        val clientId = credential.clientId ?: if (antigravity) ANTIGRAVITY_CLIENT_ID else ""
        val clientSecret = credential.clientSecret ?: if (antigravity) ANTIGRAVITY_CLIENT_SECRET else ""
        if (antigravity && (clientId.isBlank() || clientSecret.isBlank())) {
            throw IOException("Antigravity OAuth client configuration is missing from this build")
        }
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", credential.refreshToken.orEmpty())
            .add("grant_type", "refresh_token")
            .build()
        val request = Request.Builder()
            .url(credential.tokenUri ?: GOOGLE_TOKEN_URL)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        val tokenData = executeJson(request)
        val expiresIn = tokenData.double("expires_in") ?: 3600.0
        val refreshed = credential.copy(
            accessToken = tokenData.string("access_token") ?: credential.accessToken,
            refreshToken = tokenData.string("refresh_token") ?: credential.refreshToken,
            expiryDate = System.currentTimeMillis() + (expiresIn * 1000).toLong()
        )
        return if (antigravity && refreshed.projectId.isNullOrBlank()) {
            refreshed.copy(projectId = discoverAntigravityProject(refreshed) ?: ANTIGRAVITY_FALLBACK_PROJECT_ID)
        } else {
            refreshed
        }
    }

    private fun exchangeOpenAiAuthorizationCode(
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): NativeAuthCredential {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", OPENAI_CLIENT_ID)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .build()
        val request = Request.Builder()
            .url("https://auth.openai.com/oauth/token")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        val tokenData = executeJson(request)
        val accessToken = tokenData.string("access_token")
            ?: throw IOException("OpenAI token response did not include an access token")
        val refreshToken = tokenData.string("refresh_token")
            ?: throw IOException("OpenAI token response did not include a refresh token")
        val idToken = tokenData.string("id_token")
        val expiresIn = tokenData.double("expires_in") ?: 3600.0
        val accessClaims = parseJwtPayload(accessToken)
        val idClaims = parseJwtPayload(idToken)
        val accountId = accessClaims?.get("https://api.openai.com/auth")?.jsonObject
            ?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull
            ?: idClaims?.get("https://api.openai.com/auth")?.jsonObject
                ?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull
        val email = idClaims?.get("email")?.jsonPrimitive?.contentOrNull

        return NativeAuthCredential(
            providerType = ProviderType.CODEX_NATIVE.name,
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            accountId = accountId,
            email = email,
            expiryDate = System.currentTimeMillis() + (expiresIn * 1000).toLong()
        )
    }

    private fun exchangeOpenAiApiKey(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
            .add("client_id", OPENAI_CLIENT_ID)
            .add("requested_token", "openai-api-key")
            .add("subject_token", idToken)
            .add("subject_token_type", "urn:ietf:params:oauth:token-type:id_token")
            .add("name", "MaterialChat native Codex")
            .build()
        val request = Request.Builder()
            .url("https://auth.openai.com/oauth/token")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        return executeJson(request).string("access_token")
    }

    private suspend fun discoverAntigravityProject(credential: NativeAuthCredential): String? {
        val token = credential.accessToken ?: return null
        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "User-Agent" to "google-api-nodejs-client/9.15.1",
            "X-Goog-Api-Client" to "google-cloud-sdk vscode_cloudshelleditor/0.1",
            "Client-Metadata" to "{\"ideType\":\"IDE_UNSPECIFIED\",\"platform\":\"PLATFORM_UNSPECIFIED\",\"pluginType\":\"GEMINI\"}"
        )
        val body = "{\"metadata\":{\"ideType\":\"IDE_UNSPECIFIED\",\"platform\":\"PLATFORM_UNSPECIFIED\",\"pluginType\":\"GEMINI\"}}"
            .toRequestBody(JSON_MEDIA_TYPE)

        for (endpoint in ANTIGRAVITY_LOAD_ENDPOINTS) {
            val requestBuilder = Request.Builder()
                .url("$endpoint:loadCodeAssist")
                .post(body)
            headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
            val request = requestBuilder.build()
            val project = runCatching { findProjectId(executeJson(request)) }.getOrNull()
            if (!project.isNullOrBlank()) return project
        }
        return null
    }

    private fun findProjectId(value: kotlinx.serialization.json.JsonElement): String? {
        if (value is kotlinx.serialization.json.JsonObject) {
            for ((key, nested) in value) {
                if (key.equals("projectId", ignoreCase = true) || key.equals("project", ignoreCase = true)) {
                    val text = nested.jsonPrimitive.contentOrNull
                    if (!text.isNullOrBlank()) return text
                }
                findProjectId(nested)?.let { return it }
            }
        }
        if (value is kotlinx.serialization.json.JsonArray) {
            value.forEach { nested -> findProjectId(nested)?.let { return it } }
        }
        return null
    }

    private fun requireAntigravityOauthConfig() {
        if (ANTIGRAVITY_CLIENT_ID.isBlank() || ANTIGRAVITY_CLIENT_SECRET.isBlank()) {
            throw IOException(
                "Antigravity OAuth client configuration is missing from this build. " +
                    "Set ANTIGRAVITY_CLIENT_ID and ANTIGRAVITY_CLIENT_SECRET before building."
            )
        }
    }

    private fun fetchGoogleEmail(accessToken: String): String? {
        if (accessToken.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v1/userinfo")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return executeJsonBlocking(request).string("email")
    }

    private fun waitForOpenAiCredential(
        port: Int,
        expectedPath: String,
        expectedState: String,
        openBrowser: () -> Unit,
        exchangeCode: (String) -> NativeAuthCredential
    ): NativeAuthCredential {
        ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = LOOPBACK_TIMEOUT_MS
            openBrowser()
            while (true) {
                server.accept().use { socket ->
                    val result = handleOpenAiLoopbackSocket(socket, expectedPath, expectedState, exchangeCode)
                    if (result != null) return result
                }
            }
        }
    }

    private fun handleOpenAiLoopbackSocket(
        socket: Socket,
        expectedPath: String,
        expectedState: String,
        exchangeCode: (String) -> NativeAuthCredential
    ): NativeAuthCredential? {
        val reader = BufferedReader(socket.getInputStream().reader())
        val writer = PrintWriter(socket.getOutputStream())
        val requestLine = reader.readLine().orEmpty()
        while (reader.readLine()?.isNotEmpty() == true) {
            // Drain headers
        }
        val pathWithQuery = requestLine.split(" ").getOrNull(1).orEmpty()
        val uri = Uri.parse("http://localhost$pathWithQuery")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        val result = when {
            error != null -> LoopbackResult.Failure("Authentication failed", "OAuth failed: $error")
            uri.path != expectedPath -> LoopbackResult.Ignore("Unexpected callback")
            state != expectedState -> LoopbackResult.Failure("Authentication failed", "State mismatch. Please retry sign-in.")
            code.isNullOrBlank() -> LoopbackResult.Failure("Authentication failed", "No authorization code received.")
            else -> runCatching { exchangeCode(code) }.fold(
                onSuccess = { credential ->
                    writeLoopbackHtml(
                        writer = writer,
                        title = "Authentication successful",
                        message = "MaterialChat has received your OpenAI Codex credentials. Return to the app and tap Save to finish.",
                        autoReturnToApp = true
                    )
                    return credential
                },
                onFailure = { throwable ->
                    LoopbackResult.Failure(
                        title = "Authentication failed",
                        message = throwable.message ?: "Token exchange failed. Return to MaterialChat and try again."
                    )
                }
            )
        }

        when (result) {
            is LoopbackResult.Failure -> {
                writeLoopbackHtml(writer, result.title, result.message, isError = true)
                throw IOException(result.message)
            }
            is LoopbackResult.Ignore -> {
                writeLoopbackHtml(writer, result.title, "Return to MaterialChat and retry sign-in.", isError = true)
                return null
            }
        }
    }

    private fun waitForLoopbackCode(
        port: Int,
        expectedPath: String,
        expectedState: String,
        openBrowser: () -> Unit
    ): String {
        ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = LOOPBACK_TIMEOUT_MS
            openBrowser()
            while (true) {
                server.accept().use { socket ->
                    val result = handleLoopbackSocket(socket, expectedPath, expectedState)
                    if (result != null) return result
                }
            }
        }
    }

    private fun handleLoopbackSocket(
        socket: Socket,
        expectedPath: String,
        expectedState: String
    ): String? {
        val reader = BufferedReader(socket.getInputStream().reader())
        val writer = PrintWriter(socket.getOutputStream())
        val requestLine = reader.readLine().orEmpty()
        while (reader.readLine()?.isNotEmpty() == true) {
            // Drain headers
        }
        val pathWithQuery = requestLine.split(" ").getOrNull(1).orEmpty()
        val uri = Uri.parse("http://localhost$pathWithQuery")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        val html = when {
            error != null -> "<h1>Authentication failed</h1><p>${escapeHtml(error)}</p>"
            uri.path != expectedPath -> "<h1>Unexpected callback</h1>"
            state != expectedState -> "<h1>State mismatch</h1><p>Please retry sign-in.</p>"
            code != null -> "<h1>Authentication successful</h1><p>You can close this tab and return to MaterialChat.</p>"
            else -> "<h1>Authentication failed</h1><p>No authorization code received.</p>"
        }
        writer.print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n<html><body>$html</body></html>")
        writer.flush()
        if (error != null) throw IOException("OAuth failed: $error")
        if (uri.path == expectedPath && state == expectedState && !code.isNullOrBlank()) return code
        return null
    }

    private fun writeLoopbackHtml(
        writer: PrintWriter,
        title: String,
        message: String,
        isError: Boolean = false,
        autoReturnToApp: Boolean = false
    ) {
        val safeTitle = escapeHtml(title)
        val safeMessage = escapeHtml(message)
        val color = if (isError) "#b3261e" else "#0f5132"
        val appUrl = "materialchat://oauth/codex-complete"
        val autoReturn = if (autoReturnToApp) {
            """
            <meta http-equiv="refresh" content="1;url=$appUrl">
            <script>setTimeout(function(){ window.location.href = '$appUrl'; }, 700);</script>
            """.trimIndent()
        } else {
            ""
        }
        val returnLink = if (autoReturnToApp) {
            """
            <p>If you are not returned automatically, <a href="$appUrl">tap here to return to MaterialChat</a>.</p>
            <p>You can also close this tab and switch back manually.</p>
            """.trimIndent()
        } else {
            "<p>Close this tab and return to MaterialChat.</p>"
        }
        val html = """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                $autoReturn
                <title>$safeTitle</title>
                <style>
                    body { font-family: sans-serif; padding: 32px; line-height: 1.45; color: #1b1b1f; }
                    h1 { color: $color; }
                    a { color: #0057d9; }
                </style>
            </head>
            <body>
                <h1>$safeTitle</h1>
                <p>$safeMessage</p>
                $returnLink
            </body>
            </html>
        """.trimIndent()
        writer.print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$html")
        writer.flush()
    }

    private sealed class LoopbackResult {
        data class Failure(val title: String, val message: String) : LoopbackResult()
        data class Ignore(val title: String) : LoopbackResult()
    }

    private fun openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun executeJson(request: Request): kotlinx.serialization.json.JsonObject {
        return executeJsonBlocking(request)
    }

    private fun executeJsonBlocking(request: Request): kotlinx.serialization.json.JsonObject {
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: UnknownHostException) {
            executeWithAuthDnsFallback(request, e)
        } catch (e: IOException) {
            throw IOException("Network request to ${request.url.host} failed: ${e.message}", e)
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw IOException("HTTP ${it.code}: ${body.take(500)}")
            }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun executeWithAuthDnsFallback(request: Request, systemDnsError: UnknownHostException): Response {
        return try {
            authFallbackClient.newCall(request).execute()
        } catch (fallbackDnsError: UnknownHostException) {
            systemDnsError.addSuppressed(fallbackDnsError)
            throw IOException(
                "MaterialChat could not resolve ${request.url.host} from inside the app. " +
                    "Chrome may still work because it uses separate DNS. Try disabling Android Private DNS/VPN, " +
                    "or retry on another network.",
                systemDnsError
            )
        } catch (e: IOException) {
            throw IOException("Network request to ${request.url.host} failed after DNS fallback: ${e.message}", e)
        }
    }

    private fun cascadingDohDns(): Dns {
        val resolvers = listOf(
            dnsOverHttps("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
            dnsOverHttps("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
            dnsOverHttps("https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112")
        )
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                var firstError: UnknownHostException? = null
                for (resolver in resolvers) {
                    try {
                        val addresses = resolver.lookup(hostname)
                        if (addresses.isNotEmpty()) return addresses
                    } catch (e: UnknownHostException) {
                        firstError?.addSuppressed(e) ?: run { firstError = e }
                    }
                }
                throw firstError ?: UnknownHostException(hostname)
            }
        }
    }

    private fun dnsOverHttps(url: String, vararg bootstrapHosts: String): DnsOverHttps {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        val addresses = bootstrapHosts.map { InetAddress.getByName(it) }.toTypedArray()
        return DnsOverHttps.Builder()
            .client(client)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(*addresses)
            .build()
    }

    private fun parseJwtPayload(jwt: String?): kotlinx.serialization.json.JsonObject? {
        if (jwt.isNullOrBlank()) return null
        val payload = jwt.split(".").getOrNull(1) ?: return null
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        return runCatching { json.parseToJsonElement(String(decoded)).jsonObject }.getOrNull()
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        return base + "?" + params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun generateCodeVerifier(): String = randomUrlSafe(32)

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return Base64.encodeToString(buffer, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun escapeHtml(value: String): String {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private fun kotlinx.serialization.json.JsonObject.double(key: String): Double? {
        return this[key]?.jsonPrimitive?.doubleOrNull
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val LOOPBACK_TIMEOUT_MS = 5 * 60 * 1000

        const val COPILOT_USER_AGENT = "opencode/1.1.36"
        const val GITHUB_COPILOT_CLIENT_ID = "Ov23li8tweQw6odWQebz"
        const val GITHUB_COPILOT_SCOPE = "read:user"
        const val GITHUB_POLLING_SAFETY_SECONDS = 3L

        const val OPENAI_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val OPENAI_ORIGINATOR = "pi"
        val OPENAI_SCOPES = listOf("openid", "profile", "email", "offline_access")
        const val OPENAI_CALLBACK_PORT = 1455
        const val OPENAI_CALLBACK_PATH = "/auth/callback"

        const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
        val ANTIGRAVITY_CLIENT_ID: String
            get() = BuildConfig.ANTIGRAVITY_CLIENT_ID
        val ANTIGRAVITY_CLIENT_SECRET: String
            get() = BuildConfig.ANTIGRAVITY_CLIENT_SECRET
        val ANTIGRAVITY_SCOPES = listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs"
        )
        const val ANTIGRAVITY_CALLBACK_PORT = 51121
        const val ANTIGRAVITY_CALLBACK_PATH = "/oauthcallback"
        const val ANTIGRAVITY_FALLBACK_PROJECT_ID = "rising-fact-p41fc"
        val ANTIGRAVITY_LOAD_ENDPOINTS = listOf(
            "https://cloudcode-pa.googleapis.com/v1internal",
            "https://daily-cloudcode-pa.sandbox.googleapis.com/v1internal"
        )
    }
}
