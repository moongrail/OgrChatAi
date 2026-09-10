package com.ogrchatai.app.domain.usecase.model

import com.ogrchatai.app.domain.model.HuggingFaceModel
import com.ogrchatai.app.domain.model.ModelFilter
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.domain.model.SearchResult
import com.ogrchatai.app.domain.model.Sibling
import com.ogrchatai.app.domain.repository.ModelRepository
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

            if (filters.quantization != null) {
                models = models.filter { model ->
                    model.quantizationTags.any { tag ->
                        tag.contains(filters.quantization!!, ignoreCase = true)
                    } || model.siblings.any { sib ->
                        sib.rfilename.contains(filters.quantization!!, ignoreCase = true)
                    }
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
        }
    }
}
