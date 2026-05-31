package com.vterminal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.vterminal.ui.*

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }

        fun section(t: String) { layout.addView(TextView(this).apply { text = t; setTextColor(0xFF666666.toInt()); textSize = 11f; setPadding(0, 12, 0, 4) }) }
        fun btn(t: String, a: () -> Unit) { layout.addView(Button(this).apply { text = t; setTextColor(0xFFE0E0E0.toInt()); setBackgroundColor(0xFF1A1A2E.toInt()); setOnClickListener { a() } }) }

        section("TERMINAL")
        btn("Ubuntu Shell (Proot)") { finish() }

        section("TOOLS")
        btn("File Manager") { startActivity(Intent(this, FileManagerActivity::class.java)) }
        btn("Process Manager") { startActivity(Intent(this, ProcessManagerActivity::class.java)) }
        btn("Network Tools") { startActivity(Intent(this, NetworkToolsActivity::class.java)) }
        btn("Media Player") { startActivity(Intent(this, MediaPlayerActivity::class.java)) }

        section("DEVICE")
        btn("Camera") { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
        btn("Developer Options") { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }

        section("ABOUT")
        btn("V-Terminal Pro v2.0") { Toast.makeText(this, "Terminal & Ubuntu Environment\n2026 Edition\n2000+ lines of code", Toast.LENGTH_LONG).show() }

        scroll.addView(layout); setContentView(scroll)
    }
}
