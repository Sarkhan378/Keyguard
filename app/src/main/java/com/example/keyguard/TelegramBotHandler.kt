package com.example.keyguard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TelegramBotHandler(
    private val context: Context,
    private val botToken: String,
    private val chatId: String,
    private val resetLockoutCallback: () -> Unit
) {
    companion object {
        const val TAG = "TelegramBotHandler"
    }

    private val client = OkHttpClient()
    private var lastUpdateId: Long? = null

    fun startListening() {
        Log.d(TAG, "Starting to listen for Telegram updates")
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val updates = getTelegramUpdates()
                    for (update in updates) {
                        Log.d(TAG, "Received update: $update")
                        if (update.message?.text == "/reset") {
                            resetLockoutCallback()
                            sendTelegramMessage("Локаут снят")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in startListening", e)
                }
                delay(5000) // Проверяем обновления каждые 5 секунд
            }
        }
    }

    private suspend fun getTelegramUpdates(): List<Update> {
        val url = "https://api.telegram.org/bot$botToken/getUpdates"
        val requestBuilder = Request.Builder().url(url)

        lastUpdateId?.let {
            requestBuilder.url("$url?offset=${it + 1}")
        }

        while (!isNetworkAvailable(context)) {
            Log.e(TAG, "No internet connection available. Retrying in 5 seconds.")
            delay(5000)
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to get updates: ${response.message}")
                return emptyList()
            }

            val updates = mutableListOf<Update>()
            val responseBody = response.body?.string() ?: return emptyList()
            Log.d(TAG, "getTelegramUpdates responseBody: $responseBody")
            val jsonResponse = JSONObject(responseBody)
            val resultArray = jsonResponse.getJSONArray("result")

            for (i in 0 until resultArray.length()) {
                val updateJson = resultArray.getJSONObject(i)
                val updateId = updateJson.getLong("update_id")
                val messageJson = updateJson.optJSONObject("message")
                val text = messageJson?.optString("text")
                updates.add(Update(Message(text)))
                lastUpdateId = updateId
            }

            updates
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getTelegramUpdates", e)
            emptyList()
        }
    }

    fun sendTelegramMessage(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.telegram.org/bot$botToken/sendMessage"
                val mediaType = "application/json".toMediaTypeOrNull()

                val requestBody = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", message)
                    put("parse_mode", "Markdown")
                }.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("TelegramBotHandler", "Failed to send message to Telegram: ${response.message}")
                    } else {
                        Log.i("TelegramBotHandler", "Message sent to Telegram successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("TelegramBotHandler", "Error sending message to Telegram", e)
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

data class Update(val message: Message?)
data class Message(val text: String?)
