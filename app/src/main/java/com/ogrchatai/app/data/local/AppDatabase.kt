package com.ogrchatai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val DATABASE_NAME = "chatllm_database"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
