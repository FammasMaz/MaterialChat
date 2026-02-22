package com.materialchat.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.materialchat.data.local.database.dao.ArenaDao
import com.materialchat.data.local.database.dao.BookmarkDao
import com.materialchat.data.local.database.dao.ConversationDao
import com.materialchat.data.local.database.dao.MessageDao
import com.materialchat.data.local.database.dao.PersonaDao
import com.materialchat.data.local.database.dao.ProviderDao
import com.materialchat.data.local.database.entity.ArenaBattleEntity
import com.materialchat.data.local.database.entity.BookmarkEntity
import com.materialchat.data.local.database.entity.ConversationEntity
import com.materialchat.data.local.database.entity.MessageEntity
import com.materialchat.data.local.database.entity.ModelRatingEntity
import com.materialchat.data.local.database.entity.PersonaEntity
import com.materialchat.data.local.database.entity.ProviderEntity

/**
 * Room database for MaterialChat application.
 *
 * This database stores all local data including:
 * - Provider configurations (API endpoints, settings)
 * - Conversations (chat sessions)
 * - Messages (individual chat messages with optional image attachments)
 * - Arena battles and model ELO ratings
 * - Custom AI personas
 * - Message bookmarks for the knowledge base
 *
 * Note: API keys are NOT stored in this database. They are stored separately
 * in encrypted storage using Google Tink (see EncryptedPreferences).
 */
@Database(
    entities = [
        ProviderEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ArenaBattleEntity::class,
        ModelRatingEntity::class,
        PersonaEntity::class,
        BookmarkEntity::class
    ],
    version = 8,
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

    /**
     * DAO for arena battle and model rating operations.
     */
    abstract fun arenaDao(): ArenaDao

    /**
     * DAO for persona operations.
     */
    abstract fun personaDao(): PersonaDao

    /**
     * DAO for bookmark operations.
     */
    abstract fun bookmarkDao(): BookmarkDao

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
         * Migration from version 5 to 6: Add duration columns to messages table.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN thinking_duration_ms INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN total_duration_ms INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration from version 6 to 7: Add model_name to messages and branch_source_message_id to conversations.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN model_name TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE conversations ADD COLUMN branch_source_message_id TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration from version 7 to 8: Add arena battles, model ratings, personas, bookmarks,
         * persona_id on conversations, and fusion_metadata on messages.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Arena battles table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS arena_battles (
                        id TEXT NOT NULL PRIMARY KEY,
                        prompt TEXT NOT NULL,
                        left_model_name TEXT NOT NULL,
                        left_provider_id TEXT,
                        left_response TEXT NOT NULL,
                        right_model_name TEXT NOT NULL,
                        right_provider_id TEXT,
                        right_response TEXT NOT NULL,
                        winner TEXT,
                        left_thinking_content TEXT,
                        right_thinking_content TEXT,
                        left_duration_ms INTEGER,
                        right_duration_ms INTEGER,
                        created_at INTEGER NOT NULL
                    )
                """)

                // Model ratings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS model_ratings (
                        model_name TEXT NOT NULL PRIMARY KEY,
                        elo_rating REAL NOT NULL DEFAULT 1500.0,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0,
                        ties INTEGER NOT NULL DEFAULT 0,
                        total_battles INTEGER NOT NULL DEFAULT 0,
                        last_battle_at INTEGER
                    )
                """)

                // Personas table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personas (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        emoji TEXT NOT NULL DEFAULT '🤖',
                        description TEXT NOT NULL DEFAULT '',
                        system_prompt TEXT NOT NULL,
                        expertise_tags TEXT,
                        tone TEXT NOT NULL DEFAULT 'balanced',
                        conversation_starters TEXT,
                        color_seed INTEGER,
                        is_builtin INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """)

                // Add persona_id to conversations
                db.execSQL("ALTER TABLE conversations ADD COLUMN persona_id TEXT DEFAULT NULL")

                // Bookmarks table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        message_id TEXT NOT NULL,
                        conversation_id TEXT NOT NULL,
                        tags TEXT,
                        note TEXT,
                        category TEXT NOT NULL DEFAULT 'general',
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
                        FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_message_id ON bookmarks(message_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_conversation_id ON bookmarks(conversation_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_category ON bookmarks(category)")

                // Add fusion_metadata to messages
                db.execSQL("ALTER TABLE messages ADD COLUMN fusion_metadata TEXT DEFAULT NULL")
            }
        }

        internal val MIGRATIONS = arrayOf(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
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
