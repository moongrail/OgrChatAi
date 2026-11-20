package com.ogrchatai.app.domain.usecase.model

import android.util.Log
import com.ogrchatai.app.domain.model.ModelFilter
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.domain.model.SearchResult
import com.ogrchatai.app.domain.repository.ModelRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SearchModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(
        query: String,
        page: Int = 1,
        filters: ModelFilter = ModelFilter()
    ): Result<SearchResult> {
        if (query.isBlank()) {
            return Result.success(SearchResult(models = emptyList(), hasMore = false))
        }
        return runCatching {
            val hfModels = modelRepository.searchModels(query)
            var models = hfModels.map { hf ->
                ModelInfo(
                    id = hf.id,
                    modelId = hf.modelId,
                    name = hf.name,
                    author = hf.author,
                    downloads = hf.downloads,
                    likes = hf.likes,
                    tags = hf.tags,
                    pipelineTag = hf.pipelineTag,
                    lastModified = hf.lastModified,
                    siblings = hf.siblings
                )
            }
            // HuggingFace's search endpoint already applies the `gguf` filter but usually omits
            // the file list from its response. Do not discard those results merely because
            // siblings are absent; file names are resolved later from the model info endpoint.
            //
            // Do not filter by pipeline tag or estimated RAM here. The search endpoint is the
            // source of truth for what exists; hiding results based on a name-derived parameter
            // estimate made valid repositories invisible. The card can still show an advisory
            // memory warning, but search, download and load are not blocked by it.

            val architecture = filters.architecture
            if (architecture != null) {
                models = models.filter { model ->
                    model.tags.any { tag ->
                        tag.contains(architecture, ignoreCase = true)
                    } || model.name.contains(architecture, ignoreCase = true)
                }
            }

            models = when (filters.sortBy) {
                com.ogrchatai.app.domain.model.SortBy.DOWNLOADS -> models.sortedByDescending { it.downloads }
                com.ogrchatai.app.domain.model.SortBy.LIKES -> models.sortedByDescending { it.likes }
                com.ogrchatai.app.domain.model.SortBy.RECENT -> models.sortedByDescending { it.lastModified }
            }

            val pageSize = 20
            val startIndex = (page - 1) * pageSize
            val pagedModels = if (startIndex < models.size) {
                models.drop(startIndex).take(pageSize)
            } else {
                emptyList()
            }

            SearchResult(
                models = pagedModels,
                hasMore = startIndex + pageSize < models.size
            )
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.e(TAG, "Model search failed for \"$query\"", throwable)
        }
    }

    private companion object {
        const val TAG = "SearchModelsUseCase"
    }
}
