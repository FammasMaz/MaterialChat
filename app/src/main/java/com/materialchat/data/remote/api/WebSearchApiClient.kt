package com.materialchat.data.remote.api

import com.materialchat.data.remote.dto.ExaSearchRequest
import com.materialchat.data.remote.dto.ExaSearchResponse
import com.materialchat.data.remote.dto.SearxngSearchResponse
import com.materialchat.domain.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

/**
 * API client for web search providers (Exa and SearXNG).
 * Uses the standard (non-streaming) OkHttpClient.
 */
class WebSearchApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }
) {
    /**
     * Searches using the Exa API.
     */
    suspend fun searchExa(
        query: String,
        apiKey: String,
        maxResults: Int = 5
    ): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val requestDto = ExaSearchRequest(
                query = query,
                numResults = maxResults
            )

            val requestBody = json.encodeToString(requestDto)
                .toRequestBody(JSON_MEDIA_TYPE)

            val httpRequest = Request.Builder()
                .url("https://api.exa.ai/search")
                .addHeader("x-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(
                        IOException("Exa API error ${resp.code}: $errorBody")
                    )
                }

                val body = resp.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response from Exa"))

                val exaResponse = json.decodeFromString<ExaSearchResponse>(body)
                val results = exaResponse.results.mapIndexed { index, dto ->
                    val host = extractDomain(dto.url)
                    WebSearchResult(
                        index = index + 1,
                        url = dto.url,
                        title = dto.title ?: dto.url,
                        snippet = dto.highlights?.joinToString("\n")?.trim()
                            ?: dto.text?.take(600)?.trim()
                            ?: "",
                        imageUrl = dto.image,
                        faviconUrl = dto.favicon ?: buildFaviconUrl(host),
                        publishedDate = dto.publishedDate,
                        domain = host
                    )
                }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Searches using a SearXNG instance.
     */
    suspend fun searchSearxng(
        query: String,
        baseUrl: String,
        maxResults: Int = 5
    ): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = baseUrl.trimEnd('/')
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$normalizedUrl/search?q=$encodedQuery&format=json"

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    return@withContext Result.failure(
                        IOException("SearXNG error ${resp.code}: $errorBody")
                    )
                }

                val body = resp.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response from SearXNG"))

                val searxngResponse = json.decodeFromString<SearxngSearchResponse>(body)
                val results = searxngResponse.results
                    .take(maxResults)
                    .mapIndexed { index, dto ->
                        val host = extractDomain(dto.url)
                        WebSearchResult(
                            index = index + 1,
                            url = dto.url,
                            title = dto.title,
                            snippet = dto.content ?: "",
                            imageUrl = dto.imgSrc,
                            faviconUrl = buildFaviconUrl(host),
                            publishedDate = dto.publishedDate,
                            domain = host
                        )
                    }
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun extractDomain(url: String): String {
            return try {
                URI(url).host?.removePrefix("www.") ?: url
            } catch (e: Exception) {
                url
            }
        }

        fun buildFaviconUrl(host: String?): String? {
            if (host.isNullOrBlank()) return null
            return "https://www.google.com/s2/favicons?domain=$host&sz=128"
        }
    }
}
