package com.ogrchatai.app.domain.repository

import com.ogrchatai.app.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAllFolders(): Flow<List<Folder>>
    suspend fun getFolderById(folderId: Long): Folder?
    suspend fun getChildFolders(parentId: Long): List<Folder>
    suspend fun getRootFolders(): List<Folder>
    suspend fun createFolder(name: String, parentId: Long?, color: String?, icon: String?): Long
    suspend fun updateFolder(folder: Folder)
    suspend fun deleteFolder(folderId: Long): Result<Unit>
    suspend fun getChatCountInFolder(folderId: Long): Int
}
