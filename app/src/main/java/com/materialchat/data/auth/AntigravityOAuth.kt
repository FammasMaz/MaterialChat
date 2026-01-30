package com.materialchat.data.auth

import com.materialchat.domain.model.AntigravityConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Antigravity-specific OAuth helper functions.
 *
 * Provides utilities for:
 * - Fetching user info after authentication
 * - Resolving project ID for API requests
 * - Endpoint health checking with fallback logic
 * - Building authenticated request headers
 */
@Singleton
class AntigravityOAuth @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    /**
     * User info retrieved from Google's userinfo endpoint.
     */
    data class UserInfo(
        val email: String,
        val name: String?,
        val picture: String?
    )

    /**
     * Project info for Antigravity API requests.
     */
    data class ProjectInfo(
        val projectId: String,
        val endpoint: String
    )

    /**
     * Fetches user info from Google's userinfo API.
     *
     * @param accessToken Valid OAuth access token
     * @return UserInfo containing email and profile data
     * @throws OAuthException.UserInfoFailed on failure
     */
    suspend fun fetchUserInfo(accessToken: String): UserInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(AntigravityConfig.USERINFO_URL)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw OAuthException.UserInfoFailed(
                    "HTTP ${response.code}: ${response.body?.string() ?: "Unknown error"}"
                )
            }

            val body = response.body?.string()
                ?: throw OAuthException.UserInfoFailed("Empty response from userinfo endpoint")

            parseUserInfo(body)
        } catch (e: IOException) {
            throw OAuthException.NetworkError(e)
        } catch (e: OAuthException) {
            throw e
        } catch (e: Exception) {
            throw OAuthException.UserInfoFailed(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Resolves the best endpoint and project ID for Antigravity API requests.
     *
     * Tries endpoints in order (daily -> autopush -> prod) and uses the first
     * responsive one. Also attempts to resolve the user's project ID.
     *
     * @param accessToken Valid OAuth access token
     * @return ProjectInfo containing the resolved endpoint and project ID
     */
    suspend fun resolveProjectInfo(accessToken: String): ProjectInfo = withContext(Dispatchers.IO) {
        var workingEndpoint = AntigravityConfig.ENDPOINT_PROD
        var projectId = AntigravityConfig.DEFAULT_PROJECT_ID

        // Try endpoints in order, use first working one
        for (endpoint in AntigravityConfig.ENDPOINT_FALLBACKS) {
            if (isEndpointHealthy(endpoint, accessToken)) {
                workingEndpoint = endpoint

                // Try to resolve project ID from this endpoint
                val resolvedProjectId = tryResolveProjectId(endpoint, accessToken)
                if (resolvedProjectId != null) {
                    projectId = resolvedProjectId
                }
                break
            }
        }

        ProjectInfo(projectId = projectId, endpoint = workingEndpoint)
    }

    /**
     * Builds HTTP headers for Antigravity API requests.
     *
     * @param accessToken Valid OAuth access token
     * @param projectId Resolved project ID
     * @return Map of headers to include in API requests
     */
    fun buildRequestHeaders(accessToken: String, projectId: String): Map<String, String> {
        return buildMap {
            put("Authorization", "Bearer $accessToken")
            put("X-Goog-User-Project", projectId)
            AntigravityConfig.REQUEST_HEADERS.forEach { (key, value) ->
                put(key, value)
            }
        }
    }

    /**
     * Builds the full API URL for Antigravity chat completions.
     *
     * @param endpoint The Antigravity endpoint base URL
     * @param projectId The project ID
     * @param modelId The model to use
     * @return Full URL for the generateContent API
     */
    fun buildChatUrl(endpoint: String, projectId: String, modelId: String): String {
        // The Antigravity API uses Gemini-style paths
        return "$endpoint/v1internal/projects/$projectId/locations/us-central1/publishers/google/models/$modelId:generateContent"
    }

    /**
     * Builds the streaming API URL for Antigravity chat.
     *
     * @param endpoint The Antigravity endpoint base URL
     * @param projectId The project ID
     * @param modelId The model to use
     * @return Full URL for streaming generateContent API
     */
    fun buildStreamingChatUrl(endpoint: String, projectId: String, modelId: String): String {
        return "$endpoint/v1internal/projects/$projectId/locations/us-central1/publishers/google/models/$modelId:streamGenerateContent?alt=sse"
    }

    // ============================================================================
    // Private Helper Methods
    // ============================================================================

    private fun parseUserInfo(responseBody: String): UserInfo {
        val jsonObject = json.parseToJsonElement(responseBody).jsonObject

        val email = jsonObject["email"]?.jsonPrimitive?.content
            ?: throw OAuthException.UserInfoFailed("Missing email in userinfo response")

        val name = jsonObject["name"]?.jsonPrimitive?.content
        val picture = jsonObject["picture"]?.jsonPrimitive?.content

        return UserInfo(
            email = email,
            name = name,
            picture = picture
        )
    }

    private fun isEndpointHealthy(endpoint: String, accessToken: String): Boolean {
        return try {
            // Simple health check - try to access the endpoint
            val request = Request.Builder()
                .url("$endpoint/v1internal")
                .addHeader("Authorization", "Bearer $accessToken")
                .head()
                .build()

            val response = httpClient.newCall(request).execute()
            // Accept any non-5xx response as "healthy"
            response.code < 500
        } catch (e: Exception) {
            false
        }
    }

    private fun tryResolveProjectId(endpoint: String, accessToken: String): String? {
        return try {
            // Try to get project ID from Antigravity's project resolution endpoint
            val request = Request.Builder()
                .url("$endpoint/v1internal/projects/-")
                .addHeader("Authorization", "Bearer $accessToken")
                .apply {
                    AntigravityConfig.REQUEST_HEADERS.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val jsonObject = json.parseToJsonElement(body).jsonObject
                jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("projects/")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
