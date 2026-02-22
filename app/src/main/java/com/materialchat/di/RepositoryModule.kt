package com.materialchat.di

import com.materialchat.data.repository.ArenaRepositoryImpl
import com.materialchat.data.repository.BookmarkRepositoryImpl
import com.materialchat.data.repository.ChatRepositoryImpl
import com.materialchat.data.repository.ConversationRepositoryImpl
import com.materialchat.data.repository.PersonaRepositoryImpl
import com.materialchat.data.repository.ProviderRepositoryImpl
import com.materialchat.domain.repository.ArenaRepository
import com.materialchat.domain.repository.BookmarkRepository
import com.materialchat.domain.repository.ChatRepository
import com.materialchat.domain.repository.ConversationRepository
import com.materialchat.domain.repository.PersonaRepository
import com.materialchat.domain.repository.ProviderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding repository implementations to their interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProviderRepository(
        impl: ProviderRepositoryImpl
    ): ProviderRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindArenaRepository(
        impl: ArenaRepositoryImpl
    ): ArenaRepository

    @Binds
    @Singleton
    abstract fun bindPersonaRepository(
        impl: PersonaRepositoryImpl
    ): PersonaRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        impl: BookmarkRepositoryImpl
    ): BookmarkRepository
}
