package com.materialchat.di

import com.materialchat.data.remote.api.ChatApiClient
import com.materialchat.data.remote.api.ModelListApiClient
import com.materialchat.data.remote.sse.SseEventParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(STREAMING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
        sseEventParser: SseEventParser
    ): ChatApiClient {
        return ChatApiClient(
            okHttpClient = okHttpClient,
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
