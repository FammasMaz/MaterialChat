package com.materialchat.di

import com.materialchat.data.auth.AntigravityOAuth
import com.materialchat.data.auth.OAuthManager
import com.materialchat.data.auth.PkceGenerator
import com.materialchat.data.local.preferences.EncryptedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for OAuth and authentication-related dependencies.
 *
 * This module provides bindings for:
 * - OAuthManager: Central OAuth orchestration
 * - AntigravityOAuth: Antigravity-specific OAuth helpers
 * - OkHttpClient for auth requests (uses standard client)
 *
 * These dependencies are needed for the multi-provider OAuth system
 * and are used by ProviderRepositoryImpl to bridge OAuth operations
 * to the UI layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /**
     * Provides the OAuthManager for handling OAuth flows.
     *
     * Uses the standard OkHttpClient since auth requests are short-lived
     * and don't need streaming timeouts.
     */
    @Provides
    @Singleton
    fun provideOAuthManager(
        encryptedPreferences: EncryptedPreferences,
        pkceGenerator: PkceGenerator,
        @StandardClient okHttpClient: OkHttpClient,
        json: Json
    ): OAuthManager {
        return OAuthManager(
            encryptedPreferences = encryptedPreferences,
            pkceGenerator = pkceGenerator,
            httpClient = okHttpClient,
            json = json
        )
    }

    /**
     * Provides the AntigravityOAuth helper for Antigravity-specific operations.
     *
     * Handles user info fetching, project ID resolution, and building
     * authenticated request headers for Antigravity API calls.
     */
    @Provides
    @Singleton
    fun provideAntigravityOAuth(
        @StandardClient okHttpClient: OkHttpClient,
        json: Json
    ): AntigravityOAuth {
        return AntigravityOAuth(
            httpClient = okHttpClient,
            json = json
        )
    }
}
