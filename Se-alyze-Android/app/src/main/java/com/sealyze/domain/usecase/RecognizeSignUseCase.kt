package com.sealyze.domain.usecase

import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.model.TranslationResult
import com.sealyze.domain.repository.SignRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RecognizeSignUseCase @Inject constructor(
    private val signRepository: SignRepository
) {
    operator fun invoke(frames: Flow<SignFrame>): Flow<TranslationResult> {
        return signRepository.recognizeSignStream(frames)
    }
}
