package com.example.keyguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import android.os.BatteryManager
import android.os.Looper
import android.telephony.TelephonyManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


object DataCollector {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private const val TAG = "DataCollector"

    fun getLocation(context: Context, callback: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            fusedLocationClient?.lastLocation?.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val location = task.result
                    val locationString = "${location?.latitude}, ${location?.longitude}"
                    Log.d("DataCollector", "Location found: $locationString")
                    callback(locationString)
                } else {
                    Log.d("DataCollector", "Last known location is not available. Requesting new location.")
                    requestNewLocationData(context, callback)
                }
            }
        } else {
            Log.d("DataCollector", "Permission not granted")
            callback("Permission not granted")
        }
    }

    private fun requestNewLocationData(context: Context, callback: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("DataCollector", "Permission not granted for requesting new location")
            callback("Permission not granted")
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                val locationString = "${location?.latitude}, ${location?.longitude}"
                Log.d("DataCollector", "New location found: $locationString")
                callback(locationString)
                fusedLocationClient?.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("DataCollector", "Location permission not granted", e)
            callback("Permission not granted")
        }
    }

    fun getBatteryLevel(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level != -1 && scale != -1) {
            (level.toFloat() / scale.toFloat() * 100.0f).toInt()
        } else {
            -1
        }
    }

    fun getDeviceInfo(context: Context): String {
        val deviceModel = android.os.Build.MODEL
        val deviceOS = android.os.Build.VERSION.RELEASE
        val carrierName = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkOperatorName
        val macAddress = getMacAddress(context)
        return "Model: $deviceModel, OS: $deviceOS, Carrier: $carrierName, MAC: $macAddress"
    }

    private fun getMacAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val macAddress = wifiInfo.macAddress

            if (macAddress != null) {
                Log.d("DataCollector", "MAC Address obtained from WifiManager: $macAddress")
                macAddress
            } else {
                Log.d("DataCollector", "MAC Address is null, returning default address")
                "02:00:00:00:00:00"
            }
        } catch (ex: Exception) {
            Log.e("DataCollector", "Error getting MAC address", ex)
            "02:00:00:00:00:00"
        }
    }

    suspend fun getIpAddress(): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.ipify.org?format=json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("DataCollector", "IP response: $responseBody")
                        val jsonResponse = JSONObject(responseBody ?: "")
                        jsonResponse.getString("ip")
                    } else {
                        Log.e("DataCollector", "Failed to get IP address: ${response.message}")
                        "IP not available"
                    }
                }
            } catch (e: Exception) {
                Log.e("DataCollector", "Error getting IP address", e)
                "IP not available"
            }
        }
    }

}
