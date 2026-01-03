package com.sealyze.data.source

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.sealyze.domain.model.Landmark
import com.sealyze.domain.model.SignFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaPipeDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var handLandmarker: HandLandmarker? = null
    // SharedFlow to emit results to subscribers (ViewModel/Repo)
    private val _landmarkFlow = MutableSharedFlow<SignFrame>(extraBufferCapacity = 1)
    val landmarkFlow: Flow<SignFrame> = _landmarkFlow.asSharedFlow()

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)
            .build()

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
            // In production, emit an error state to the UI
        }
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, input: MPImage) {
        val frame = convertToDomain(result)
        // Emit to flow. 
        // Note: This callback comes from MediaPipe thread. We use tryEmit to be safe/fast.
        _landmarkFlow.tryEmit(frame)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        error.printStackTrace()
    }

    fun processVideoFrame(imageProxy: ImageProxy) {
        val mpImage = BitmapImageBuilder(imageProxy.toBitmap()).build()
        // Use current timestamp
        handLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
        
        // IMPORTANT: Must close!
        imageProxy.close()
    }

    private fun convertToDomain(result: HandLandmarkerResult): SignFrame {
        val leftHand = result.landmarks().getOrNull(0)?.map { 
            Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
        }
        val rightHand = result.landmarks().getOrNull(1)?.map {
            Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
        }
        
        return SignFrame(
            leftHand = leftHand,
            rightHand = rightHand,
            pose = null,
            timestamp = System.currentTimeMillis()
        )
    }
}
