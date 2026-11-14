package com.materialchat.assistant.di

import android.content.Context
import com.materialchat.assistant.service.AssistantBindingDetector
import com.materialchat.assistant.voice.AssistantSpeechRuntime
import com.materialchat.assistant.voice.GeminiTtsBackend
import com.materialchat.assistant.voice.AudioTrackPcmPlayer
import com.materialchat.assistant.voice.AudioTrackSherpaPlayer
import com.materialchat.assistant.voice.OkHttpSherpaArtifactFetcher
import com.materialchat.assistant.voice.OnnxSherpaSynthesizerFactory
import com.materialchat.assistant.voice.SherpaModelStore
import com.materialchat.assistant.voice.SherpaTtsBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import com.materialchat.data.local.preferences.EncryptedPreferences
import com.materialchat.assistant.voice.SpeechRecognitionManager
import com.materialchat.assistant.voice.TextToSpeechManager
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import com.materialchat.domain.usecase.CreateConversationUseCase
import com.materialchat.domain.usecase.GenerateConversationTitleUseCase
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
    fun provideAssistantSpeechRuntime(
        android: TextToSpeechManager,
        credentials: EncryptedPreferences,
        sherpa: SherpaTtsBackend
    ): AssistantSpeechRuntime =
        AssistantSpeechRuntime(android, GeminiTtsBackend(player = AudioTrackPcmPlayer()), credentials, sherpa)

    /** Neural voice models live in filesDir/tts/<model>/ and are never bundled in the APK. */
    @Provides
    @Singleton
    fun provideSherpaModelStore(@ApplicationContext context: Context): SherpaModelStore = SherpaModelStore(
        File(context.filesDir, "tts"),
        OkHttpSherpaArtifactFetcher(),
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    @Provides
    @Singleton
    fun provideSherpaTtsBackend(store: SherpaModelStore): SherpaTtsBackend =
        SherpaTtsBackend(store, OnnxSherpaSynthesizerFactory(), AudioTrackSherpaPlayer())

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
    fun assistantSpeechRuntime(): AssistantSpeechRuntime
    fun speechRecognitionManager(): SpeechRecognitionManager
    fun textToSpeechManager(): TextToSpeechManager
    fun chatRepository(): ChatRepository
    fun providerRepository(): ProviderRepository
    fun appPreferences(): AppPreferences
    fun createConversationUseCase(): CreateConversationUseCase
    fun conversationRepository(): ConversationRepository
    fun generateConversationTitleUseCase(): GenerateConversationTitleUseCase
    fun assistantBindingDetector(): AssistantBindingDetector
}
