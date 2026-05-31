package com.vterminal

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*

class TermuxActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private var shell: Process? = null
    private var shellIn: OutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8)
            setBackgroundColor(0xFF0A0A0F.toInt())
        }
        val title = TextView(this).apply {
            text = "[ TERMUX INTEGRATED ]"; setTextColor(0xFF00FFCC.toInt()); textSize = 13f
            setPadding(0,0,0,8); layout.addView(this)
        }
        scroll = ScrollView(this).apply { layout.addView(this) }
        output = TextView(this).apply {
            setTextColor(0xFFE0F7FA.toInt()); textSize = 12f; setPadding(8,8,8,8)
            setBackgroundColor(0xFF0D0E15.toInt()); scroll.addView(this)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0,8,0,0); layout.addView(this)
        }
        val prompt = TextView(this).apply {
            text = "$ "; setTextColor(0xFFFF0055.toInt()); textSize = 14f; bar.addView(this)
        }
        input = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF555555.toInt())
            hint = "command..."; inputType = EditorInfo.TYPE_NULL; imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            bar.addView(this)
        }
        val send = Button(this).apply {
            text = ">"; setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00FFCC.toInt())
            setOnClickListener { sendCmd() }; bar.addView(this)
        }
        input.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { sendCmd(); true } else false }
        setContentView(layout)
        startShell()
    }

    private fun startShell() {
        append("[TERMUX] Starting shell...\n")
        Thread {
            try {
                val pb = ProcessBuilder("/system/bin/sh")
                pb.redirectErrorStream(true); shell = pb.start(); shellIn = shell!!.outputStream
                Thread { shell!!.inputStream.bufferedReader().forEachLine { append("$it\n") } }.start()
                append("[TERMUX] Ready.\n\n")
            } catch (e: Exception) { append("Error: ${e.message}\n") }
        }.start()
    }

    private fun sendCmd() {
        val cmd = input.text.toString()
        if (cmd.isNotEmpty() && shellIn != null) {
            try { shellIn!!.write("$cmd\n".toByteArray()); shellIn!!.flush(); input.text.clear() }
            catch (e: Exception) { append("Error: ${e.message}\n") }
        }
    }

    private fun append(t: String) { runOnUiThread { output.append(t); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } } }
    override fun onDestroy() { super.onDestroy(); shell?.destroy() }
}
