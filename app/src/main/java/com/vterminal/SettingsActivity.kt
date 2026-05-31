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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }

        fun section(t: String) { layout.addView(TextView(this).apply { text = t; setTextColor(0xFF666666.toInt()); textSize = 11f; setPadding(0,12,0,4) }) }
        fun btn(t: String, a: () -> Unit) { layout.addView(Button(this).apply { text = t; setTextColor(0xFFE0E0E0.toInt()); setBackgroundColor(0xFF1A1A2E.toInt()); setOnClickListener { a() } }) }

        section("TERMINAL")
        btn("Ubuntu Shell (Proot)") { finish() }
        btn("Termux Integrated") { startActivity(Intent(this, TermuxActivity::class.java)) }
        btn("V-Viewer (GPU Desktop)") { Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show() }

        section("CONNECTIVITY")
        btn("WiFi Settings") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        btn("Bluetooth Settings") { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        btn("Network & Internet") { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }

        section("DEVICE")
        btn("Camera Preview") {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
        btn("Manage All Files") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:$packageName") })
            }
        }
        btn("Developer Options") { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }

        section("ABOUT")
        btn("V-Terminal Pro v1.0") { Toast.makeText(this, "Terminal & Ubuntu • Termux Integrated • GPU Viewer\n2026", Toast.LENGTH_LONG).show() }

        scroll.addView(layout); setContentView(scroll)
    }
}
