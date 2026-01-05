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
    val lensFacing: Int = androidx.camera.core.CameraSelector.LENS_FACING_BACK
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
                    sentenceDebounceJob?.cancel()
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
                
                // Optional: Speak the full sentence
                if (sentence.isNotEmpty()) {
                    android.util.Log.i("SealyzeDetection", "🗣️ HABLANDO: '$sentence'")
                    ttsManager.speak(sentence)
                    
                    // CLEAR EVERYTHING AFTER A SHORT DELAY (e.g. 2s)
                    // This gives the user time to hear/see the sentence, then resets completely
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
                    
                    android.util.Log.d("SealyzeDebug", "ViewModel: Received prediction: ${result.word} (${result.confidence})")
                    
                    // STICKY TRANSLATION LOGIC WITH TIMEOUT
                    val currentTime = System.currentTimeMillis()
                    
                    // Update validation time if we have a strong detection
                    if (!result.word.startsWith("_") && result.word.isNotEmpty() && result.confidence > 0.55f) {
                         lastDetectionTime = currentTime
                    }

                    // Decide what to show (Show >= 0.50)
                    val displayWord = if (!result.word.startsWith("_") && result.word.isNotEmpty() && result.confidence > 0.50f) {
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
                            isProcessing = result.confidence > 0.3f 
                        ) 
                    }
                    
                    // TTS Logic uses the NEW word detection, not just the display state
                    val newWordDetected = !result.word.startsWith("_") && result.word.isNotEmpty() && (result.word != previousWord)
                    
                    // LOWERED THRESHOLD: 0.50f (Matches display)
                    if (newWordDetected && result.confidence > 0.50f) {
                        // LOG FOR USER: This is the definitive "Detection Event"
                        android.util.Log.i("SealyzeDetection", "🎯 DETECTADO: '${result.word}'")
                        
                        // 1. Add to Buffer (Wait for more words)
                        smartSentenceRepository.addWord(result.word)
                        
                        // NO IMMEDIATE TTS: User wants to build a sentence first.
                        // ttsManager.speak(result.word) <-- REMOVED
                        
                        // 2. Start/Reset 3s Timer (Debounce)
                        sentenceDebounceJob?.cancel()
                        
                        // Cancel the "Hide" timer if it was running, to keep sticky text visible while buffering
                        clearSentenceJob?.cancel()

                        sentenceDebounceJob = viewModelScope.launch {
                            android.util.Log.d("SealyzeDebug", "⏳ Waiting ${_uiState.value.sentenceDelay}ms for more signs...")
                            
                            // Dynamic timeout based on user selection
                            val totalDuration = _uiState.value.sentenceDelay
                            val step = 100L
                            for (elapsed in 0L..totalDuration step step) {
                                if (!isActive) break
                                val progress = elapsed.toFloat() / totalDuration
                                _uiState.update { it.copy(autoGenProgress = progress) }
                                kotlinx.coroutines.delay(step)
                            }
                            
                            // 3. Timer Finished -> GENERATE, SPEAK & CLEAR
                            _uiState.update { it.copy(autoGenProgress = 1f) }
                            android.util.Log.d("SealyzeDebug", "⏳ 3s Finished! Generating Sentence...")
                            smartSentenceRepository.generateSentence()
                            
                            // NOTE: The 'generatedSentence.collect' block (lines 60-80) handles the speaking and 
                            // final clearing (after 5s) once the repository emits the sentence.
                            
                             _uiState.update { it.copy(autoGenProgress = 0f) }
                        }
                    } else {
                        android.util.Log.d("SealyzeDebug", "❌ TTS: NOT speaking '$displayWord' - Failed checks")
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
    }
}
