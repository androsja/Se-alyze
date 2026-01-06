package com.sealyze.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.ImageProxy
import com.sealyze.data.source.MediaPipeDataSource
import com.sealyze.data.repository.SmartSentenceRepository
import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.usecase.RecognizeSignUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import javax.inject.Inject

data class CameraUiState(
    val currentTranslation: String = "",
    val confidence: Float = 0f,
    val landmarks: SignFrame? = null,
    val debugInfo: String = "",
    val smartSentenceBuffer: List<String> = emptyList(),
    val smartSentence: String = "",
    val autoGenProgress: Float = 0f, // 0.0f to 1.0f
    val isProcessing: Boolean = false,
    val sentenceDelay: Long = 5000L, // Default 5 seconds
    val lensFacing: Int = androidx.camera.core.CameraSelector.LENS_FACING_BACK,
    val isWideAngle: Boolean = false, // Zoom state
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 1.0f
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val recognizeSignUseCase: RecognizeSignUseCase,
    private val mediaPipeDataSource: MediaPipeDataSource,
    private val smartSentenceRepository: SmartSentenceRepository,
    private val ttsManager: com.sealyze.presentation.util.TextToSpeechManager,
    private val settingsRepository: com.sealyze.data.repository.SettingsRepository
) : ViewModel() {

    private var sentenceDebounceJob: kotlinx.coroutines.Job? = null
    private var lastDetectionTime: Long = 0L
    private var ignoredWord: String = ""
    private var currentDebounceWord: String? = null // FIXED: Defined at class level

    private val _uiState = MutableStateFlow(CameraUiState(
        sentenceDelay = settingsRepository.getSentenceDelay(),
        lensFacing = settingsRepository.getLensFacing()
    ))
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        // 1. Observe Landmarks from MediaPipe DataSource (UI Updates)
        viewModelScope.launch {
            mediaPipeDataSource.landmarkFlow.collect { signFrame ->
                _uiState.update { it.copy(landmarks = signFrame) }
            }
        }

        // 2. Observe Smart Sentence Repo
        viewModelScope.launch {
            smartSentenceRepository.wordBuffer.collect { buffer ->
                _uiState.update { it.copy(smartSentenceBuffer = buffer) }
                // If buffer becomes empty (e.g. after generation), reset progress
                if (buffer.isEmpty()) {
                    // CRITICAL FIX: Do NOT cancel the job here.
                    // If the buffer empties because Groq just finished, we still need the job to survive
                    // long enough to 'await()' the result and speak it.
                    // sentenceDebounceJob?.cancel() <-- REMOVED
                    
                    _uiState.update { it.copy(autoGenProgress = 0f) }
                }
            }
        }

        // Auto-clear Job
        var clearSentenceJob: kotlinx.coroutines.Job? = null

        viewModelScope.launch {
            smartSentenceRepository.generatedSentence.collect { sentence ->
                // Capture the current word to ignore it (since it's being spoken)
                val currentWord = _uiState.value.currentTranslation
                if (currentWord.isNotEmpty()) {
                    ignoredWord = currentWord
                }

                // CRITICAL: Clear 'currentTranslation' so the full Smart Sentence can be seen
                _uiState.update { it.copy(
                    smartSentence = sentence, 
                    currentTranslation = "", 
                    autoGenProgress = 0f 
                ) } 
                
                // Cancel previous clear timer
                clearSentenceJob?.cancel()
                
                // **LATENCY FIX**: We already spoke the raw words immediately when timer expired.
                // The LLM-generated sentence is just for displaying a better UI version.
                // So we DON'T speak it again to avoid double-speaking.
                if (sentence.isNotEmpty()) {
                    android.util.Log.i("SealyzeDetection", "📝 LLM Sentence Ready (UI only): '$sentence'")
                    // Removed: ttsManager.speak(sentence) - Already spoke raw words
                    
                    // CLEAR EVERYTHING AFTER A SHORT DELAY (e.g. 2s)
                    // This gives the user time to see the improved sentence, then resets completely
                    clearSentenceJob = launch {
                        kotlinx.coroutines.delay(2000) 
                        _uiState.update { it.copy(
                            smartSentence = "",
                            currentTranslation = "", // Ensure bubble is cleared too
                            autoGenProgress = 0f
                        ) }
                        // Reset ignoredWord to allow re-detecting the same sign immediately if desired
                        ignoredWord = ""
                    }
                }
            }
        }
        
        // 2. Observe Landmarks for Recognition Pipeline (Logic)
        viewModelScope.launch {
            recognizeSignUseCase(mediaPipeDataSource.landmarkFlow)
                .collect { result ->
                    // CHECK IGNORED WORD (Stop showing word if we just spoke it)
                    if (ignoredWord.isNotEmpty()) {
                        if (result.word == ignoredWord) {
                            // User still holding the sign? Ignore it to keep UI clear.
                             _uiState.update { it.copy(currentTranslation = "") }
                            return@collect
                        } else {
                            // User changed sign or dropped hand -> Reset ignore
                            ignoredWord = ""
                        }
                    }

                    val previousWord = _uiState.value.currentTranslation
                    
                    //android.util.Log.d("SealyzeDebug", "ViewModel: Received prediction: ${result.word} (${result.confidence})")
                    
                    // STICKY TRANSLATION LOGIC WITH TIMEOUT
                    val currentTime = System.currentTimeMillis()
                    
                    // Update validation time if we have a strong detection (Increased threshold)
                    if (!result.word.startsWith("_") && result.word.isNotEmpty() && result.confidence > 0.75f) {
                         lastDetectionTime = currentTime
                    }

                    // Decide what to show (Show >= 0.70)
                    val displayWord = if (!result.word.startsWith("_") && result.word.isNotEmpty() && result.confidence > 0.70f) {
                        result.word 
                    } else {
                        // If no new word, keep previous... UNLESS it's stale (1.5s timeout)
                        if (currentTime - lastDetectionTime > 1500) {
                            "" // Clear stale word
                        } else {
                            previousWord 
                        }
                    }
                    
                    _uiState.update { 
                        it.copy(
                            currentTranslation = displayWord, 
                            confidence = result.confidence,
                            debugInfo = result.probabilityDebug,
                            // Flash "Processing" if we have a non-empty result (even if low confidence)
                            isProcessing = result.confidence > 0.4f 
                        ) 
                    }
                    
                    // TTS Logic uses the NEW word detection, not just the display state
                    val newWordDetected = !result.word.startsWith("_") && result.word.isNotEmpty() && (result.word != previousWord)
                    
                    // HIGHER THRESHOLD FOR TTS/ADD: 0.75f (Reduces false positives)
                    if (newWordDetected && result.confidence > 0.750f) {
                        // LOG FOR USER: This is the definitive "Detection Event"
                        android.util.Log.i("SealyzeDetection", "🎯 DETECTADO: '${result.word}'")
                        
                        // 1. Add to Buffer (Wait for more words)
                        smartSentenceRepository.addWord(result.word)
                        
                        // NO IMMEDIATE TTS: User wants to build a sentence first.
                        // ttsManager.speak(result.word) <-- REMOVED
                        
                        // 2. Start/Reset 3s Timer (Debounce)
                        // Only cancel if the detected word is DIFFERENT from the one currently debouncing
                        if (currentDebounceWord == null || result.word != currentDebounceWord) {
                            sentenceDebounceJob?.cancel()
                            currentDebounceWord = null // Reset if cancelled due to different word
                        }
                        
                        // Cancel the "Hide" timer if it was running, to keep sticky text visible while buffering
                        clearSentenceJob?.cancel()

                        // START NEW TIMER ONLY IF NEEDED
                        // 1. If word changed, we already cancelled above -> currentDebounceWord is null -> Start.
                        // 2. If word is same, BUT job is dead/not active -> Start (Recovery).
                        // 3. If word is same AND job is running -> Do nothing (Let it run).
                        
                        val isTimerActive = sentenceDebounceJob?.isActive == true
                        
                        if (currentDebounceWord == null || !isTimerActive) {
                            // Set the word that started this timer
                            currentDebounceWord = result.word
                            
                            // Force cancel just in case it's a zombie job reference
                            sentenceDebounceJob?.cancel()
    
                            sentenceDebounceJob = viewModelScope.launch {
                                val totalDuration = _uiState.value.sentenceDelay
                                val halfDuration = totalDuration / 2
                                val step = 100L
                                
                                android.util.Log.d("SealyzeDebug", "⏳ Starting speculative timer: ${totalDuration}ms (LLM launch at ${halfDuration}ms)")
    
                                // 1. FIRST HALF WAIT (Progress 0% -> 50%)
                                for (elapsed in 0L..halfDuration step step) {
                                    if (!isActive) return@launch
                                    val progress = elapsed.toFloat() / totalDuration
                                    _uiState.update { it.copy(autoGenProgress = progress) }
                                    kotlinx.coroutines.delay(step)
                                }
                                
                                // 2. SPECULATIVE EXECUTION: Launch Groq in background
                                // This is a child coroutine; if parent (debounce job) is cancelled, this dies too.
                                android.util.Log.d("SealyzeDebug", "🚀 Speculative: Launching Groq request now...")
                                val groqDeferred = async { 
                                    smartSentenceRepository.generateSentence() 
                                }
                                
                                // 3. SECOND HALF WAIT (Progress 50% -> 100%)
                                android.util.Log.d("SealyzeDebug", "⏳ Speculative: Starting second half wait...")
                                for (elapsed in halfDuration..totalDuration step step) {
                                    if (!isActive) {
                                        android.util.Log.d("SealyzeDebug", "🛑 Speculative: Job CANCELLED during second half wait")
                                        return@launch
                                    }
                                    val progress = elapsed.toFloat() / totalDuration
                                    _uiState.update { it.copy(autoGenProgress = progress) }
                                    kotlinx.coroutines.delay(step)
                                }
                                
                                // 4. TIMER FINISHED
                                android.util.Log.d("SealyzeDebug", "🏁 Speculative: Timer completed. Finalizing...")
                                _uiState.update { it.copy(autoGenProgress = 1f) }
                                
                                // 5. GET RESULT
                                android.util.Log.d("SealyzeDebug", "⏳ Speculative: Awaiting Groq result...")
                                try {
                                    val finalSentence = groqDeferred.await()
                                    android.util.Log.d("SealyzeDebug", "✅ Speculative: Groq returned: '$finalSentence'")
                                    
                                    // 6. SPEAK
                                    if (finalSentence.isNotEmpty()) {
                                        android.util.Log.i("SealyzeDetection", "🗣️ Speaking Speculative Result: '$finalSentence'")
                                        ttsManager.speak(finalSentence)
                                    } else {
                                        android.util.Log.w("SealyzeDebug", "⚠️ Speculative warning: Groq result empty")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("SealyzeDebug", "❌ Speculative Error during await/speak: ${e.message}", e)
                                }
                                
                                // 7. Cleanup
                                currentDebounceWord = null
                                // Note: We don't set sentenceDebounceJob = null here because it's tricky 
                                // inside the coroutine itself, but currentDebounceWord = null is the key signal.
                                _uiState.update { it.copy(autoGenProgress = 0f) }
                            }
                        }
                    } else {
                       // android.util.Log.d("SealyzeDebug", "❌ TTS: NOT speaking '$displayWord' - Failed checks")
                    }
                }
        }
    }

    fun processImageProxy(imageProxy: ImageProxy) {
        mediaPipeDataSource.processVideoFrame(imageProxy)
    }

    fun onGenerateSentence() {
        viewModelScope.launch {
            smartSentenceRepository.generateSentence()
        }
    }

    fun onClearSentence() {
        smartSentenceRepository.clearBuffer()
    }
    
    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }

    fun setSentenceDelay(delayMs: Long) {
        settingsRepository.saveSentenceDelay(delayMs)
        _uiState.update { it.copy(sentenceDelay = delayMs) }
    }

    fun setLensFacing(lens: Int) {
        settingsRepository.saveLensFacing(lens)
        _uiState.update { it.copy(lensFacing = lens) }
        // Reset wide angle when switching lenses (optional safe default)
        _uiState.update { it.copy(isWideAngle = false) }
    }
    
    fun toggleWideAngle() {
        _uiState.update { it.copy(isWideAngle = !it.isWideAngle) }
    }

    fun updateZoomStats(min: Float, max: Float) {
        _uiState.update { it.copy(minZoom = min, maxZoom = max) }
    }
}
