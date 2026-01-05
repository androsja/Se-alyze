package com.sealyze.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.core.CameraSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sealyze_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SENTENCE_DELAY = "sentence_delay"
        private const val KEY_LENS_FACING = "lens_facing"
    }

    fun getSentenceDelay(): Long {
        return prefs.getLong(KEY_SENTENCE_DELAY, 5000L)
    }

    fun saveSentenceDelay(delay: Long) {
        prefs.edit().putLong(KEY_SENTENCE_DELAY, delay).apply()
    }

    fun getLensFacing(): Int {
        return prefs.getInt(KEY_LENS_FACING, CameraSelector.LENS_FACING_BACK)
    }

    fun saveLensFacing(lensFacing: Int) {
        prefs.edit().putInt(KEY_LENS_FACING, lensFacing).apply()
    }
}
