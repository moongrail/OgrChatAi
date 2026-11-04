package com.ogrchatai.app.domain.usecase.model

import com.ogrchatai.app.domain.model.HuggingFaceModel
import com.ogrchatai.app.domain.model.ModelFilter
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.domain.model.SearchResult
import com.ogrchatai.app.domain.model.Sibling
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.util.ModelCompatibility
import javax.inject.Inject

class SearchModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(
        query: String,
        page: Int = 1,
        filters: ModelFilter = ModelFilter(),
        deviceRamMb: Long = 0L
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

            // Chat models only. Audio/embedding/vision repositories can contain a GGUF file
            // but are not useful in this text-only chat app. A missing pipeline tag is kept
            // because many community GGUF repositories do not declare one.
            models = models.filter { model ->
                model.pipelineTag == null || model.pipelineTag == "text-generation"
            }

            if (deviceRamMb > 0) {
                models = models.filter { model ->
                    ModelCompatibility.isLikelyCompatible(
                        modelName = "${model.id} ${model.name} ${model.tags.joinToString(" ")}",
                        deviceRamMb = deviceRamMb
                    )
                }
            }

            val quantization = filters.quantization
            if (quantization != null) {
                models = models.filter { model ->
                    model.quantizationTags.any { tag ->
                        tag.contains(quantization, ignoreCase = true)
                    } || model.siblings.any { sib ->
                        sib.rfilename.contains(quantization, ignoreCase = true)
                    }
                }
            }

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
        }
    }
}
