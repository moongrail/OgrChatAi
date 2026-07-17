package com.mindforge.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mindforge.app.data.local.entity.ChatEntity
import com.mindforge.app.data.local.entity.ChatMessageEntity
import com.mindforge.app.data.local.entity.DownloadedModelEntity

@Database(
    entities = [
        ChatEntity::class,
        ChatMessageEntity::class,
        DownloadedModelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao

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
