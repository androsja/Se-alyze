package com.sealyze.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.sp
import com.sealyze.presentation.viewmodel.CameraViewModel
import com.sealyze.BuildConfig

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    // ... existing initialization ...
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // Permission State
    val hasPermission = androidx.compose.runtime.remember {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    // Simple state to track if we should show camera
    val (permissionGranted, setPermissionGranted) = androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(hasPermission) 
    }
    
    // Update launcher to update state
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            setPermissionGranted(isGranted)
        }
    )

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!permissionGranted) {
            launcher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // State for Camera Lens (Default = BACK)
    // REMOVED local state: var lensFacing by remember { ... }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            // key(...) ensures CameraPreview is fully recreated when lens changes
            androidx.compose.runtime.key(uiState.lensFacing) {
                CameraPreview(
                    cameraLens = uiState.lensFacing,
                    onImageAnalyzed = { proxy -> 
                        viewModel.processImageProxy(proxy)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            SkeletonOverlay(
                landmarks = uiState.landmarks,
                modifier = Modifier.fillMaxSize()
            )

            // DEBUG INFO (Toggleable via gradle.properties)
            if ( BuildConfig.SHOW_DEBUG_OVERLAY) {
                // 1. Processing Indicator (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 100.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Determine State
                    val handsVisible = uiState.landmarks?.let { it.leftHand != null || it.rightHand != null } ?: false
                    val (statusText, statusColor) = when {
                        uiState.confidence > 0.6f -> "SEÑA DETECTADA" to Color.Green
                        uiState.confidence > 0.3f -> "ANALIZANDO" to Color.Yellow
                        handsVisible -> "SEÑA ENCONTRADA" to Color.Cyan
                        else -> "ESPERANDO" to Color.Red
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                         // Status Dot
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(10.dp)
                                .background(
                                    color = statusColor,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }

                // 2. Technical Stats (Top Left)
                if (uiState.debugInfo.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 100.dp, start = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = uiState.debugInfo,
                            color = Color.Green,
                            style = MaterialTheme.typography.labelSmall, // Letra chiquitica
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            // --- TOP BAR ---
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // ... (modifiers omitted)
            ) {
                // ... (Title Area omitted) ...

                // Glassy Switch Button
                androidx.compose.material3.IconButton(
                    onClick = { 
                        val newLens = if (uiState.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                            androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                        } else {
                            androidx.camera.core.CameraSelector.LENS_FACING_BACK
                        }
                        viewModel.setLensFacing(newLens)
                    },
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.2f), 
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .border(
                            width = 1.dp, 
                            color = Color.White.copy(alpha = 0.3f), 
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Cameraswitch,
                        contentDescription = "Cambiar Cámara",
                        tint = Color.White
                    )
                }
            }

        } else {
             // Fallback text if denied or waiting
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Text("Se requieren permisos de cámara para continuar.", color = Color.White)
             }
        }

        // --- UNIFIED COMPACT BOTTOM BAR ---
        // Merges Live Detection, Smart Sentence, and Controls into one sleek glass panel.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT: Main Content (Buffer + Result)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 1. Buffer Display (or timer hint)
                    // SHOW BUFFER: Shows "Hola • Mundo" in yellow while waiting for 3s timer
                    if (uiState.smartSentenceBuffer.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.smartSentenceBuffer.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Yellow.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            
                            // TIMER TEXT (Optional hint)
                            if (uiState.autoGenProgress > 0f) {
                                Text(
                                    text = " (Esperando...)", 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    } else if (uiState.autoGenProgress > 0f) {
                        // Fallback purely for progress
                        Text(
                           text = "Procesando...",
                           style = MaterialTheme.typography.bodySmall,
                           color = Color.Yellow.copy(alpha = 0.9f)
                        )
                    } else {
                        Text(
                            text = "Escuchando gestos...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // 2. Main Title: Smart Sentence OR Live Word
                    val mainText = if (uiState.smartSentence.isNotEmpty()) {
                        "✨ ${uiState.smartSentence}"
                    } else if (uiState.currentTranslation.isNotEmpty()) {
                        uiState.currentTranslation
                    } else {
                        "..."
                    }
                    
                    val mainColor = if (uiState.smartSentence.isNotEmpty()) Color.Cyan else Color.White

                    // Animated Text Transition
                    androidx.compose.animation.AnimatedContent(
                        targetState = mainText,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 }).togetherWith(
                                androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 2 }
                            )
                        },
                        label = "MainText"
                    ) { target ->
                        Text(
                            text = target,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = mainColor
                        )
                    }
                    
                    // 3. PROGRESS BAR (Countdown)
                    if (uiState.autoGenProgress > 0f) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = uiState.autoGenProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, end = 16.dp)
                                .height(2.dp),
                            color = Color.Cyan,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                }

                // RIGHT: Actions (Clear / Status Icon)
                // If hands are detected, show the "Scanning Waves"
                // If text exists, show Clear button
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    
                    // --- TIMER SELECTOR ---
                    var showTimerMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        androidx.compose.material3.IconButton(
                            onClick = { showTimerMenu = true },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                             Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Timer, 
                                contentDescription = "Tiempo",
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        
                        androidx.compose.material3.DropdownMenu(
                            expanded = showTimerMenu,
                            onDismissRequest = { showTimerMenu = false }
                        ) {
                            listOf(5000L to "5s", 8000L to "8s", 10000L to "10s").forEach { (delay, label) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(label + if (uiState.sentenceDelay == delay) " ✓" else "") },
                                    onClick = { 
                                        viewModel.setSentenceDelay(delay)
                                        showTimerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Scanning Animation (Visible when hands are present)
                    val handsVisible = uiState.landmarks?.let { it.leftHand != null || it.rightHand != null } ?: false
                    
                    if (handsVisible) {
                        ScanningWaves(modifier = Modifier.padding(end = 12.dp))
                    }

                    if (uiState.smartSentenceBuffer.isNotEmpty() || uiState.smartSentence.isNotEmpty()) {
                        androidx.compose.material3.IconButton(
                            onClick = { viewModel.onClearSentence() },
                            modifier = Modifier
                                .background(
                                    color = Color.White.copy(alpha = 0.15f), 
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.DeleteSweep,
                                contentDescription = "Borrar",
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    } else if (!handsVisible) {
                        // Idle Indicator (Static dot when no hands)
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.FiberManualRecord,
                            contentDescription = "Idle",
                            tint = Color.Gray.copy(alpha = 0.3f),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningWaves(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    // Create 4 bars with explicit animations
    // Use raw looping to avoid list processing issues
    val scale1 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label="1")
    val scale2 by infiniteTransition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label="2")
    val scale3 by infiniteTransition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label="3")
    val scale4 by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label="4")

    val scales = listOf(scale1, scale2, scale3, scale4)

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scales.forEach { scale ->
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .width(4.dp)
                    .height(24.dp) // Max height container
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(4.dp)
                        .height(24.dp * scale)
                        .background(Color.Cyan, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
