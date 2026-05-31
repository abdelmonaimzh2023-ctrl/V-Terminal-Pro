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
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.*

class MainActivity : AppCompatActivity() {
    private lateinit var terminalOutput: TextView
    private lateinit var terminalInput: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var cancelBtn: Button
    private lateinit var progressLayout: LinearLayout

    private var shellProcess: Process? = null
    private var shellInput: OutputStream? = null
    private var shellOutput: Thread? = null
    private var extractThread: Thread? = null
    private var extractProcess: Process? = null
    private var selectedImageUri: Uri? = null

    private val permissionRequestCode = 100
    private val manageStorageCode = 101
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

        sendBtn.setOnClickListener { sendCommand() }
        settingsBtn.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        cancelBtn.setOnClickListener { cancelExtraction() }
        browseBtn.setOnClickListener { browseImageFile() }
        terminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) { sendCommand(); true } else false
        }

        createNotificationChannel()
        requestAllPermissions()
    }

    private fun browseImageFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, pickFileCode)
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET)
        val ungranted = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (ungranted.isNotEmpty()) ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), permissionRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFileCode && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            appendOutput("[FILE] Selected\n")
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
    private fun cancelExtraction() { extractThread?.interrupt(); extractProcess?.destroy(); hideProgress(); appendOutput("[CANCEL] Extraction cancelled.\n") }

    private fun initUbuntu() {
        appendOutput("[INIT] Initializing Ubuntu...\n")
        extractThread = Thread {
            try {
                val cacheDir = cacheDir
                val prootPath = "${cacheDir}/proot"
                val busyboxPath = "${cacheDir}/busybox"
                val ubuntuPath = "${filesDir}/ubuntu"

                copyAsset("proot", prootPath); copyAsset("busybox", busyboxPath)
                Runtime.getRuntime().exec("sync").waitFor(); Thread.sleep(200)
                Runtime.getRuntime().exec("chmod 755 $prootPath").waitFor(); Runtime.getRuntime().exec("chmod 755 $busyboxPath").waitFor()

                val ubuntuDir = File(ubuntuPath)
                if (!ubuntuDir.exists() || !File("$ubuntuPath/bin/bash").exists()) {
                    val uri = selectedImageUri ?: run {
                        val defaultImage = File("${Environment.getExternalStorageDirectory()}/V-Viewer/arm-rootfs.tar.gz")
                        if (defaultImage.exists()) Uri.fromFile(defaultImage) else {
                            appendOutput("[ERROR] No image found. Use [BROWSE] to select.\n"); hideProgress(); return@Thread
                        }
                    }

                    val tempImage = File("${cacheDir}/archive.tar.gz")
                    appendOutput("[COPY] Copying archive...\n")
                    showProgress("Copying...", 0)
                    contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(tempImage).use { output -> input.copyTo(output) } }
                    appendOutput("[COPY] Done.\n")

                    appendOutput("[GUNZIP] Decompressing gzip...\n")
                    showProgress("Decompressing...", 15)
                    val gunzipPb = ProcessBuilder(busyboxPath, "gunzip", "-f", tempImage.absolutePath)
                    gunzipPb.redirectErrorStream(true)
                    gunzipPb.start().waitFor()
                    val tarFile = File("${cacheDir}/archive.tar")
                    appendOutput("[GUNZIP] Done.\n")

                    ubuntuDir.mkdirs()
                    appendOutput("[EXTRACT] Extracting tar...\n")
                    showProgress("Extracting...", 30)
                    val extractPb = ProcessBuilder(busyboxPath, "tar", "-xf", tarFile.absolutePath, "-C", ubuntuPath)
                    extractPb.redirectErrorStream(true)
                    extractProcess = extractPb.start()
                    extractProcess!!.inputStream.bufferedReader().forEachLine { appendOutput("$it\n") }
                    extractProcess!!.waitFor()
                    tarFile.delete()
                }

                val bashExists = File("$ubuntuPath/bin/bash").exists()
                appendOutput("[VERIFY] bash: $bashExists\n")
                if (!bashExists) { hideProgress(); appendOutput("[ERROR] bash not found.\n"); return@Thread }

                hideProgress()
                appendOutput("[SHELL] Starting bash...\n")
                val pb = ProcessBuilder(prootPath, "-r", ubuntuPath, "-b", "/dev", "-b", "/proc", "-b", "/sys", "/bin/bash")
                pb.redirectErrorStream(true); shellProcess = pb.start(); shellInput = shellProcess!!.outputStream
                runOnUiThread { showNotification() }
                shellOutput = Thread { shellProcess!!.inputStream.bufferedReader().forEachLine { appendOutput("$it\n") } }.apply { start() }
                appendOutput("[SHELL] Ubuntu Shell Ready.\n\n")
            } catch (e: Exception) { hideProgress(); appendOutput("[ERROR] ${e.message}\n") }
        }.apply { start() }
    }

    private fun copyAsset(name: String, dest: String) { val f = File(dest); if (f.exists() && f.length() > 100000) return; try { assets.open(name).use { input -> FileOutputStream(f).use { output -> input.copyTo(output); output.flush(); output.fd.sync() } } } catch (e: Exception) {} }
    private fun sendCommand() { val cmd = terminalInput.text.toString(); if (cmd.isNotEmpty() && shellInput != null) { try { shellInput!!.write("$cmd\n".toByteArray()); shellInput!!.flush(); terminalInput.text.clear() } catch (e: Exception) { appendOutput("[SEND ERROR] ${e.message}\n") } } }
    private fun appendOutput(text: String) { runOnUiThread { terminalOutput.append(text); scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) } } }
    override fun onDestroy() { super.onDestroy(); shellProcess?.destroy(); shellOutput?.interrupt() }
}
