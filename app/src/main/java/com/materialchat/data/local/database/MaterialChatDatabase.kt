package com.materialchat.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.local.database.dao.ProviderDao
import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.data.local.database.entity.MessageEntity
import com.materialchat.data.local.database.entity.ProviderEntity

/**
 * Room database for MaterialChat application.
 *
 * This database stores all local data including:
 * - Provider configurations (API endpoints, settings)
 * - Conversations (chat sessions)
 * - Messages (individual chat messages)
 *
 * Note: API keys are NOT stored in this database. They are stored separately
 * in encrypted storage using Google Tink (see EncryptedPreferences).
 */
@Database(
    entities = [
        ProviderEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MaterialChatDatabase : RoomDatabase() {

    /**
     * DAO for provider operations.
     */
    abstract fun providerDao(): ProviderDao

    /**
     * DAO for conversation operations.
     */
    abstract fun conversationDao(): ConversationDao

    /**
     * DAO for message operations.
     */
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DATABASE_NAME = "materialchat.db"

        @Volatile
        private var INSTANCE: MaterialChatDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * Uses double-checked locking to ensure thread-safe lazy initialization.
         */
        fun getInstance(context: Context): MaterialChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MaterialChatDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MaterialChatDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
