package com.mindforge.app.domain.usecase.model

import com.mindforge.app.domain.model.DownloadedModel
import com.mindforge.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadedModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    operator fun invoke(): Flow<List<DownloadedModel>> {
        return modelRepository.getDownloadedModels()
    }
}
