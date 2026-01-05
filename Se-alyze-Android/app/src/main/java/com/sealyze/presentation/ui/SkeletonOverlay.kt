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
            
            // 1. Calculate Scaling to match PreviewView (FILL_CENTER) behavior
            // Camera assumes 9:16 (vertical). Screen is usually taller (e.g. 9:20).
            val cameraAspectRatio = 9f / 16f 
            val screenAspectRatio = width / height
            
            var scaleFactor = 1f
            var offsetX = 0f
            var offsetY = 0f

            if (screenAspectRatio < cameraAspectRatio) {
                // Screen is narrower than camera -> Scale by Height, Crop Width
                scaleFactor = height / 16f // Normalized 1.0 = 16 units
                // Actually easier: Scale to fill Height
                val scaledWidth = height * cameraAspectRatio
                offsetX = (width - scaledWidth) / 2f
                scaleFactor = height // Since y is 0..1
            } else {
                 // Screen is wider/shorter -> Scale by Width, Crop Height
                 val scaledHeight = width / cameraAspectRatio
                 offsetY = (height - scaledHeight) / 2f
                 scaleFactor = width // Since x is 0..1 (approx)
            }
            
            // Simplified Logic for "Fill Height" (Most phones are tall)
            // We assume the preview FILLS the screen.
            // We need to map 0..1 to the scaled rect.
            
            val previewW: Float
            val previewH: Float
            
            if (screenAspectRatio < cameraAspectRatio) {
                 // Screen is "Skinnier". Image is wider. Crop sides.
                 previewH = height
                 previewW = height * cameraAspectRatio
                 offsetX = (width - previewW) / 2f
                 offsetY = 0f
            } else {
                 // Screen is "Fatter". Image is taller. Crop top/bottom.
                 previewW = width
                 previewH = width / cameraAspectRatio
                 offsetX = 0f
                 offsetY = (height - previewH) / 2f
            }

            // Helper to draw points
            fun drawPoints(points: List<Landmark>?, color: Color) {
                points?.forEach { point ->
                    // 1. Mirror X (Front Camera)
                    val xMirrored = 1 - point.x
                    
                    // 2. Scale & Translate
                    val screenX = (xMirrored * previewW) + offsetX
                    val screenY = (point.y * previewH) + offsetY
                    
                    drawCircle(
                        color = color,
                        radius = 8f,
                        center = Offset(screenX, screenY)
                    )
                }
            }
            
            drawPoints(frame.leftHand, Color.Green)
            drawPoints(frame.rightHand, Color.Green)
            drawPoints(frame.pose, Color.Blue)
        }
    }
}
