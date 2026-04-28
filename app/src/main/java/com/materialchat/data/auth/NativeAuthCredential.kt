package com.materialchat.data.auth

import com.materialchat.domain.model.ProviderType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Encrypted credential payload for native OAuth-backed providers.
 * Stored in the existing encrypted provider secret slot.
 */
@Serializable
data class NativeAuthCredential(
    val providerType: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val apiKey: String? = null,
    val accountId: String? = null,
    val projectId: String? = null,
    val email: String? = null,
    val expiryDate: Long = 0L,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val tokenUri: String? = null
) {
    val bearerToken: String?
        get() = apiKey?.takeIf { it.isNotBlank() }
            ?: accessToken?.takeIf { it.isNotBlank() }

    fun isExpired(bufferMs: Long = 5 * 60 * 1000L): Boolean {
        if (expiryDate <= 0L) return false
        return expiryDate <= System.currentTimeMillis() + bufferMs
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            isLenient = true
        }

        fun encode(credential: NativeAuthCredential): String {
            return json.encodeToString(serializer(), credential)
        }

        fun decodeOrNull(value: String?): NativeAuthCredential? {
            if (value.isNullOrBlank()) return null
            return try {
                json.decodeFromString(serializer(), value)
            } catch (_: Exception) {
                null
            }
        }

        fun emptyFor(type: ProviderType): NativeAuthCredential {
            return NativeAuthCredential(providerType = type.name)
        }
    }
}
