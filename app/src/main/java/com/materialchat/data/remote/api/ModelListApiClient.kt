package com.materialchat.data.remote.api

import com.materialchat.data.auth.NativeAuthCredential
import com.materialchat.data.remote.dto.OllamaModelsResponse
import com.materialchat.data.remote.dto.OpenAiModelsResponse
import com.materialchat.domain.model.AiModel
import com.materialchat.domain.model.Provider
import com.materialchat.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API client for fetching available models from AI providers.
 *
 * Supports:
 * - OpenAI-compatible APIs: GET /v1/models
 * - Ollama: GET /api/tags
 */
class ModelListApiClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {

    /**
     * Fetches available models from the given provider.
     *
     * @param provider The AI provider to query
     * @param apiKey The API key (required for OpenAI-compatible providers)
     * @return Result containing list of available models or error
     */
    suspend fun fetchModels(
        provider: Provider,
        apiKey: String?
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> fetchOpenAiModels(
                baseUrl = provider.baseUrl,
                providerId = provider.id,
                apiKey = apiKey ?: ""
            )
            ProviderType.OLLAMA_NATIVE -> fetchOllamaModels(
                baseUrl = provider.baseUrl,
                providerId = provider.id
            )
            ProviderType.GITHUB_COPILOT_NATIVE -> fetchGitHubCopilotModels(
                baseUrl = provider.baseUrl,
                providerId = provider.id,
                credentialJson = apiKey
            )
            ProviderType.CODEX_NATIVE -> Result.success(CODEX_MODELS.map { model ->
                AiModel(id = model, name = model, providerId = provider.id)
            })
            ProviderType.ANTIGRAVITY_NATIVE -> Result.success(ANTIGRAVITY_MODELS.map { model ->
                AiModel(id = model, name = model, providerId = provider.id)
            })
        }
    }

    /**
     * Fetches models from an OpenAI-compatible API.
     *
     * Endpoint: GET /v1/models
     *
     * @param baseUrl The API base URL
     * @param providerId The provider ID for model association
     * @param apiKey The API key for authentication
     * @return Result containing list of AiModels
     */
    private suspend fun fetchOpenAiModels(
        baseUrl: String,
        providerId: String,
        apiKey: String
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val urls = ChatApiClient.buildModelsUrlsWithFallback(baseUrl)
        var lastFailure: Result<List<AiModel>>? = null

        for (url in urls) {
            val result = tryFetchOpenAiModels(url, providerId, apiKey)
            if (result.isSuccess) return@withContext result

            // If it's a 404, try the next fallback URL
            val error = result.exceptionOrNull()
            if (error is ApiException && error.isNotFound) {
                lastFailure = result
                continue
            }
            // For non-404 errors, fail immediately
            return@withContext result
        }

        lastFailure ?: Result.failure(ApiException(code = -1, message = "No URLs to try"))
    }

    /**
     * Attempts to fetch models from a single URL.
     */
    private suspend fun tryFetchOpenAiModels(
        url: String,
        providerId: String,
        apiKey: String
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        ApiException(
                            code = resp.code,
                            message = "Failed to fetch models: HTTP ${resp.code}"
                        )
                    )
                }

                val body = resp.body?.string() ?: return@withContext Result.failure(
                    ApiException(code = -1, message = "Empty response body")
                )

                // Check if response is JSON (some providers return HTML error pages)
                val contentType = resp.header("Content-Type") ?: ""
                if (!contentType.contains("application/json") && body.trimStart().startsWith("<")) {
                    return@withContext Result.failure(
                        ApiException(
                            code = resp.code,
                            message = "Provider returned HTML instead of JSON. Check if the base URL is correct and the models endpoint is supported."
                        )
                    )
                }

                val modelsResponse = json.decodeFromString<OpenAiModelsResponse>(body)

                val models = modelsResponse.data.map { modelData ->
                    AiModel(
                        id = modelData.id,
                        name = modelData.id,
                        providerId = providerId
                    )
                }.sortedBy { it.id }

                Result.success(models)
            }
        } catch (e: IOException) {
            Result.failure(
                ApiException(
                    code = -1,
                    message = "Network error: ${e.message}",
                    cause = e
                )
            )
        } catch (e: Exception) {
            Result.failure(
                ApiException(
                    code = -1,
                    message = "Failed to parse models response: ${e.message}",
                    cause = e
                )
            )
        }
    }

    private suspend fun fetchGitHubCopilotModels(
        baseUrl: String,
        providerId: String,
        credentialJson: String?
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        val credential = NativeAuthCredential.decodeOrNull(credentialJson)
        val token = credential?.accessToken
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(ApiException(code = 401, message = "GitHub Copilot is not authenticated"))
        }

        try {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/models")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("User-Agent", "opencode/1.1.36")
                .addHeader("Accept", "application/json")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        ApiException(code = resp.code, message = "Failed to fetch Copilot models: HTTP ${resp.code}")
                    )
                }
                val body = resp.body?.string().orEmpty()
                val modelsResponse = json.decodeFromString<OpenAiModelsResponse>(body)
                val models = modelsResponse.data
                    .mapNotNull { modelData -> modelData.id.takeIf { !it.contains("embedding", ignoreCase = true) } }
                    .map { id -> AiModel(id = id, name = id, providerId = providerId) }
                    .sortedBy { it.id }
                Result.success(models.ifEmpty {
                    GITHUB_COPILOT_MODELS.map { model -> AiModel(id = model, name = model, providerId = providerId) }
                })
            }
        } catch (e: Exception) {
            Result.success(GITHUB_COPILOT_MODELS.map { model ->
                AiModel(id = model, name = model, providerId = providerId)
            })
        }
    }

    /**
     * Fetches models from an Ollama server.
     *
     * Endpoint: GET /api/tags
     *
     * @param baseUrl The Ollama server URL
     * @param providerId The provider ID for model association
     * @return Result containing list of AiModels
     */
    private suspend fun fetchOllamaModels(
        baseUrl: String,
        providerId: String
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/tags"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        ApiException(
                            code = resp.code,
                            message = "Failed to fetch models: HTTP ${resp.code}"
                        )
                    )
                }

                val body = resp.body?.string() ?: return@withContext Result.failure(
                    ApiException(code = -1, message = "Empty response body")
                )

                // Check if response is JSON (some servers return HTML error pages)
                val contentType = resp.header("Content-Type") ?: ""
                if (!contentType.contains("application/json") && body.trimStart().startsWith("<")) {
                    return@withContext Result.failure(
                        ApiException(
                            code = resp.code,
                            message = "Server returned HTML instead of JSON. Check if the Ollama server URL is correct and the /api/tags endpoint is accessible."
                        )
                    )
                }

                val modelsResponse = json.decodeFromString<OllamaModelsResponse>(body)

                val models = modelsResponse.models.map { modelInfo ->
                    AiModel(
                        id = modelInfo.name,
                        name = formatOllamaModelName(modelInfo.name),
                        providerId = providerId
                    )
                }.sortedBy { it.id }

                Result.success(models)
            }
        } catch (e: IOException) {
            Result.failure(
                ApiException(
                    code = -1,
                    message = "Network error: ${e.message}",
                    cause = e
                )
            )
        } catch (e: Exception) {
            Result.failure(
                ApiException(
                    code = -1,
                    message = "Failed to parse models response: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * Formats Ollama model names for display.
     *
     * Ollama model names often include tags like ":latest" which can be simplified.
     *
     * @param modelName The raw model name (e.g., "llama3.2:latest")
     * @return Formatted display name (e.g., "llama3.2")
     */
    private fun formatOllamaModelName(modelName: String): String {
        // Remove ":latest" tag if present, as it's the default
        return if (modelName.endsWith(":latest")) {
            modelName.removeSuffix(":latest")
        } else {
            modelName
        }
    }

    companion object {
        /**
         * Default timeout for model list requests.
         */
        private const val REQUEST_TIMEOUT_SECONDS = 30L

        private val CODEX_MODELS = listOf(
            "gpt-5",
            "gpt-5.1",
            "gpt-5.2",
            "gpt-5-codex",
            "gpt-5-codex-mini",
            "gpt-5.1-codex",
            "gpt-5.1-codex-max",
            "gpt-5.1-codex-mini",
            "gpt-5.2-codex",
            "gpt-5.3-codex",
            "gpt-5.4",
            "gpt-5.5"
        )

        private val GITHUB_COPILOT_MODELS = listOf(
            "gpt-5",
            "gpt-5.1",
            "gpt-5.1-codex",
            "gpt-5.1-codex-mini",
            "gpt-5.1-codex-max",
            "gpt-5.2",
            "gpt-4o",
            "gpt-4.1",
            "o3",
            "o4-mini",
            "claude-sonnet-4",
            "claude-sonnet-4.5",
            "claude-sonnet-4.6",
            "claude-opus-4",
            "claude-opus-4.5",
            "claude-opus-4.6",
            "claude-haiku-4.5",
            "gemini-2.5-pro",
            "gemini-3-flash-preview",
            "gemini-3-pro-preview",
            "grok-code-fast-1"
        )

        private val ANTIGRAVITY_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3-pro-preview",
            "gemini-3-flash",
            "gemini-3-flash-agent",
            "gemini-3.1-pro",
            "claude-sonnet-4.5",
            "claude-sonnet-4.6",
            "claude-opus-4.5",
            "claude-opus-4.6",
            "gpt-oss-120b-medium"
        )

        /**
         * Creates a default OkHttpClient for model fetching.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Exception thrown when an API request fails.
 *
 * @property code HTTP status code or -1 for non-HTTP errors
 * @property message Human-readable error message
 * @property cause The underlying exception, if any
 */
class ApiException(
    val code: Int,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Whether this error might be resolved by retrying.
     */
    val isRecoverable: Boolean
        get() = code == 429 || code >= 500

    /**
     * Whether this is an authentication error.
     */
    val isAuthError: Boolean
        get() = code == 401 || code == 403

    /**
     * Whether this is a not found error.
     */
    val isNotFound: Boolean
        get() = code == 404
}
