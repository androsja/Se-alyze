package com.sealyze.data.repository

import com.sealyze.data.source.GeminiDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartSentenceRepository @Inject constructor(
    private val geminiDataSource: GeminiDataSource
) {
    private val _wordBuffer = MutableStateFlow<List<String>>(emptyList())
    val wordBuffer: StateFlow<List<String>> = _wordBuffer.asStateFlow()

    private val _generatedSentence = MutableStateFlow<String>("")
    val generatedSentence: StateFlow<String> = _generatedSentence.asStateFlow()

    fun addWord(word: String) {
        // Strict filter: No technical classes or empty strings
        if (word.startsWith("_") || word.isEmpty()) return

        val current = _wordBuffer.value.toMutableList()
        // Avoid immediate duplicates (simple stability check)
        if (current.isEmpty() || current.last() != word) {
            current.add(word)
            _wordBuffer.value = current
        }
    }

    fun clearBuffer() {
        _wordBuffer.value = emptyList()
        _generatedSentence.value = ""
    }

    suspend fun generateSentence() {
        val words = _wordBuffer.value
        if (words.isNotEmpty()) {
            // FAST MODE: Bypass Gemini LLM for speed
            // val result = geminiDataSource.generateSentence(words)
            val result = words.joinToString(" ")
            _generatedSentence.value = result
            
            // Clear buffer after successful generation so next sentence is fresh
            _wordBuffer.value = emptyList()
        }
    }
}
