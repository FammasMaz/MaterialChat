package com.materialchat.di

import com.materialchat.data.repository.BookmarkRepositoryImpl
import com.materialchat.data.repository.ChatRepositoryImpl
import com.materialchat.data.repository.ConversationRepositoryImpl
import com.materialchat.data.repository.ProviderRepositoryImpl
import com.materialchat.domain.repository.BookmarkRepository
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.ProviderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding repository implementations to their interfaces.
 *
 * This module uses @Binds annotations to tell Hilt how to provide
 * repository interface implementations. The implementations themselves
 * use @Inject constructors for their dependencies.
 *
 * Bindings:
 * - ProviderRepository -> ProviderRepositoryImpl
 * - ConversationRepository -> ConversationRepositoryImpl
 * - ChatRepository -> ChatRepositoryImpl
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds [ProviderRepositoryImpl] to [ProviderRepository] interface.
     *
     * The implementation handles:
     * - Provider CRUD operations (Room database)
     * - API key encryption/decryption (Tink)
     * - Active provider management
     * - Default provider seeding
     */
    @Binds
    @Singleton
    abstract fun bindProviderRepository(
        impl: ProviderRepositoryImpl
    ): ProviderRepository

    /**
     * Binds [ConversationRepositoryImpl] to [ConversationRepository] interface.
     *
     * The implementation handles:
     * - Conversation CRUD operations (Room database)
     * - Message CRUD operations (Room database)
     * - Streaming message content updates
     * - Export to JSON and Markdown formats
     */
    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    /**
     * Binds [ChatRepositoryImpl] to [ChatRepository] interface.
     *
     * The implementation handles:
     * - Streaming chat completions from AI providers
     * - Model list fetching from providers
     * - Connection testing
     * - Stream cancellation
     */
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository

    /**
     * Binds [BookmarkRepositoryImpl] to [BookmarkRepository] interface.
     *
     * The implementation handles:
     * - Bookmark CRUD operations (Room database)
     * - Tag aggregation and filtering
     * - Category-based filtering
     * - Reactive observation of bookmark state
     */
    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        impl: BookmarkRepositoryImpl
    ): BookmarkRepository
}
