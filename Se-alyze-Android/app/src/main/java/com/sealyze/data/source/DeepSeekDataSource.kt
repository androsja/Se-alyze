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
class DeepSeekDataSource @Inject constructor() {

    private val deepSeekUrl = "https://api.deepseek.com/chat/completions"
    private val deepSeekModel = "deepseek-chat"

    suspend fun generateSentence(prompt: String): String? {
        return withContext(Dispatchers.IO) {
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
                        return@withContext clean
                    }
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.e("SealyzeDetection", "🤖 DEEPSEEK ERROR (${connection.responseCode}): $error")
                }
            } catch (e: Exception) {
                android.util.Log.e("SealyzeDetection", "🤖 DEEPSEEK EXCEPTION: ${e.message}")
            }
            return@withContext null
        }
    }
}
