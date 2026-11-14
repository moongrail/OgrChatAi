package com.ogrchatai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ogrchatai.app.data.local.entity.ChatEntity
import com.ogrchatai.app.data.local.entity.ChatMessageEntity
import com.ogrchatai.app.data.local.entity.DownloadedModelEntity
import com.ogrchatai.app.data.local.entity.FolderEntity

@Database(
    entities = [
        ChatEntity::class,
        ChatMessageEntity::class,
        DownloadedModelEntity::class,
        FolderEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val DATABASE_NAME = "chat_llm_database"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_chat_messages_chat_id`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_chat_id_timestamp` " +
                        "ON `chat_messages` (`chat_id`, `timestamp`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `chats` ADD COLUMN `memory_cleared_at` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
