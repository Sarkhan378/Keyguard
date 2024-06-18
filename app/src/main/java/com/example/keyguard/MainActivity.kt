package com.example.keyguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!hasLocationPermission()) {
            requestLocationPermission()
        }

        val startLockScreenServiceButton: Button = findViewById(R.id.btnStartLockScreenService)
        startLockScreenServiceButton.setOnClickListener {
            Log.d("MainActivity", "Start LockScreenService button clicked")
            val intent = Intent(this, LockScreenService::class.java)
            if (Settings.canDrawOverlays(this)) {
                startService(intent)
                Log.d("MainActivity", "LockScreenService started")
            } else {
                Log.d("MainActivity", "Permission to draw overlays not granted")
                val overlaySettingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(overlaySettingsIntent)
            }
        }

        val settingsButton: Button = findViewById(R.id.btnSettings)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted, you can use the location-related functionality
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied, disable the functionality that depends on this permission.
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
