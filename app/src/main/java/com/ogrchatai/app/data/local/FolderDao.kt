package com.ogrchatai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ogrchatai.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY sort_order ASC, name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun getFolderById(folderId: Long): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE parent_id = :parentId ORDER BY sort_order ASC, name ASC")
    fun getChildFolders(parentId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parent_id IS NULL ORDER BY sort_order ASC, name ASC")
    fun getRootFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("UPDATE folders SET updated_at = :timestamp WHERE id = :folderId")
    suspend fun updateFolderTimestamp(folderId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM chats WHERE folder_id = :folderId")
    suspend fun getChatCountInFolder(folderId: Long): Int
}
