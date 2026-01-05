package com.sealyze.data.source

import com.sealyze.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiDataSource @Inject constructor() {

    // ESTRATEGIA HÍBRIDA: GEMINI (Gratis) -> DEEPSEEK (Pago/Backup)
    
    // 1. Lista de modelos Gemini (Prioridad)
    private val geminiModels = listOf(
        "gemini-2.0-flash" 
    )
    private val geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta/models/"



    suspend fun generateSentence(words: List<String>): String? {
        if (words.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            val promptText = "Eres un traductor estricto de lengua de señas. Conecta estas palabras en una frase simple en español. NO agregues información nueva. Palabras: ${words.joinToString(", ")}"
            android.util.Log.i("SealyzeDetection", "SCENARIO: Trying Gemini Chain...")

            // PASO 1: Intentar con GEMINI
            for (model in geminiModels) {
                val success = tryGemini(model, promptText)
                if (success != null) return@withContext success
                kotlinx.coroutines.delay(200)
            }
            
            android.util.Log.w("SealyzeDetection", "⚠️ GEMINI FALLÓ en todos los intentos.")
            checkAvailableModels() // Keep for debug
            return@withContext null
        }
    }

    // --- GEMINI IMPLEMENTATION ---
    private fun tryGemini(modelName: String, prompt: String): String? {
        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val url = URL("$geminiBaseUrl$modelName:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val text = JSONObject(response).optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim()
                
                if (!text.isNullOrEmpty()) {
                    android.util.Log.i("SealyzeDetection", "🤖 GEMINI EXITOSO ('$modelName'): '$text'")
                    return text
                }
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                android.util.Log.e("SealyzeDetection", "⚠️ GEMINI ERROR ($modelName): Code=${connection.responseCode}, Body=$errorMsg")
            }
        } catch (e: Exception) {
            android.util.Log.e("SealyzeDebug", "Gemini Exception: ${e.message}")
        }
        return null
    }

    private fun checkAvailableModels() {
        try {
            android.util.Log.i("SealyzeDetection", "🔍 INSPECTING AVAILABLE MODELS...")
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=${BuildConfig.GEMINI_API_KEY}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.i("SealyzeDetection", "📋 AVAILABLE MODELS: $response")
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                android.util.Log.e("SealyzeDetection", "❌ LIST MODELS FAILED: $error")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}
