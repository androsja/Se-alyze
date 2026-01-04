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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.*
import androidx.compose.ui.unit.sp
import com.sealyze.presentation.viewmodel.CameraViewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
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
    var lensFacing by androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableIntStateOf(androidx.camera.core.CameraSelector.LENS_FACING_BACK) 
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permissionGranted) {
            // key(...) ensures CameraPreview is fully recreated when lens changes
            androidx.compose.runtime.key(lensFacing) {
                CameraPreview(
                    cameraLens = lensFacing,
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
            
            // --- TOP BAR ---
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Title / Logo Area
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = "Se-alyze",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Traductor LSC en tiempo real",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Glassy Switch Button
                androidx.compose.material3.IconButton(
                    onClick = { 
                        lensFacing = if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                            androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                        } else {
                            androidx.camera.core.CameraSelector.LENS_FACING_BACK
                        }
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

        // --- BOTTOM SHEET (Glassmorphism) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
             androidx.compose.foundation.layout.Column(
                 horizontalAlignment = Alignment.CenterHorizontally,
                 modifier = Modifier.fillMaxWidth()
             ) {
                Text(
                    text = "TRADUCCIÓN DETECTADA",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Animated Text Transition
                androidx.compose.animation.AnimatedContent(
                    targetState = if (uiState.currentTranslation.isNotEmpty()) uiState.currentTranslation else "...",
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn()).togetherWith(
                            androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                        )
                    },
                    label = "TranslationText"
                ) { targetText ->
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
             }
        }
    }
}
