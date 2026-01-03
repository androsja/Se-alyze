package com.sealyze.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import com.sealyze.domain.model.Landmark
import com.sealyze.domain.model.SignFrame

@Composable
fun SkeletonOverlay(
    landmarks: SignFrame?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        landmarks?.let { frame ->
            val width = size.width
            val height = size.height

            // Helper to draw points
            fun drawPoints(points: List<Landmark>?, color: Color) {
                points?.forEach { point ->
                    // Normalization note: MediaPipe returns [0,1]. We scale to screen size.
                    // IMPORTANT: X is mirrored for front camera usually, but we'll draw raw first.
                    drawCircle(
                        color = color,
                        radius = 8f,
                        center = Offset(point.x * width, point.y * height)
                    )
                }
            }
            
            drawPoints(frame.leftHand, Color.Green)
            drawPoints(frame.rightHand, Color.Green)
            drawPoints(frame.pose, Color.Blue)
        }
    }
}
