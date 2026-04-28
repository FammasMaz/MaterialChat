package com.materialchat.di

import android.content.Context
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.data.remote.api.ChatApiClient
import com.materialchat.data.remote.api.GitHubReleaseApiClient
import com.materialchat.data.remote.api.ModelListApiClient
import com.materialchat.data.remote.api.WebSearchApiClient
import com.materialchat.data.remote.sse.SseEventParser
import com.materialchat.data.repository.UpdateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module for network-related dependencies.
 *
 * Provides:
 * - OkHttpClient instances (standard and streaming)
 * - API clients (ChatApiClient, ModelListApiClient)
 * - SSE parser
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
    private const val STREAMING_READ_TIMEOUT_SECONDS = 120L

    /**
     * Provides the standard OkHttpClient for non-streaming requests.
     *
     * Configured with:
     * - 30 second connect, read, and write timeouts
     * - Retry on connection failure enabled
     */
    @Provides
    @Singleton
    @StandardClient
    fun provideStandardOkHttpClient(): OkHttpClient {
        return baseClientBuilder(READ_TIMEOUT_SECONDS)
            .build()
    }

    /**
     * Provides the OkHttpClient for streaming requests.
     *
     * Configured with:
     * - 30 second connect and write timeouts
     * - 120 second read timeout for long-running streams
     * - Retry on connection failure enabled
     */
    @Provides
    @Singleton
    @StreamingClient
    fun provideStreamingOkHttpClient(): OkHttpClient {
        return baseClientBuilder(STREAMING_READ_TIMEOUT_SECONDS)
            .build()
    }

    /**
     * Provides the SSE event parser for parsing streaming responses.
     */
    @Provides
    @Singleton
    fun provideSseEventParser(json: Json): SseEventParser {
        return SseEventParser(json)
    }

    /**
     * Provides the ChatApiClient for streaming chat completions.
     *
     * Uses the streaming OkHttpClient with longer read timeouts.
     */
    @Provides
    @Singleton
    fun provideChatApiClient(
        @StreamingClient okHttpClient: OkHttpClient,
        json: Json,
        sseEventParser: SseEventParser,
        @ApplicationContext context: Context
    ): ChatApiClient {
        return ChatApiClient(
            okHttpClient = okHttpClient,
            appContext = context.applicationContext,
            json = json,
            sseEventParser = sseEventParser
        )
    }

    /**
     * Provides the ModelListApiClient for fetching available models.
     *
     * Uses the standard OkHttpClient as model fetching doesn't require streaming.
     */
    @Provides
    @Singleton
    fun provideModelListApiClient(
        @StandardClient okHttpClient: OkHttpClient,
        json: Json
    ): ModelListApiClient {
        return ModelListApiClient(
            okHttpClient = okHttpClient,
            json = json
        )
    }

    /**
     * Provides the WebSearchApiClient for web search operations.
     *
     * Uses the standard OkHttpClient as search doesn't require streaming.
     */
    @Provides
    @Singleton
    fun provideWebSearchApiClient(
        @StandardClient okHttpClient: OkHttpClient,
        json: Json
    ): WebSearchApiClient {
        return WebSearchApiClient(
            okHttpClient = okHttpClient,
            json = json
        )
    }

    /**
     * Provides the GitHubReleaseApiClient for checking app updates.
     *
     * Uses the standard OkHttpClient for fetching release information.
     */
    @Provides
    @Singleton
    fun provideGitHubReleaseApiClient(
        @StandardClient okHttpClient: OkHttpClient,
        json: Json
    ): GitHubReleaseApiClient {
        return GitHubReleaseApiClient(
            okHttpClient = okHttpClient,
            json = json
        )
    }

    /**
     * Provides the UpdateManager for managing app updates.
     */
    @Provides
    @Singleton
    fun provideUpdateManager(
        githubApiClient: GitHubReleaseApiClient,
        appPreferences: AppPreferences,
        @StandardClient okHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): UpdateManager {
        return UpdateManager(
            githubApiClient = githubApiClient,
            appPreferences = appPreferences,
            okHttpClient = okHttpClient,
            context = context
        )
    }

    private fun baseClientBuilder(readTimeoutSeconds: Long): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .dns(systemDnsWithHttpsFallback())
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
    }

    private fun systemDnsWithHttpsFallback(): Dns {
        val dohClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        val cloudflareDns = DnsOverHttps.Builder()
            .client(dohClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001")
            )
            .build()

        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return try {
                    Dns.SYSTEM.lookup(hostname)
                } catch (systemError: UnknownHostException) {
                    try {
                        cloudflareDns.lookup(hostname)
                    } catch (fallbackError: UnknownHostException) {
                        systemError.addSuppressed(fallbackError)
                        throw systemError
                    }
                }
            }
        }
    }
}

/**
 * Qualifier annotation for the standard OkHttpClient.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardClient

/**
 * Qualifier annotation for the streaming OkHttpClient.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingClient
