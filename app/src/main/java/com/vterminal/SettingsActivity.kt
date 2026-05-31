package com.vterminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Title
        val title = TextView(this).apply {
            text = "Settings"
            textSize = 20f
            setTextColor(0xFF00FFCC.toInt())
            setPadding(0, 0, 0, 16)
            layout.addView(this)
        }

        fun addItem(section: String, text: String, action: () -> Unit) {
            // Section header
            val header = TextView(this).apply {
                this.text = section
                textSize = 11f
                setTextColor(0xFF666666.toInt())
                setPadding(0, 12, 0, 4)
            }
            layout.addView(header)

            // Button
            val btn = Button(this@SettingsActivity).apply {
                this.text = text
                setTextColor(0xFFE0E0E0.toInt())
                setBackgroundColor(0xFF1A1A2E.toInt())
                setOnClickListener { action() }
            }
            layout.addView(btn)
        }

        addItem("CONNECTIVITY", "WiFi Settings") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        addItem("", "Bluetooth Settings") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        addItem("", "Network & Internet") {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }

        addItem("STORAGE", "Manage All Files") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        addItem("SYSTEM", "Developer Options") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        addItem("", "Location Settings") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        addItem("ABOUT", "V-Terminal Pro v1.0") {
            Toast.makeText(this, "Terminal & Ubuntu Environment\n2026 Edition", Toast.LENGTH_LONG).show()
        }

        scroll.addView(layout)
        setContentView(scroll)
    }
}
