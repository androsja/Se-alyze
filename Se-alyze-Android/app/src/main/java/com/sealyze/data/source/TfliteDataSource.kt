package com.sealyze.data.source

import android.content.Context
import com.sealyze.domain.model.SignFrame
import com.sealyze.domain.model.TranslationResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import java.io.FileInputStream
import java.nio.channels.FileChannel

class TfliteDataSource @Inject constructor(
    private val context: Context
) {

    private var interpreter: Interpreter? = null
    private val sequenceBuffer = ArrayDeque<SignFrame>()
    private val SEQUENCE_LENGTH = 32 // Adjusted to 32 frames (approx 1.05s at 30 FPS)
    private val FEATURE_SIZE = 126 // 21*3 (LH) + 21*3 (RH) - Simplified for Android
    private var labels: List<String> = emptyList()

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        if (interpreter != null) return
        try {
            android.util.Log.d("SealyzeDebug", "Attempting to initialize TFLite Interpreter...")
            val modelBuffer = loadModelFile("lsc_model.tflite")
            val options = Interpreter.Options()
            options.addDelegate(FlexDelegate())
            
            interpreter = Interpreter(modelBuffer, options)
            loadLabels()
            android.util.Log.d("SealyzeDebug", "Success: TFLite Interpreter initialized with FlexDelegate.")
        } catch (e: Throwable) { // Catch Throwable to handle UnsatisfiedLinkError/NoClassDefFoundError
            android.util.Log.e("SealyzeDebug", "CRITICAL FAILURE initializing TFLite: ${e.message}", e)
            e.printStackTrace()
        }
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels() {
        try {
            context.assets.open("labels.txt").bufferedReader().use {
                labels = it.readLines().filter { line -> line.isNotEmpty() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback default labels if file read fails (should not happen)
            labels = listOf("hola", "gracias", "yo", "querer", "agua", "ayuda_me")
        }
    }

    fun addToBufferAndPredict(frame: SignFrame): TranslationResult? {
        sequenceBuffer.addLast(frame)
        if (sequenceBuffer.size > SEQUENCE_LENGTH) {
            sequenceBuffer.removeFirst()
        }

        // DEBUG: Explicitly log if we see hands here to confirm flow logic
        if (frame.leftHand != null || frame.rightHand != null) {
             android.util.Log.d("SealyzeDebug", "TFLite: Frame added with hands! Buffer size=${sequenceBuffer.size}")
        }

        if (sequenceBuffer.size == SEQUENCE_LENGTH) {
            return runInference()
        }
        return null
    }

    private fun runInference(): TranslationResult? {


        // 1. Convert buffer to Flat Array [1, 30, 126]
        // Flattening order matches Python: LH (21*3) -> RH (21*3)
        val inputData = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(FEATURE_SIZE) } }

        sequenceBuffer.forEachIndexed { index, frame ->
            var offset = 0

            // 1. POSE REMOVED (Mismatch with Android HandLandmarker)
            // We skip the 33*4 landmarks entirely.

            // 2. LEFT HAND (21 landmarks * 3: x, y, z)
            if (frame.leftHand != null) {
                for (landmark in frame.leftHand) {
                    inputData[0][index][offset++] = landmark.x
                    inputData[0][index][offset++] = landmark.y
                    inputData[0][index][offset++] = landmark.z
                }
            } else {
                // Fill with zeros if hand not detected
                for (i in 0 until 21 * 3) {
                    inputData[0][index][offset++] = 0f
                }
            }

            // 3. RIGHT HAND (21 landmarks * 3: x, y, z)
            if (frame.rightHand != null) {
                for (landmark in frame.rightHand) {
                    inputData[0][index][offset++] = landmark.x
                    inputData[0][index][offset++] = landmark.y
                    inputData[0][index][offset++] = landmark.z
                }
            } else {
                for (i in 0 until 21 * 3) {
                    inputData[0][index][offset++] = 0f
                }
            }
        }

        // 1.5. CHECK: Are there enough valid frames?
        // If we haven't seen hands in at least 5 frames, don't predict.
        // Reduced from 8 to 5 to make detection feel "snappier".
        val validFrames = sequenceBuffer.count { it.leftHand != null || it.rightHand != null }
        if (validFrames < 5) {
            //android.util.Log.d("SealyzeDebug", "Skipping inference: Not enough hands detected ($validFrames/30)")
            return TranslationResult("", 0f, null, "No hands")
        }

        // 2. Run Inference
        val outputData = Array(1) { FloatArray(labels.size) }
        try {
            if (interpreter == null) {
                android.util.Log.w("SealyzeDebug", "Interpreter is null. Attempting re-initialization...")
                initializeInterpreter()
                if (interpreter == null) {
                    android.util.Log.e("SealyzeDebug", "ERROR: Interpreter is STILL NULL after retry. Skipping inference.")
                    return null
                }
            }
            interpreter?.run(inputData, outputData)
        } catch (e: Throwable) {
            android.util.Log.e("SealyzeDebug", "Inference error: ${e.message}", e)
            e.printStackTrace()
            return null
        }

        // 3. Process Result (Argmax & Debug Info)
        val probabilities = outputData[0]
        var maxIndex = -1
        var maxScore = 0f

        // Create list of (Label, Score) and sort descending
        val sortedProbs = probabilities.indices
            .map { i -> labels[i] to probabilities[i] }
            .sortedByDescending { it.second }
        
        // Build Debug String (Top 3) - Multi-line for readability
        val debugString = sortedProbs.take(3)
            .joinToString("\n") { "${it.first}: ${"%.2f".format(it.second)}" }
            
        android.util.Log.d("SealyzeDebug", "Inference: $debugString")

        val winner = sortedProbs[0]
        maxScore = winner.second
        val winnerLabel = winner.first

        // If no clear winner (low confidence), return empty result to clear UI
        if (maxScore > 0.50f) {
            android.util.Log.d("SealyzeDebug", "WINNER: $winnerLabel ($maxScore)")
            return TranslationResult(winnerLabel, maxScore, null, debugString)
        } else {
            android.util.Log.d("SealyzeDebug", "No clear winner (Max: $maxScore)")
            return TranslationResult("", maxScore, null, debugString)
        }
    }
}
