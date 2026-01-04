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
        
        // LOGGING: Check if hands are actually detected
        if (frame.leftHand != null || frame.rightHand != null) {
            android.util.Log.d("SealyzeDebug", "MediaPipe: Hands detected! L=${frame.leftHand != null} R=${frame.rightHand != null}")
        } else {
            // Uncomment to see empty frames (can be spammy)
            // android.util.Log.v("SealyzeDebug", "MediaPipe: No hands detected")
        }

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
        var leftHand: List<Landmark>? = null
        var rightHand: List<Landmark>? = null

        // MediaPipe returns a list of hands. We need to check 'handedness' to know which is which.
        // handedness() returns a list of Categories for each detected hand.
        
        for (i in result.landmarks().indices) {
            val handLandmarks = result.landmarks()[i]
            val handCategory = result.handedness()[i].firstOrNull() ?: continue
            
            // MediaPipe in 'Selfie' mode sometimes inverts Left/Right labels depending on configuration.
            // But usually 'Right' means the user's right hand. 
            // We map the landmarks and FLIP X (1-x) to match the 'Mirror Mode' training data.
            
            val mappedLandmarks = handLandmarks.map {
                Landmark(
                    x = it.x(), // Removed manual flip. Trusting raw MP output.
                    y = it.y(),
                    z = it.z(),
                    visibility = it.visibility().orElse(0f)
                )
            }

            // CRITICAL FIX: "Selfie Mode" Handedness Swap
            // MediaPipe in Selfie mode often sees User's Right Hand as "Left" (Screen Left).
            // We swap them here so the Model receives "Right Hand" data when User raises Right Hand.
            if (handCategory.categoryName() == "Left") {
                rightHand = mappedLandmarks // Was leftHand
            } else {
                leftHand = mappedLandmarks // Was rightHand
            }
        }
        
        return SignFrame(
            leftHand = leftHand,
            rightHand = rightHand,
            pose = null,
            timestamp = System.currentTimeMillis()
        )
    }
}
