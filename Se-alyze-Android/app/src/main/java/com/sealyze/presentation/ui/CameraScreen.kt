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
import androidx.hilt.navigation.compose.hiltViewModel
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
    
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> 
            // Often just forcing recomposition by changing state or just proceeding
            // For this simple MV, we'll assume if granted, the composable recomposing/or re-entering might be enough,
            // but usually we want a state variable.
        }
    )
    
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

    Box(modifier = Modifier.fillMaxSize()) {
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
            
            // Camera Switch Button (Top Right)
            androidx.compose.material3.IconButton(
                onClick = { 
                    lensFacing = if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                        androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                    } else {
                        androidx.camera.core.CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Cameraswitch,
                    contentDescription = "Cambiar Cámara",
                    tint = Color.White
                )
            }

        } else {
             // Fallback text if denied or waiting
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 Text("Se requieren permisos de cámara para continuar.", color = Color.White)
             }
        }

        // Translation Result Box (Visible always to show initial state)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(32.dp)
        ) {
            Text(
                text = if (uiState.currentTranslation.isNotEmpty()) uiState.currentTranslation else "Esperando señas...",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
