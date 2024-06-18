package com.example.keyguard

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup

class SettingsActivity : Activity() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        radioGroup = findViewById(R.id.radioGroup)
        saveButton = findViewById(R.id.saveButton)
        preferences = getSharedPreferences("settings", MODE_PRIVATE)

        saveButton.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val selectedRadioButton = findViewById<RadioButton>(selectedId)
            val method = selectedRadioButton.text.toString()

            preferences.edit().putString("unlock_method", method).apply()
        }

        // Load saved settings
        val savedMethod = preferences.getString("unlock_method", "PIN")

        if (savedMethod == "PIN") {
            radioGroup.check(R.id.radioPin)
        } else {
            radioGroup.check(R.id.radioPattern)
        }
    }
}
