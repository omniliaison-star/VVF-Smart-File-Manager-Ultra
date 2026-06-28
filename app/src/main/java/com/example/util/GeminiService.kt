package com.example.util

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {
    private const val TAG = "GeminiService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var overriddenKey: String? = null

    fun setOverriddenKey(key: String) {
        overriddenKey = if (key.trim().isNotEmpty()) key.trim() else null
    }

    val apiKey: String
        get() = overriddenKey ?: BuildConfig.GEMINI_API_KEY

    /**
     * Checks if a valid API key is available.
     */
    fun isApiKeyAvailable(): Boolean {
        return apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && apiKey != "GEMINI_API_KEY"
    }

    /**
     * Fetches embedding vector for a given text.
     * Returns a float array representing the embedding, or null if it fails.
     */
    suspend fun getEmbedding(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            Log.w(TAG, "API Key is not configured for embeddings. Falling back.")
            return@withContext null
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2-preview:embedContent?key=$apiKey"
        
        val jsonPayload = JSONObject().apply {
            put("model", "models/gemini-embedding-2-preview")
            put("content", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", text)
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Embedding request failed: Code ${response.code}, Msg ${response.message}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val jsonResponse = JSONObject(bodyString)
                val embeddingObj = jsonResponse.optJSONObject("embedding") ?: return@withContext null
                val valuesArray = embeddingObj.optJSONArray("values") ?: return@withContext null
                
                val vector = FloatArray(valuesArray.length())
                for (i in 0 until valuesArray.length()) {
                    vector[i] = valuesArray.getDouble(i).toFloat()
                }
                return@withContext vector
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching embedding: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Generates a text response from Gemini (e.g. for chat, summarization).
     * Uses gemini-3.5-flash by default, or gemini-3.1-pro-preview if high thinking is requested.
     */
    suspend fun generateResponse(
        prompt: String,
        systemInstruction: String? = null,
        useHighThinking: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext "API Key not configured. Please add your GEMINI_API_KEY to the AI Studio Secrets panel."
        }

        val modelName = if (useHighThinking) "gemini-3.1-pro-preview" else "gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        try {
            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })

                if (systemInstruction != null) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstruction)
                            })
                        })
                    })
                }

                // If high thinking is requested, add thinkingLevel
                if (useHighThinking) {
                    put("generationConfig", JSONObject().apply {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingLevel", "HIGH")
                        })
                    })
                }
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini Request Failed: ${response.code} $errorBody")
                    return@withContext "API Error (Code ${response.code}): ${response.message}\n$errorBody"
                }

                val bodyString = response.body?.string() ?: return@withContext "Empty response"
                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates") ?: return@withContext "No candidates found"
                if (candidates.length() == 0) return@withContext "No output candidate"
                
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content") ?: return@withContext "No content"
                val parts = content.optJSONArray("parts") ?: return@withContext "No parts"
                if (parts.length() == 0) return@withContext "No parts content"
                
                return@withContext parts.getJSONObject(0).optString("text", "No text found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content: ${e.message}", e)
            return@withContext "Connection Error: ${e.message}"
        }
    }

    /**
     * Helper to compute cosine similarity between two float vectors.
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        if (norm1 == 0f || norm2 == 0f) return 0f
        return (dotProduct / (Math.sqrt(norm1.toDouble()) * Math.sqrt(norm2.toDouble()))).toFloat()
    }
}
