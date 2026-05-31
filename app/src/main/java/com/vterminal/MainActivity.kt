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
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.*

class MainActivity : AppCompatActivity() {
    private lateinit var terminalOutput: TextView
    private lateinit var terminalInput: EditText
    private lateinit var scrollView: ScrollView

    private var shellProcess: Process? = null
    private var shellInput: OutputStream? = null
    private var shellOutput: Thread? = null

    private val permissionRequestCode = 100
    private val manageStorageCode = 101
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

        sendBtn.setOnClickListener { sendCommand() }
        settingsBtn.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        terminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) { sendCommand(); true } else false
        }

        createNotificationChannel()
        requestAllPermissions()
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT); permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val ungranted = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (ungranted.isNotEmpty()) ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), permissionRequestCode)
        else checkManageStorage()
    }

    private fun checkManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:$packageName") }, manageStorageCode)
            } else initUbuntu()
        } else initUbuntu()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) checkManageStorage()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == manageStorageCode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Full storage access granted", Toast.LENGTH_SHORT).show(); initUbuntu()
            } else Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channelId, "Terminal Session", NotificationManager.IMPORTANCE_LOW))
    }

    private fun showNotification() {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, channelId).setContentTitle("V-Terminal Pro").setContentText("Ubuntu session is running").setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).setContentIntent(pi).build()
        getSystemService(NotificationManager::class.java).notify(notificationId, n)
    }

    private fun initUbuntu() {
        appendOutput("\n[INIT] Initializing Ubuntu...\n")
        Thread {
            try {
                val cacheDir = cacheDir
                val prootPath = "${cacheDir}/proot"
                val busyboxPath = "${cacheDir}/busybox"
                val ubuntuPath = "${filesDir}/ubuntu"
                val imagePath = "${Environment.getExternalStorageDirectory()}/V-Viewer/rootfs-full.tar"

                copyAsset("proot", prootPath)
                copyAsset("busybox", busyboxPath)
                Runtime.getRuntime().exec("sync").waitFor()
                Thread.sleep(200)
                Runtime.getRuntime().exec("chmod 755 $prootPath").waitFor()
                Runtime.getRuntime().exec("chmod 755 $busyboxPath").waitFor()
                File(prootPath).setExecutable(true, false)
                File(busyboxPath).setExecutable(true, false)

                val ubuntuDir = File(ubuntuPath)
                if (!ubuntuDir.exists() || !File("$ubuntuPath/bin/bash").exists()) {
                    appendOutput("[EXTRACT] Extracting Ubuntu...\n")
                    ubuntuDir.mkdirs()
                    val pb = ProcessBuilder(busyboxPath, "tar", "-xzf", imagePath, "-C", ubuntuPath)
                    pb.redirectErrorStream(true)
                    val p = pb.start()
                    p.inputStream.bufferedReader().forEachLine { appendOutput("$it\n") }
                    p.waitFor()
                    Runtime.getRuntime().exec("sync").waitFor()
                    Thread.sleep(500)
                    appendOutput("[EXTRACT] Done.\n")
                }

                appendOutput("[SHELL] Starting bash...\n")
                val pb = ProcessBuilder(prootPath, "-r", ubuntuPath, "-b", "/dev", "-b", "/proc", "-b", "/sys", "/bin/bash")
                pb.redirectErrorStream(true)
                shellProcess = pb.start()
                shellInput = shellProcess!!.outputStream
                runOnUiThread { showNotification() }
                shellOutput = Thread { shellProcess!!.inputStream.bufferedReader().forEachLine { appendOutput("$it\n") } }.apply { start() }
                appendOutput("[SHELL] Ubuntu Shell Ready.\n\n")
            } catch (e: Exception) { appendOutput("[ERROR] ${e.message}\n") }
        }.start()
    }

    private fun copyAsset(name: String, dest: String) {
        val f = File(dest); if (f.exists() && f.length() > 100000) return
        try { assets.open(name).use { input -> FileOutputStream(f).use { output -> input.copyTo(output); output.flush(); output.fd.sync() } } }
        catch (e: Exception) { appendOutput("[COPY ERROR] ${e.message}\n") }
    }

    private fun sendCommand() {
        val cmd = terminalInput.text.toString()
        if (cmd.isNotEmpty() && shellInput != null) {
            try { shellInput!!.write("$cmd\n".toByteArray()); shellInput!!.flush(); terminalInput.text.clear() }
            catch (e: Exception) { appendOutput("[SEND ERROR] ${e.message}\n") }
        }
    }

    private fun appendOutput(text: String) { runOnUiThread { terminalOutput.append(text); scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) } } }

    override fun onDestroy() { super.onDestroy(); shellProcess?.destroy(); shellOutput?.interrupt() }
}
