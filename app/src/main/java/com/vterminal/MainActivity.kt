package com.vterminal

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*

class MainActivity : AppCompatActivity() {
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private var shell: Process? = null
    private var shellIn: OutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF000000.toInt()); setPadding(2,2,2,2)
        }

        // Extra keys
        val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addKey(label: String, code: String) {
            keys.addView(Button(this).apply {
                text = label; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF1A1A1A.toInt())
                textSize = 10f; layoutParams = LinearLayout.LayoutParams(0, 44, 1f)
                setOnClickListener { shellIn?.write(code.toByteArray()); shellIn?.flush() }
            })
        }
        addKey("ESC", "\u001b"); addKey("TAB", "\t"); addKey("CTRL", "\u0003"); addKey("ALT", "\u001b")
        addKey("-", "-"); addKey("/", "/"); addKey("|", "|"); addKey("UP", "\u001b[A")
        root.addView(keys)

        // Output
        scroll = ScrollView(this).apply { root.addView(this) }
        output = TextView(this).apply {
            setTextColor(0xFF00FF00.toInt()); textSize = 12f; setPadding(6,6,6,6)
            setBackgroundColor(0xFF000000.toInt()); typeface = android.graphics.Typeface.MONOSPACE
            scroll.addView(this)
        }

        // Input bar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0xFF0D0D0D.toInt()); setPadding(4,4,4,4)
            gravity = android.view.Gravity.CENTER_VERTICAL; root.addView(this)
        }
        bar.addView(TextView(this).apply {
            text = "$ "; setTextColor(0xFF00FF00.toInt()); textSize = 14f; typeface = android.graphics.Typeface.MONOSPACE
        })
        input = EditText(this).apply {
            setTextColor(0xFF00FF00.toInt()); setHintTextColor(0xFF003300.toInt())
            background = null; hint = "command..."; textSize = 14f; typeface = android.graphics.Typeface.MONOSPACE
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { sendCmd(); true } else false }
            bar.addView(this)
        }

        setContentView(root)
        startShell()
    }

    private fun startShell() {
        append("[SHELL] Starting...\n")
        Thread {
            try {
                // استخدام proot مع Ubuntu إذا كان موجوداً
                val prootPath = "${cacheDir}/proot"
                val ubuntuPath = "${filesDir}/ubuntu"
                val pb = if (File("$ubuntuPath/bin/bash").exists()) {
                    copyAsset("proot", prootPath)
                    Runtime.getRuntime().exec("chmod 755 $prootPath").waitFor()
                    ProcessBuilder(prootPath, "-r", ubuntuPath, "-b", "/dev", "-b", "/proc", "-b", "/sys", "/bin/bash")
                } else {
                    ProcessBuilder("/system/bin/sh")
                }
                pb.redirectErrorStream(true); shell = pb.start(); shellIn = shell!!.outputStream
                Thread { shell!!.inputStream.bufferedReader().forEachLine { append("$it\n") } }.start()
                append("[SHELL] Ready.\n\n")
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

    private fun copyAsset(name: String, dest: String) {
        val f = File(dest); if (f.exists() && f.length() > 100000) return
        try { assets.open(name).use { it.copyTo(FileOutputStream(f)) } } catch (_: Exception) {}
    }

    private fun append(t: String) { runOnUiThread { output.append(t); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } } }
    override fun onDestroy() { super.onDestroy(); shell?.destroy() }
}
