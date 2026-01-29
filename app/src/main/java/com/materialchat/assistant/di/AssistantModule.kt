package com.materialchat.assistant.di

import android.content.Context
import com.materialchat.assistant.voice.SpeechRecognitionManager
import com.materialchat.assistant.voice.TextToSpeechManager
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Assistant-related dependencies.
 *
 * Provides:
 * - SpeechRecognitionManager for voice input
 * - TextToSpeechManager for voice output
 */
@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideSpeechRecognitionManager(
        @ApplicationContext context: Context
    ): SpeechRecognitionManager = SpeechRecognitionManager(context)

    @Provides
    @Singleton
    fun provideTextToSpeechManager(
        @ApplicationContext context: Context
    ): TextToSpeechManager = TextToSpeechManager(context)
}

/**
 * Hilt EntryPoint for accessing dependencies in VoiceInteractionSession.
 *
 * Since VoiceInteractionSession is not an Activity or Fragment,
 * we need to use EntryPoints to access Hilt-managed dependencies.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistantEntryPoint {
    fun speechRecognitionManager(): SpeechRecognitionManager
    fun textToSpeechManager(): TextToSpeechManager
    fun chatRepository(): ChatRepository
    fun providerRepository(): ProviderRepository
    fun appPreferences(): AppPreferences
    fun createConversationUseCase(): CreateConversationUseCase
    fun conversationRepository(): ConversationRepository
}
