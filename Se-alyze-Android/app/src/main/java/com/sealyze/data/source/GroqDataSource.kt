package com.sealyze.data.source

import com.sealyze.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqDataSource @Inject constructor() {

    private val groqUrl = "https://api.groq.com/openai/v1/chat/completions"
    // Using Llama 3.3 70B Versatile (New recommended default)
    private val groqModel = "llama-3.3-70b-versatile"

    suspend fun generateSentence(prompt: String): String? {
        // Fail fast if no key
        if (BuildConfig.GROQ_API_KEY.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("model", groqModel)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "Eres un intérprete experto de lengua de señas en tiempo real. Tu trabajo es convertir una secuencia de palabras clave detectadas (que pueden tener errores o repeticiones) en una frase coherente y natural en español.\n" +
                                    "REGLAS:\n" +
                                    "1. Elimina repeticiones innecesarias (ej: 'hola hola buenos dias' -> 'Hola, buenos días').\n" +
                                    "2. Agrega conectores y artículos para darle sentido gramatical.\n" +
                                    "3. Si palabras sueltas no tienen sentido en el contexto, prioriza la coherencia.\n" +
                                    "4. Responde ÚNICAMENTE con la frase final corregida. Nada más.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.1)
                }

                val connection = (URL(groqUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    doOutput = true
                }

                OutputStreamWriter(connection.outputStream).use { it.write(jsonBody.toString()) }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val content = JSONObject(response).optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                    
                    if (!content.isNullOrEmpty()) {
                        val clean = content.replace("\"", "")
                        android.util.Log.i("SealyzeDetection", "🚀 GROQ EXITOSO: '$clean'")
                        return@withContext clean
                    }
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.e("SealyzeDetection", "🚀 GROQ ERROR (${connection.responseCode}): $error")
                }
            } catch (e: Exception) {
                android.util.Log.e("SealyzeDetection", "🚀 GROQ EXCEPTION: ${e.message}")
            }
            return@withContext null
        }
    }
}
