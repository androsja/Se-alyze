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
                    
                    _uiState.update { 
                        it.copy(
                            currentTranslation = result.word,
                            confidence = result.confidence
                        ) 
                    }
                    
                    // TTS Logic: Speak if confidence is high and word changed
                    if (result.confidence > 0.85f && result.word != previousWord && result.word.isNotEmpty()) {
                        ttsManager.speak(result.word)
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
