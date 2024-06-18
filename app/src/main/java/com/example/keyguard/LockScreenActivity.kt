package com.example.keyguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText

class LockScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LockScreenActivity", "onCreate called")
        setContentView(R.layout.activity_lock_screen)

        val editTextPin = findViewById<EditText>(R.id.editTextPin)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)

        btnUnlock.setOnClickListener {
            val enteredPin = editTextPin.text.toString()
            Log.d("LockScreenActivity", "Unlock button clicked with PIN: $enteredPin")
            val intent = Intent(this, LockScreenService::class.java)
            intent.putExtra("PIN_CODE", enteredPin)
            startService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("LockScreenActivity", "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d("LockScreenActivity", "onResume called")
    }
}
