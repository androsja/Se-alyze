package com.sealyze.domain.repository

import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.model.TranslationResult
import kotlinx.coroutines.flow.Flow

interface SignRepository {
    
    /**
     * Processes a stream of sign frames and attempts to recognize signs.
     */
    fun recognizeSignStream(frames: Flow<SignFrame>): Flow<TranslationResult>

    /**
     * Gets the list of supported words/signs.
     */
    suspend fun getSupportedVocabulary(): List<String>
}
