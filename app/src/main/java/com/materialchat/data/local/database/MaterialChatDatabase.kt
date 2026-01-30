package com.materialchat.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * - Messages (individual chat messages with optional image attachments)
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
    version = 6,
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
         * Migration from version 2 to 3: Add icon column to conversations table.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN icon TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration from version 3 to 4: Add image_attachments column to messages table.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN image_attachments TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration from version 4 to 5: Add parent_id column for conversation branching.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN parent_id TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_parent_id ON conversations(parent_id)")
            }
        }

        /**
         * Migration from version 5 to 6: Add OAuth and multi-provider support columns.
         *
         * New provider columns:
         * - auth_type: Authentication method (API_KEY, OAUTH, NONE)
         * - system_prompt: Provider-specific system prompt
         * - headers_json: JSON serialized custom headers
         * - options_json: JSON serialized provider options
         * - supports_streaming: Whether provider supports streaming
         * - supports_images: Whether provider supports image attachments
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN auth_type TEXT NOT NULL DEFAULT 'API_KEY'")
                db.execSQL("ALTER TABLE providers ADD COLUMN system_prompt TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE providers ADD COLUMN headers_json TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE providers ADD COLUMN options_json TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE providers ADD COLUMN supports_streaming INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE providers ADD COLUMN supports_images INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATIONS = arrayOf(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )

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
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }
}
