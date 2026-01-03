package com.sealyze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sealyze.presentation.ui.CameraScreen
import com.sealyze.presentation.ui.theme.SealyzeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // No theme defined yet, but assuming MaterialTheme default wrapping
            // or we create a basic theme file. For now valid Compose code.
            CameraScreen()
        }
    }
}
