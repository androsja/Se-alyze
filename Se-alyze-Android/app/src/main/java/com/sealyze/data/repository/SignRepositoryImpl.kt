package com.sealyze.data.repository

import com.sealyze.data.source.MediaPipeDataSource
import com.sealyze.data.source.TfliteDataSource
import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.model.TranslationResult
import com.sealyze.domain.repository.SignRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class SignRepositoryImpl @Inject constructor(
    private val mediaPipeDataSource: MediaPipeDataSource,
    private val tfliteDataSource: TfliteDataSource
) : SignRepository {

    override fun recognizeSignStream(frames: Flow<SignFrame>): Flow<TranslationResult> {
        return frames.mapNotNull { frame ->
            tfliteDataSource.addToBufferAndPredict(frame)
        }
    }

    override suspend fun getSupportedVocabulary(): List<String> {
        return listOf("Hola", "Gracias", "Ayuda") // Mock, later read from json
    }
}
