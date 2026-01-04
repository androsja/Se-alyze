package com.sealyze.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.ImageProxy
import com.sealyze.data.source.MediaPipeDataSource
import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.usecase.RecognizeSignUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val currentTranslation: String = "",
    val confidence: Float = 0f,
    val landmarks: SignFrame? = null,
    val debugInfo: String = ""
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val recognizeSignUseCase: RecognizeSignUseCase,
    private val mediaPipeDataSource: MediaPipeDataSource,
    private val ttsManager: com.sealyze.presentation.util.TextToSpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        // 1. Observe Landmarks from MediaPipe DataSource (UI Updates)
        viewModelScope.launch {
            mediaPipeDataSource.landmarkFlow.collect { signFrame ->
                _uiState.update { it.copy(landmarks = signFrame) }
            }
        }
        
        // 2. Observe Landmarks for Recognition Pipeline (Logic)
        viewModelScope.launch {
            recognizeSignUseCase(mediaPipeDataSource.landmarkFlow)
                .collect { result ->
                    val previousWord = _uiState.value.currentTranslation
                    
                    android.util.Log.d("SealyzeDebug", "ViewModel: Received prediction: ${result.word} (${result.confidence})")
                    
                    // Clear translation text when detecting neutral state (_none)
                    val displayWord = if (result.word.startsWith("_")) "" else result.word
                    
                    _uiState.update { 
                        it.copy(
                            currentTranslation = displayWord,
                            confidence = result.confidence,
                            debugInfo = "Conf: ${"%.2f".format(result.confidence)}"
                        ) 
                    }
                    
                    // TTS Logic: Speak if confidence is high and word changed (ignore technical classes starting with _)
                    // Compare with displayWord (what's shown) not previousWord to properly detect state changes
                    val confidenceCheck = result.confidence > 0.60f
                    val wordChangedCheck = displayWord != previousWord
                    val notEmptyCheck = displayWord.isNotEmpty()
                    val notTechnicalCheck = !result.word.startsWith("_")
                    
                    android.util.Log.d("SealyzeDebug", "TTS Checks: conf=${result.confidence} (>${0.60f}=$confidenceCheck), wordChanged=($previousWord != $displayWord)=$wordChangedCheck, notEmpty=$notEmptyCheck, notTechnical=$notTechnicalCheck")
                    
                    if (confidenceCheck && wordChangedCheck && notEmptyCheck && notTechnicalCheck) {
                        android.util.Log.d("SealyzeDebug", "✅ TTS: Speaking '$displayWord'")
                        ttsManager.speak(displayWord)
                    } else {
                        android.util.Log.d("SealyzeDebug", "❌ TTS: NOT speaking '$displayWord' - Failed checks")
                    }
                }
        }
    }

    fun processImageProxy(imageProxy: ImageProxy) {
        mediaPipeDataSource.processVideoFrame(imageProxy)
    }
    
    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
