package com.vterminal.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*

class ProcessManagerActivity : AppCompatActivity() {
    private lateinit var processList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        processList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        scroll.addView(processList)
        setContentView(scroll)
        refreshProcesses()
    }

    private fun refreshProcesses() {
        processList.removeAllViews()
        try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", "ps -A -o PID,USER,NAME")
            pb.redirectErrorStream(true)
            val p = pb.start()
            p.inputStream.bufferedReader().forEachLine { line ->
                processList.addView(TextView(this).apply {
                    text = line; setTextColor(0xFFE0E0E0.toInt()); textSize = 10f
                })
            }
        } catch (e: Exception) {
            processList.addView(TextView(this).apply { text = "Error: ${e.message}" })
        }
    }
}
