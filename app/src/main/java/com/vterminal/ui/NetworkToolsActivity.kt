package com.vterminal.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*
import java.net.*

class NetworkToolsActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        
        output = TextView(this).apply { setTextColor(0xFF00FFCC.toInt()); textSize = 12f }
        root.addView(ScrollView(this).apply { addView(output) })
        
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply { hint = "IP or domain..."; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        bar.addView(input)
        
        val pingBtn = Button(this).apply { text = "PING"; setOnClickListener { ping(input.text.toString()) } }
        bar.addView(pingBtn)
        
        val dnsBtn = Button(this).apply { text = "DNS"; setOnClickListener { dnsLookup(input.text.toString()) } }
        bar.addView(dnsBtn)
        
        root.addView(bar)
        setContentView(root)
    }

    private fun ping(host: String) {
        Thread {
            try {
                val pb = ProcessBuilder("/system/bin/ping", "-c", "4", host)
                pb.redirectErrorStream(true)
                val p = pb.start()
                p.inputStream.bufferedReader().forEachLine { append("$it\n") }
            } catch (e: Exception) { append("Error: ${e.message}\n") }
        }.start()
    }

    private fun dnsLookup(host: String) {
        Thread {
            try {
                val addr = InetAddress.getAllByName(host)
                append("DNS Lookup for $host:\n")
                addr.forEach { append("  ${it.hostAddress}\n") }
            } catch (e: Exception) { append("Error: ${e.message}\n") }
        }.start()
    }

    private fun append(t: String) { runOnUiThread { output.append(t) } }
}
