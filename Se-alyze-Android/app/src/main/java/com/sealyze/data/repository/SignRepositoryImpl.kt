package com.sealyze.data.repository

import com.sealyze.data.source.MediaPipeDataSource
import com.sealyze.data.source.TfliteDataSource
import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.model.TranslationResult
import com.sealyze.domain.repository.SignRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

class SignRepositoryImpl @Inject constructor(
    private val mediaPipeDataSource: MediaPipeDataSource,
    private val tfliteDataSource: TfliteDataSource
) : SignRepository {

    override fun recognizeSignStream(frames: Flow<SignFrame>): Flow<TranslationResult> {
        var lastWord = ""
        var stabilityCount = 0
        val STABILITY_THRESHOLD = 3

        return frames.mapNotNull { frame ->
            // android.util.Log.d("SealyzeDebug", "Repository: Processing frame...")
            tfliteDataSource.addToBufferAndPredict(frame)
        }.transform { result ->
            if (result.word == lastWord) {
                stabilityCount++
            } else {
                lastWord = result.word
                stabilityCount = 1
            }

            if (stabilityCount >= STABILITY_THRESHOLD) {
                emit(result)
            } else {
                android.util.Log.d("SealyzeDebug", "Repository: Filtering transient '${result.word}' ($stabilityCount/$STABILITY_THRESHOLD)")
            }
        }
    }

    override suspend fun getSupportedVocabulary(): List<String> {
        return listOf("Hola", "Gracias", "Ayuda") // Mock, later read from json
    }
}
