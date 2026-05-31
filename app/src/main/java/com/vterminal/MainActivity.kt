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
    private val pickFileCode = 102
    private val channelId = "terminal_session"
    private val notificationId = 1

    private val shellPaths = listOf("/bin/bash", "/usr/bin/bash", "/bin/sh", "/usr/bin/sh")

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
        requestPermissions()
    }

    private fun browseImageFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gzip", "application/x-xz", "application/zip", "application/x-tar"))
        }
        startActivityForResult(intent, pickFileCode)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFileCode && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            appendOutput("[READY] File selected: ${selectedImageUri?.lastPathSegment}\n")
            initUbuntu()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channelId, "Terminal Session", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun showNotification() {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("V-Terminal Pro")
            .setContentText("Ubuntu session is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(notificationId, notif)
    }

    private fun showProgress(text: String, progress: Int) {
        runOnUiThread {
            progressLayout.visibility = View.VISIBLE
            progressText.text = "$text ($progress%)"
            progressBar.progress = progress
        }
    }

    private fun hideProgress() {
        runOnUiThread { progressLayout.visibility = View.GONE }
    }

    private fun cancelExtraction() {
        extractThread?.interrupt()
        extractProcess?.destroy()
        hideProgress()
        appendOutput("[CANCEL] Extraction cancelled.\n")
    }

    private fun findShell(rootPath: String): String? {
        for (path in shellPaths) {
            val fullPath = "$rootPath/$path"
            val file = File(fullPath)
            if (file.exists()) {
                appendOutput("[FIND] Shell found: $path (${file.length()} bytes)\n")
                return path
            }
        }
        return null
    }

    private fun getExtractCommand(fileName: String): String {
        return when {
            fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz") -> "tar -xzf"
            fileName.endsWith(".tar.xz") || fileName.endsWith(".txz") -> "tar -xJf"
            fileName.endsWith(".tar.bz2") || fileName.endsWith(".tbz2") -> "tar -xjf"
            fileName.endsWith(".tar") -> "tar -xf"
            fileName.endsWith(".zip") -> "unzip -o"
            else -> "tar -xzf"
        }
    }

    private fun initUbuntu() {
        appendOutput("[INIT] Starting extraction pipeline...\n")
        extractThread = Thread {
            try {
                val cacheDir = cacheDir
                val prootPath = "${cacheDir}/proot"
                val busyboxPath = "${cacheDir}/busybox"
                val ubuntuPath = "${filesDir}/ubuntu"

                // 1. تجهيز الأدوات
                copyAsset("proot", prootPath)
                copyAsset("busybox", busyboxPath)
                Runtime.getRuntime().exec("sync").waitFor()
                Thread.sleep(300)
                Runtime.getRuntime().exec("chmod 755 $prootPath").waitFor()
                Runtime.getRuntime().exec("chmod 755 $busyboxPath").waitFor()
                showProgress("Tools ready", 5)

                val ubuntuDir = File(ubuntuPath)
                val existingShell = findShell(ubuntuPath)

                if (existingShell == null) {
                    val uri = selectedImageUri ?: run {
                        appendOutput("[ERROR] No file selected.\n")
                        hideProgress()
                        return@Thread
                    }

                    val fileName = uri.lastPathSegment ?: "archive.tar"
                    val tempImage = File("${cacheDir}/archive")

                    // 2. نسخ الملف داخلياً
                    appendOutput("[COPY] Copying archive to internal storage...\n")
                    showProgress("Copying archive...", 10)
                    val totalSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                    var copied = 0L
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempImage).use { output ->
                            val buf = ByteArray(65536)
                            var len: Int
                            while (input.read(buf).also { len = it } != -1 && !Thread.currentThread().isInterrupted) {
                                output.write(buf, 0, len)
                                copied += len
                                if (totalSize > 0) {
                                    val pct = (copied * 20 / totalSize).toInt().coerceIn(10, 25)
                                    showProgress("Copying... ${copied / 1048576}MB / ${totalSize / 1048576}MB", pct)
                                }
                            }
                        }
                    }
                    appendOutput("[COPY] Copy complete (${copied / 1048576}MB).\n")

                    // 3. انتظار للتأكد من إغلاق الملف
                    Runtime.getRuntime().exec("sync").waitFor()
                    Thread.sleep(500)

                    // 4. فك الضغط
                    ubuntuDir.mkdirs()
                    val extractCmd = getExtractCommand(fileName)
                    appendOutput("[EXTRACT] Using command: $extractCmd\n")
                    showProgress("Extracting...", 30)

                    val cmdParts = extractCmd.split(" ")
                    val fullCmd = mutableListOf(busyboxPath)
                    fullCmd.addAll(cmdParts)
                    fullCmd.add(tempImage.absolutePath)
                    fullCmd.add("-C")
                    fullCmd.add(ubuntuPath)

                    val extractPb = ProcessBuilder(fullCmd).redirectErrorStream(true)
                    extractProcess = extractPb.start()
                    var count = 0
                    extractProcess?.inputStream?.bufferedReader()?.forEachLine { line ->
                        if (Thread.currentThread().isInterrupted) return@forEachLine
                        count++
                        if (count % 100 == 0) {
                            appendOutput("$line\n")
                            showProgress("Extracting... $count files", 30 + (count * 60 / 50000).coerceAtMost(60))
                        }
                    }
                    val exitCode = extractProcess?.waitFor() ?: -1
                    tempImage.delete()
                    appendOutput("[EXTRACT] Extraction complete (exit code: $exitCode).\n")

                    // 5. انتظار للتأكد من كتابة جميع الملفات
                    Runtime.getRuntime().exec("sync").waitFor()
                    Thread.sleep(500)
                }

                // 6. البحث عن shell بعد فك الضغط
                val shellPath = findShell(ubuntuPath)
                if (shellPath == null) {
                    hideProgress()
                    appendOutput("[ERROR] No shell found. Archive may be invalid.\n")
                    appendOutput("[ERROR] Tried paths: ${shellPaths.joinToString()}\n")
                    return@Thread
                }

                hideProgress()
                appendOutput("[SHELL] Launching: $shellPath\n")
                val pb = ProcessBuilder(prootPath, "-r", ubuntuPath, "-b", "/dev", "-b", "/proc", "-b", "/sys", shellPath)
                pb.redirectErrorStream(true)
                shellProcess = pb.start()
                shellInput = shellProcess?.outputStream
                runOnUiThread { showNotification() }
                shellOutput = Thread {
                    shellProcess?.inputStream?.bufferedReader()?.forEachLine { appendOutput("$it\n") }
                }.apply { start() }
                appendOutput("[SHELL] Ubuntu Shell Ready.\n\n")
            } catch (e: Exception) {
                hideProgress()
                appendOutput("[ERROR] ${e.message}\n")
            }
        }.apply { start() }
    }

    private fun copyAsset(name: String, dest: String) {
        val f = File(dest)
        if (f.exists() && f.length() > 100000) return
        try {
            assets.open(name).use { input ->
                FileOutputStream(f).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            appendOutput("[COPY ERROR] $name: ${e.message}\n")
        }
    }

    private fun sendCommand() {
        val cmd = terminalInput.text.toString()
        if (cmd.isNotEmpty() && shellInput != null) {
            try {
                shellInput?.write("$cmd\n".toByteArray())
                shellInput?.flush()
                terminalInput.text.clear()
            } catch (e: Exception) {
                appendOutput("[SEND ERROR] ${e.message}\n")
            }
        }
    }

    private fun appendOutput(text: String) {
        runOnUiThread {
            terminalOutput.append(text)
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shellProcess?.destroy()
        shellOutput?.interrupt()
    }
}
