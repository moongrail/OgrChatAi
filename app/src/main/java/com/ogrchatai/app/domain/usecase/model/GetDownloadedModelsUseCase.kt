package com.ogrchatai.app.domain.usecase.model

import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadedModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(): Flow<List<DownloadedModel>> {
        return modelRepository.getDownloadedModels()
    }
}
