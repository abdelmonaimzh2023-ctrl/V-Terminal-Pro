package com.vterminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "V-Terminal Pro Settings"
            textSize = 18f
            setTextColor(0xFF00FFCC.toInt())
            layout.addView(this)
        }

        fun addButton(text: String, action: () -> Unit) {
            val btn = Button(this@SettingsActivity).apply {
                this.text = text
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF1A1A2E.toInt())
                setOnClickListener { action() }
            }
            layout.addView(btn)
        }

        addButton("[ WiFi ] Open WiFi Settings") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        addButton("[ Network ] Open Network Settings") {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }

        addButton("[ Bluetooth ] Open Bluetooth Settings") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        addButton("[ Storage ] Manage All Files") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        addButton("[ Location ] Open Location Settings") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        addButton("[ Developer ] Developer Options") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }

        addButton("[ About ] V-Terminal Pro v1.0") {
            Toast.makeText(this, "V-Terminal Pro // Terminal & Ubuntu // 2026", Toast.LENGTH_LONG).show()
        }

        scroll.addView(layout)
        setContentView(scroll)
    }
}
