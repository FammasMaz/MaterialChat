package com.materialchat.ui.screens.chat.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object WebPreviewImageResolver {
    private const val NO_IMAGE = "__NO_IMAGE__"
    private const val MAX_HTML_CHARS = 200_000

    private val cache = ConcurrentHashMap<String, String>()
    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolve(pageUrl: String): String? {
        cache[pageUrl]?.let { cached ->
            return cached.takeIf { it != NO_IMAGE }
        }

        val resolved = withContext(Dispatchers.IO) {
            runCatching { fetchPreviewImage(pageUrl) }.getOrNull()
        }
        cache[pageUrl] = resolved ?: NO_IMAGE
        return resolved
    }

    private fun fetchPreviewImage(pageUrl: String): String? {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", "Mozilla/5.0 MaterialChat link preview")
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string()?.take(MAX_HTML_CHARS).orEmpty()
            return findMetaImage(html, pageUrl) ?: findLinkImage(html, pageUrl)
        }
    }

    private fun findMetaImage(html: String, pageUrl: String): String? {
        for (tag in META_TAG_REGEX.findAll(html).map { it.value }) {
            val attrs = parseAttributes(tag)
            val name = attrs["property"] ?: attrs["name"] ?: continue
            if (name.lowercase() in META_IMAGE_KEYS) {
                attrs["content"]?.let { raw ->
                    resolveImageUrl(pageUrl, raw)?.let { return it }
                }
            }
        }
        return null
    }

    private fun findLinkImage(html: String, pageUrl: String): String? {
        for (tag in LINK_TAG_REGEX.findAll(html).map { it.value }) {
            val attrs = parseAttributes(tag)
            val rel = attrs["rel"]?.lowercase().orEmpty()
            if (rel.contains("image_src") || rel.contains("apple-touch-icon")) {
                attrs["href"]?.let { raw ->
                    resolveImageUrl(pageUrl, raw)?.let { return it }
                }
            }
        }
        return null
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        return ATTR_REGEX.findAll(tag).associate { match ->
            match.groupValues[1].lowercase() to decodeHtml(match.groupValues[2].trim())
        }
    }

    private fun resolveImageUrl(pageUrl: String, rawUrl: String): String? {
        val resolved = runCatching { URI(pageUrl).resolve(rawUrl.trim()).toString() }.getOrNull()
        return resolved?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private val META_IMAGE_KEYS = setOf(
        "og:image",
        "og:image:url",
        "og:image:secure_url",
        "twitter:image",
        "twitter:image:src",
        "thumbnail"
    )
    private val META_TAG_REGEX = Regex("<meta\\s+[^>]*>", RegexOption.IGNORE_CASE)
    private val LINK_TAG_REGEX = Regex("<link\\s+[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR_REGEX = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*['\"]([^'\"]+)['\"]")
}
