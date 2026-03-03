package com.materialchat.di

import android.content.Context
import android.net.ConnectivityManager
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.data.local.preferences.OpenClawPreferences
import com.materialchat.data.remote.openclaw.OpenClawGatewayClient
import com.materialchat.data.remote.sse.SseEventParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for OpenClaw Gateway dependencies.
 *
 * Provides:
 * - OpenClawPreferences (DataStore-based config storage)
 * - OpenClawGatewayClient (WebSocket + HTTP networking)
 */
@Module
@InstallIn(SingletonComponent::class)
object OpenClawModule {

    @Provides
    @Singleton
    fun provideOpenClawPreferences(
        @ApplicationContext context: Context
    ): OpenClawPreferences {
        return OpenClawPreferences(context)
    }

    @Provides
    @Singleton
    fun provideOpenClawGatewayClient(
        @StreamingClient okHttpClient: OkHttpClient,
        json: Json,
        sseEventParser: SseEventParser,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): OpenClawGatewayClient {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

        return OpenClawGatewayClient(
            okHttpClient = okHttpClient,
            json = json,
            sseEventParser = sseEventParser,
            connectivityManager = connectivityManager,
            scope = scope
        )
    }
}
