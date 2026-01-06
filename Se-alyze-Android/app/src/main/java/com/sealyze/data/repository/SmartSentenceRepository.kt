package com.sealyze.data.repository

import com.sealyze.data.source.GeminiDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartSentenceRepository @Inject constructor(
    private val groqDataSource: com.sealyze.data.source.GroqDataSource,
    private val geminiDataSource: GeminiDataSource,
    private val deepSeekDataSource: com.sealyze.data.source.DeepSeekDataSource
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

    suspend fun generateSentence(): String {
        val words = _wordBuffer.value
        if (words.isNotEmpty()) {
            val prompt = words.joinToString(", ")
            
            // STRATEGY: Groq ONLY (Fastest & Free)
            
            // 1. Try Groq
            var result = groqDataSource.generateSentence(prompt)
            
            // 2. Final Fallback: Raw Concatenation if Groq fails
            if (result == null) {
                android.util.Log.e("SealyzeDetection", "❌ Repository: Groq failed. Using raw string.")
                result = words.joinToString(" ")
            }
            
            _generatedSentence.value = result!!
            
            // Clear buffer after successful generation so next sentence is fresh
            _wordBuffer.value = emptyList()
            
            return result
        }
        return ""
    }
}
