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

    suspend fun generateSentence() {
        val words = _wordBuffer.value
        if (words.isNotEmpty()) {
            val prompt = words.joinToString(", ")
            
            // STRATEGY: Groq (Recommended) -> Gemini -> DeepSeek -> Raw
            
            // 1. Try Groq (Fastest)
            var result = groqDataSource.generateSentence(prompt)
            
            // 2. Fallback to Gemini
            if (result == null) {
                android.util.Log.w("SealyzeDetection", "⚠️ Repository: Groq failed (or no key), trying Gemini...")
                result = geminiDataSource.generateSentence(words)
            }
            
            // 3. Fallback to DeepSeek
            if (result == null) {
                android.util.Log.w("SealyzeDetection", "⚠️ Repository: Gemini failed, trying DeepSeek...")
                result = deepSeekDataSource.generateSentence(prompt)
            }
            
            // 4. Final Fallback: Raw Concatenation
            if (result == null) {
                android.util.Log.e("SealyzeDetection", "❌ Repository: All LLMs failed. Using raw string.")
                result = words.joinToString(" ")
            }
            
            _generatedSentence.value = result!!
            
            // Clear buffer after successful generation so next sentence is fresh
            _wordBuffer.value = emptyList()
        }
    }
}
