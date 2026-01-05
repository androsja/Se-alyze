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

    // 2. Configuración DeepSeek (Fallback)
    private val deepSeekUrl = "https://api.deepseek.com/chat/completions"
    private val deepSeekModel = "deepseek-chat"

    suspend fun generateSentence(words: List<String>): String {
        if (words.isEmpty()) return ""

        return withContext(Dispatchers.IO) {
            val promptText = "Eres un traductor estricto de lengua de señas. Conecta estas palabras en una frase simple en español. NO agregues información nueva. Palabras: ${words.joinToString(", ")}"
            android.util.Log.i("SealyzeDetection", "SCENARIO: Trying Gemini Chain first...")

            // PASO 1: Intentar con GEMINI
            for (model in geminiModels) {
                val success = tryGemini(model, promptText)
                if (success != null) return@withContext success
                kotlinx.coroutines.delay(200)
            }
            
            // DEBUG: List available models if everything failed
            checkAvailableModels()

            // PASO 2: Si Gemini falla todo, intentar DEEPSEEK
            android.util.Log.w("SealyzeDetection", "⚠️ GEMINI FALLÓ. Cambiando a DEEPSEEK...")
            val deepSeekSuccess = tryDeepSeek(promptText)
            if (deepSeekSuccess != null) return@withContext deepSeekSuccess

            // PASO 3: Fallback final (palabras crudas)
            android.util.Log.e("SealyzeDetection", "🤖 CRITICAL: Fallaron Gemini y DeepSeek.")
            return@withContext words.joinToString(" ")
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

    // --- DEEPSEEK IMPLEMENTATION ---
    private fun tryDeepSeek(prompt: String): String? {
        try {
            val jsonBody = JSONObject().apply {
                put("model", deepSeekModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Eres un traductor estricto. Responde solo con la frase.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.1)
            }

            val connection = (URL(deepSeekUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val content = JSONObject(response).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                
                if (!content.isNullOrEmpty()) {
                    val clean = content.replace("\"", "")
                    android.util.Log.i("SealyzeDetection", "🤖 DEEPSEEK EXITOSO: '$clean'")
                    return clean
                }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                android.util.Log.e("SealyzeDetection", "🤖 DEEPSEEK ERROR (${connection.responseCode}): $error")
            }
        } catch (e: Exception) {
            android.util.Log.e("SealyzeDetection", "🤖 DEEPSEEK EXCEPTION: ${e.message}")
        }
        return null
    }
}
