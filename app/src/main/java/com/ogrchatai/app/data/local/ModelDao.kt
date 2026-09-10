package com.ogrchatai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ogrchatai.app.data.local.entity.DownloadedModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM downloaded_models ORDER BY downloaded_at DESC")
    fun getAllDownloadedModels(): Flow<List<DownloadedModelEntity>>

    @Query("SELECT * FROM downloaded_models WHERE id = :modelId")
    fun getModelById(modelId: Long): Flow<DownloadedModelEntity?>

    @Query("SELECT * FROM downloaded_models WHERE id = :modelId")
    suspend fun getModelByIdOnce(modelId: Long): DownloadedModelEntity?

    @Query("SELECT * FROM downloaded_models WHERE hf_model_id = :hfModelId LIMIT 1")
    fun getModelByHfId(hfModelId: String): Flow<DownloadedModelEntity?>

    @Query("SELECT * FROM downloaded_models WHERE hf_model_id = :hfModelId LIMIT 1")
    suspend fun getModelByHfIdOnce(hfModelId: String): DownloadedModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DownloadedModelEntity): Long

    @Update
    suspend fun updateModel(model: DownloadedModelEntity)

    @Query("DELETE FROM downloaded_models WHERE id = :modelId")
    suspend fun deleteModelById(modelId: Long)

    @Query("DELETE FROM downloaded_models")
    suspend fun deleteAllModels()

    @Query("SELECT * FROM downloaded_models WHERE is_favorite = 1 ORDER BY downloaded_at DESC")
    fun getFavoriteModels(): Flow<List<DownloadedModelEntity>>

    @Query("UPDATE downloaded_models SET is_favorite = :isFavorite WHERE id = :modelId")
    suspend fun setFavorite(modelId: Long, isFavorite: Boolean)

    @Query("SELECT * FROM downloaded_models WHERE name LIKE '%' || :query || '%' OR hf_model_id LIKE '%' || :query || '%'")
    fun searchModels(query: String): Flow<List<DownloadedModelEntity>>

    @Query("SELECT SUM(size_bytes) FROM downloaded_models")
    suspend fun getTotalDownloadedSize(): Long?

    @Query("SELECT COUNT(*) FROM downloaded_models")
    suspend fun getModelCount(): Int
}
