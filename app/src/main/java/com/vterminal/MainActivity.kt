package com.vterminal

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vterminal.engine.*
import java.io.*

class MainActivity : AppCompatActivity() {
    private lateinit var terminalOutput: TextView
    private lateinit var terminalInput: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var cancelBtn: Button
    private lateinit var progressLayout: LinearLayout
    private lateinit var themeBtn: Button

    private var shellProcess: Process? = null
    private var shellInput: OutputStream? = null
    private var shellOutput: Thread? = null
    private var extractThread: Thread? = null
    private var selectedImageUri: Uri? = null
    private val extractionEngine = ExtractionEngine(this)
    private var currentThemeIndex = 0
    private val themeList = ThemeEngine.getThemeNames()

    private val permissionRequestCode = 100
    private val pickFileCode = 102
    private val channelId = "terminal_session"
    private val notificationId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalOutput = findViewById(R.id.terminalOutput)
        terminalInput = findViewById(R.id.terminalInput)
        scrollView = findViewById(R.id.scrollView)
        val sendBtn: Button = findViewById(R.id.sendBtn)
        val settingsBtn: Button = findViewById(R.id.settingsBtn)
        val browseBtn: Button = findViewById(R.id.browseBtn)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        cancelBtn = findViewById(R.id.cancelBtn)
        progressLayout = findViewById(R.id.progressLayout)
        themeBtn = findViewById(R.id.themeBtn)

        sendBtn.setOnClickListener { sendCommand() }
        settingsBtn.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        cancelBtn.setOnClickListener { cancelExtraction() }
        browseBtn.setOnClickListener { browseImageFile() }
        themeBtn.setOnClickListener { cycleTheme() }
        terminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) { sendCommand(); true } else false
        }

        createNotificationChannel()
        requestPermissions()
        applyTheme()
    }

    private fun cycleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % themeList.size
        ThemeEngine.setTheme(themeList[currentThemeIndex])
        applyTheme()
        appendOutput("[THEME] Switched to: ${ThemeEngine.getCurrentTheme().name}\n")
    }

    private fun applyTheme() {
        val theme = ThemeEngine.getCurrentTheme()
        terminalOutput.apply {
            setTextColor(theme.textColor)
            textSize = theme.fontSize
            setBackgroundColor(theme.bgColor)
        }
        terminalInput.apply {
            setTextColor(theme.textColor)
            setHintTextColor(darken(theme.textColor, 0.4f))
        }
        scrollView.setBackgroundColor(theme.bgColor)
        progressLayout.setBackgroundColor(theme.progressBgColor)
        progressText.setTextColor(theme.accentColor)
        progressBar.progressTintList = android.content.res.ColorStateList.valueOf(theme.progressFgColor)
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private fun browseImageFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, pickFileCode)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val ungranted = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (ungranted.isNotEmpty()) ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), permissionRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFileCode && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            appendOutput("[FILE] Selected: ${data.data?.lastPathSegment}\n")
            initUbuntu()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channelId, "Terminal Session", NotificationManager.IMPORTANCE_LOW))
    }

    private fun showNotification() {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(notificationId, NotificationCompat.Builder(this, channelId).setContentTitle("V-Terminal Pro").setContentText("Ubuntu session is running").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).setContentIntent(pi).build())
    }

    private fun showProgress(text: String, progress: Int) { runOnUiThread { progressLayout.visibility = View.VISIBLE; progressText.text = "$text ($progress%)"; progressBar.progress = progress } }
    private fun hideProgress() { runOnUiThread { progressLayout.visibility = View.GONE } }
    private fun cancelExtraction() { extractionEngine.cancel(); hideProgress(); appendOutput("[CANCEL] Extraction cancelled.\n") }

    private fun initUbuntu() {
        val uri = selectedImageUri ?: return
        appendOutput("[INIT] Starting extraction engine...\n")
        
        extractThread = Thread {
            val cacheDir = cacheDir
            val prootPath = "${cacheDir}/proot"
            val ubuntuPath = "${filesDir}/ubuntu"
            
            try {
                // تحضير proot
                if (!File(prootPath).exists()) copyAsset("proot", prootPath)
                Runtime.getRuntime().exec("chmod 755 $prootPath").waitFor()

                val success = extractionEngine.extract(
                    sourceUri = uri,
                    destPath = ubuntuPath,
                    onProgress = { progress -> showProgress(progress.message, progress.percent) },
                    onLine = { line -> appendOutput("$line\n") }
                )

                hideProgress()
                if (success && File("$ubuntuPath/bin/bash").exists()) {
                    appendOutput("[SHELL] Starting bash...\n")
                    val pb = ProcessBuilder(prootPath, "-r", ubuntuPath, "-b", "/dev", "-b", "/proc", "-b", "/sys", "/bin/bash")
                    pb.redirectErrorStream(true); shellProcess = pb.start(); shellInput = shellProcess!!.outputStream
                    runOnUiThread { showNotification() }
                    shellOutput = Thread { shellProcess!!.inputStream.bufferedReader().forEachLine { appendOutput("$it\n") } }.apply { start() }
                    appendOutput("[SHELL] Ubuntu Shell Ready.\n\n")
                } else {
                    appendOutput("[ERROR] bash not found after extraction.\n")
                }
            } catch (e: Exception) {
                hideProgress()
                appendOutput("[ERROR] ${e.message}\n")
            }
        }.apply { start() }
    }

    private fun copyAsset(name: String, dest: String) { val f = File(dest); if (f.exists() && f.length() > 100000) return; try { assets.open(name).use { input -> FileOutputStream(f).use { output -> input.copyTo(output); output.flush(); output.fd.sync() } } } catch (e: Exception) {} }
    private fun sendCommand() { val cmd = terminalInput.text.toString(); if (cmd.isNotEmpty() && shellInput != null) { try { shellInput!!.write("$cmd\n".toByteArray()); shellInput!!.flush(); terminalInput.text.clear() } catch (e: Exception) { appendOutput("[SEND ERROR] ${e.message}\n") } } }
    private fun appendOutput(text: String) { runOnUiThread { terminalOutput.append(text); scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) } } }
    override fun onDestroy() { super.onDestroy(); shellProcess?.destroy(); shellOutput?.interrupt() }
}
