package com.materialchat.data.remote.api

import com.materialchat.domain.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for fetching release information from GitHub Releases API.
 *
 * Uses the public GitHub API (no authentication required for public repos).
 */
@Singleton
class GitHubReleaseApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val GITHUB_OWNER = "FammasMaz"
        private const val GITHUB_REPO = "MaterialChat"
        private const val API_BASE_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO"
        private const val RELEASES_LATEST_URL = "$API_BASE_URL/releases/latest"
    }

    /**
     * Fetches the latest release information from GitHub.
     *
     * @return Result containing AppUpdate on success, or exception on failure
     */
    suspend fun getLatestRelease(): Result<AppUpdate> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to fetch release: HTTP ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val release = parseReleaseResponse(body)
            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses the GitHub release JSON response into an AppUpdate object.
     */
    private fun parseReleaseResponse(jsonString: String): AppUpdate {
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject

        val tagName = jsonObject["tag_name"]?.jsonPrimitive?.content
            ?: throw Exception("Missing tag_name in release")

        // Extract version from tag (e.g., "v1.2.0" -> "1.2.0")
        val versionName = tagName.removePrefix("v")

        // Parse version code from version name (e.g., "1.2.3" -> 1002003)
        val versionCode = parseVersionCode(versionName)

        val releaseNotes = jsonObject["body"]?.jsonPrimitive?.content ?: ""
        val browserUrl = jsonObject["html_url"]?.jsonPrimitive?.content ?: ""
        val publishedAt = jsonObject["published_at"]?.jsonPrimitive?.content ?: ""

        // Find the APK asset
        val assets = jsonObject["assets"]?.jsonArray
            ?: throw Exception("No assets found in release")

        val apkAsset = assets.map { it.jsonObject }
            .find { asset ->
                val name = asset["name"]?.jsonPrimitive?.content ?: ""
                name.endsWith(".apk")
            } ?: throw Exception("No APK asset found in release")

        val downloadUrl = apkAsset["browser_download_url"]?.jsonPrimitive?.content
            ?: throw Exception("Missing download URL for APK")
        val assetSize = apkAsset["size"]?.jsonPrimitive?.long ?: 0L

        return AppUpdate(
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            browserUrl = browserUrl,
            assetSize = assetSize,
            publishedAt = publishedAt
        )
    }

    /**
     * Converts a semantic version string to a numeric version code.
     * Example: "1.2.3" -> 1002003
     */
    private fun parseVersionCode(version: String): Int {
        val parts = version.split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
            2 -> parts[0] * 1_000_000 + parts[1] * 1_000
            1 -> parts[0] * 1_000_000
            else -> 0
        }
    }
}
