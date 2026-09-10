package com.ogrchatai.app.data.remote

import com.ogrchatai.app.data.remote.dto.HuggingFaceModel
import com.ogrchatai.app.data.remote.dto.HuggingFaceModelInfoResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface HuggingFaceApi {

    @GET("api/models")
    suspend fun searchModels(
        @Query("search") query: String? = null,
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
        @Query("limit") limit: Int = 50,
        @Query("filter") filter: String? = null
    ): List<HuggingFaceModel>

    @GET("api/models/{repoId}")
    suspend fun getModelInfo(
        @Path("repoId", encoded = true) repoId: String
    ): HuggingFaceModelInfoResponse

    @Streaming
    @GET("{repoId}/resolve/main/{fileName}")
    suspend fun downloadModelFile(
        @Path("repoId", encoded = true) repoId: String,
        @Path("fileName", encoded = true) fileName: String
    ): ResponseBody

    @GET("api/models")
    suspend fun getModelsByTag(
        @Query("filter") tag: String,
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
        @Query("limit") limit: Int = 50
    ): List<HuggingFaceModel>
}
