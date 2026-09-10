package com.ogrchatai.app.data.repository

import com.ogrchatai.app.data.local.FolderDao
import com.ogrchatai.app.data.local.entity.FolderEntity
import com.ogrchatai.app.domain.model.Folder
import com.ogrchatai.app.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFolderById(folderId: Long): Folder? {
        return folderDao.getFolderById(folderId).first()?.toDomain()
    }

    override suspend fun getChildFolders(parentId: Long): List<Folder> {
        return folderDao.getChildFolders(parentId).first().map { it.toDomain() }
    }

    override suspend fun getRootFolders(): List<Folder> {
        return folderDao.getRootFolders().first().map { it.toDomain() }
    }

    override suspend fun createFolder(name: String, parentId: Long?, color: String?, icon: String?): Long {
        val folder = FolderEntity(
            name = name,
            parentId = parentId,
            color = color,
            icon = icon
        )
        return folderDao.insertFolder(folder)
    }

    override suspend fun updateFolder(folder: Folder) {
        folderDao.updateFolder(folder.toEntity())
    }

    override suspend fun deleteFolder(folderId: Long): Result<Unit> {
        return runCatching {
            folderDao.deleteFolderById(folderId)
        }
    }

    override suspend fun getChatCountInFolder(folderId: Long): Int {
        return folderDao.getChatCountInFolder(folderId)
    }

    private fun FolderEntity.toDomain(): Folder = Folder(
        id = id,
        name = name,
        parentId = parentId,
        color = color,
        icon = icon,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Folder.toEntity(): FolderEntity = FolderEntity(
        id = id,
        name = name,
        parentId = parentId,
        color = color,
        icon = icon,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
