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
            setPadding(32, 32, 32, 32)
        }

        fun section(t: String) {
            layout.addView(TextView(this).apply {
                text = t
                setTextColor(0xFF666666.toInt())
                textSize = 11f
                setPadding(0, 16, 0, 4)
            })
        }

        fun btn(t: String, a: () -> Unit) {
            layout.addView(Button(this).apply {
                text = t
                setTextColor(0xFFE0E0E0.toInt())
                setBackgroundColor(0xFF1A1A2E.toInt())
                setOnClickListener { a() }
            })
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "V-Terminal Pro"
            textSize = 20f
            setTextColor(0xFF00FFCC.toInt())
            setPadding(0, 0, 0, 16)
        })

        section("TERMINAL")
        btn("Ubuntu Shell (Proot)") { finish() }

        section("STORAGE")
        btn("Manage All Files") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }

        section("DEVICE")
        btn("Developer Options") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        btn("Location Settings") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        section("ABOUT")
        btn("V-Terminal Pro v2.0") {
            Toast.makeText(
                this,
                "Terminal & Ubuntu Environment\nARM64 Shell Support\nMulti-Archive Extraction\n2026 Edition",
                Toast.LENGTH_LONG
            ).show()
        }

        scroll.addView(layout)
        setContentView(scroll)
    }
}
