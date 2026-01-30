package com.materialchat.di

import android.content.Context
import androidx.room.Room
import com.materialchat.data.local.database.MaterialChatDatabase
import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.local.database.dao.ProviderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database and DAOs.
 *
 * Provides:
 * - MaterialChatDatabase instance (singleton)
 * - ProviderDao
 * - ConversationDao
 * - MessageDao
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "materialchat.db"

    /**
     * Provides the Room database instance.
     *
     * The database is configured with:
     * - Schema migrations to preserve user data
     * - Destructive fallback on downgrade only
     * - Singleton pattern for thread safety
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MaterialChatDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            MaterialChatDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*MaterialChatDatabase.MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    /**
     * Provides the ProviderDao for provider CRUD operations.
     */
    @Provides
    @Singleton
    fun provideProviderDao(database: MaterialChatDatabase): ProviderDao {
        return database.providerDao()
    }

    /**
     * Provides the ConversationDao for conversation CRUD operations.
     */
    @Provides
    @Singleton
    fun provideConversationDao(database: MaterialChatDatabase): ConversationDao {
        return database.conversationDao()
    }

    /**
     * Provides the MessageDao for message CRUD operations.
     */
    @Provides
    @Singleton
    fun provideMessageDao(database: MaterialChatDatabase): MessageDao {
        return database.messageDao()
    }
}
