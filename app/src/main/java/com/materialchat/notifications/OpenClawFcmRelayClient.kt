package com.materialchat.notifications

import com.materialchat.BuildConfig
import com.materialchat.di.StandardClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenClawFcmRelayClient @Inject constructor(
    @StandardClient private val okHttpClient: OkHttpClient,
    private val json: Json
) {

    private val registerTokenPath = "/materialchat/push/register-token"
    private val unregisterTokenPath = "/materialchat/push/unregister-token"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun isPushEndpointAvailable(
        gatewayBaseUrl: String,
        gatewayToken: String?
    ): Boolean {
        if (gatewayBaseUrl.isBlank()) return false

        val body = "{}".toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder()
            .url("${gatewayBaseUrl.trimEnd('/')}$registerTokenPath")
            .post(body)

        if (!gatewayToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $gatewayToken")
        }

        val request = requestBuilder.build()
        okHttpClient.newCall(request).execute().use { response ->
            return response.isSuccessful || response.code == 400 || response.code == 405
        }
    }

    fun registerToken(
        gatewayBaseUrl: String,
        gatewayToken: String?,
        token: String,
        agentId: String
    ): Boolean {
        if (gatewayBaseUrl.isBlank()) return false
        val payload = RegisterTokenRequest(
            token = token,
            agentId = agentId,
            platform = "android",
            appVersion = BuildConfig.VERSION_NAME
        )
        return postJson(
            baseUrl = gatewayBaseUrl,
            path = registerTokenPath,
            gatewayToken = gatewayToken,
            bodyJson = json.encodeToString(payload)
        )
    }

    fun unregisterToken(
        gatewayBaseUrl: String,
        gatewayToken: String?,
        token: String
    ): Boolean {
        if (gatewayBaseUrl.isBlank()) return false
        val payload = UnregisterTokenRequest(token = token)
        return postJson(
            baseUrl = gatewayBaseUrl,
            path = unregisterTokenPath,
            gatewayToken = gatewayToken,
            bodyJson = json.encodeToString(payload)
        )
    }

    private fun postJson(
        baseUrl: String,
        path: String,
        gatewayToken: String?,
        bodyJson: String
    ): Boolean {
        val body = bodyJson.toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .post(body)

        if (!gatewayToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $gatewayToken")
        }

        val request = requestBuilder.build()
        okHttpClient.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    @Serializable
    private data class RegisterTokenRequest(
        val token: String,
        val agentId: String,
        val platform: String,
        val appVersion: String
    )

    @Serializable
    private data class UnregisterTokenRequest(
        val token: String
    )
}
