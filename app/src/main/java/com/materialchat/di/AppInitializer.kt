package com.materialchat.di

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles app initialization tasks on first launch.
 *
 * This class is responsible for:
 * - Seeding default providers (OpenAI and Ollama templates)
 * - Setting up initial preferences
 * - Marking first launch as complete
 */
@Singleton
class AppInitializer @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val appPreferences: AppPreferences
) {
    /**
     * Initializes the app on first launch.
     *
     * This should be called from the main activity or application startup.
     * The method is idempotent - it will only perform initialization once.
     */
    suspend fun initializeIfNeeded() {
        val isFirstLaunchComplete = appPreferences.firstLaunchComplete.first()

        if (!isFirstLaunchComplete) {
            performFirstLaunchSetup()
        }
    }

    /**
     * Performs first launch setup tasks.
     */
    private suspend fun performFirstLaunchSetup() {
        // Seed default providers (OpenAI and Ollama templates)
        providerRepository.seedDefaultProviders()

        // Set default system prompt (already set as default value in AppPreferences,
        // but we explicitly set it here to ensure it's persisted)
        appPreferences.setSystemPrompt(AppPreferences.DEFAULT_SYSTEM_PROMPT)

        // Mark first launch as complete
        appPreferences.setFirstLaunchComplete()
    }

    /**
     * Resets the app to first-launch state.
     * This is useful for testing or allowing users to reset the app.
     */
    suspend fun resetToFirstLaunch() {
        appPreferences.clearAll()
    }
}
