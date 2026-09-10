package com.ogrchatai.app.domain.usecase.model

import com.ogrchatai.app.domain.repository.ModelRepository
import javax.inject.Inject

class DeleteModelUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(modelId: String): Result<Unit> {
        require(modelId.isNotBlank()) { "Model ID cannot be blank" }
        return modelRepository.deleteModel(modelId)
    }
}
