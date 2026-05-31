package com.vterminal

import android.os.Bundle
import android.view.Gravity
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF000000.toInt())
            setPadding(4, 4, 4, 4)
        }

        val extraKeys = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 4)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun addKey(label: String, key: String) {
            val btn = Button(this).apply {
                text = label; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1A1A2E.toInt())
                textSize = 10f; layoutParams = LinearLayout.LayoutParams(0, 60, 1f)
                setOnClickListener { if (shellIn != null) { shellIn!!.write(key.toByteArray()); shellIn!!.flush() } }
            }
            extraKeys.addView(btn)
        }
        addKey("ESC", "\u001b"); addKey("TAB", "\t"); addKey("CTRL", "\u0003"); addKey("ALT", "\u001b[A")
        addKey("-", "-"); addKey("/", "/"); addKey("|", "|"); addKey("UP", "\u001b[A")
        root.addView(extraKeys)

        scroll = ScrollView(this).apply { root.addView(this) }
        output = TextView(this).apply {
            setTextColor(0xFF00FF00.toInt()); textSize = 12f; setPadding(8, 8, 8, 8)
            setBackgroundColor(0xFF0D0E15.toInt()); scroll.addView(this)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 0)
            gravity = Gravity.CENTER_VERTICAL; root.addView(this)
        }
        val prompt = TextView(this).apply {
            text = "$ "; setTextColor(0xFF00FF00.toInt()); textSize = 14f; bar.addView(this)
        }
        input = EditText(this).apply {
            setTextColor(0xFF00FF00.toInt()); setHintTextColor(0xFF333333.toInt())
            hint = "command..."; background = null; inputType = EditorInfo.TYPE_NULL
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            bar.addView(this)
        }
        val send = Button(this).apply {
            text = "SEND"; setTextColor(0xFF000000.toInt()); setBackgroundColor(0xFF00FF00.toInt())
            textSize = 10f; setOnClickListener { sendCmd() }; bar.addView(this)
        }
        input.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { sendCmd(); true } else false }
        setContentView(root)
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

    private fun sendCmd() { val cmd = input.text.toString(); if (cmd.isNotEmpty() && shellIn != null) { try { shellIn!!.write("$cmd\n".toByteArray()); shellIn!!.flush(); input.text.clear() } catch (e: Exception) { append("Error: ${e.message}\n") } } }
    private fun append(t: String) { runOnUiThread { output.append(t); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } } }
    override fun onDestroy() { super.onDestroy(); shell?.destroy() }
}
