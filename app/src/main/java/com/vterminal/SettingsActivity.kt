package com.vterminal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        fun btn(t: String, a: () -> Unit) { layout.addView(Button(this).apply { text = t; setTextColor(0xFFE0E0E0.toInt()); setBackgroundColor(0xFF1A1A2E.toInt()); setOnClickListener { a() } }) }
        btn("Ubuntu Shell") { finish() }
        btn("Camera") { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
        btn("Developer Options") { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        setContentView(layout)
    }
}
