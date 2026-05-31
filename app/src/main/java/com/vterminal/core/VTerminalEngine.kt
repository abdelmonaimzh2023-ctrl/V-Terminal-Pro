package com.vterminal.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.zip.*

/**
 * V-Terminal Pro Core Engine
 * المحرك الأساسي المسؤول عن جميع العمليات
 * 
 * @property context سياق التطبيق
 * @property prefs مدير الإعدادات
 * @property processManager مدير العمليات
 * @property networkManager مدير الشبكة
 * @property storageManager مدير التخزين
 * @property securityManager مدير الأمان
 */
class VTerminalEngine(private val context: Context) {
    
    // ==================== SINGLETON ====================
    companion object {
        @Volatile private var instance: VTerminalEngine? = null
        
        fun getInstance(context: Context): VTerminalEngine {
            return instance ?: synchronized(this) {
                instance ?: VTerminalEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // ==================== PREFERENCES ====================
    private val prefs: SharedPreferences = context.getSharedPreferences("vterminal_core", Context.MODE_PRIVATE)
    
    // ==================== EXECUTORS ====================
    val mainExecutor = Executors.newSingleThreadExecutor()
    val ioExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val networkExecutor = Executors.newCachedThreadPool()
    val scheduledExecutor = Executors.newScheduledThreadPool(4)
    
    // ==================== PATHS ====================
    val homeDir: File get() = File(context.filesDir, "home")
    val cacheDir: File get() = context.cacheDir
    val externalDir: File get() = context.getExternalFilesDir(null) ?: cacheDir
    val tempDir: File get() = File(cacheDir, "temp").also { it.mkdirs() }
    val pluginsDir: File get() = File(homeDir, "plugins").also { it.mkdirs() }
    val scriptsDir: File get() = File(homeDir, "scripts").also { it.mkdirs() }
    val packagesDir: File get() = File(homeDir, "packages").also { it.mkdirs() }
    val themesDir: File get() = File(homeDir, "themes").also { it.mkdirs() }
    val logsDir: File get() = File(homeDir, "logs").also { it.mkdirs() }
    
    // ==================== STATE ====================
    private val isInitialized = AtomicBoolean(false)
    private val activeProcesses = ConcurrentHashMap<String, Process>()
    private val activeSessions = ConcurrentHashMap<String, Session>()
    private val eventBus = EventBus()
    private val logger = Logger(logsDir)
    
    data class Session(
        val id: String = UUID.randomUUID().toString(),
        var process: Process? = null,
        var inputStream: OutputStream? = null,
        var outputStream: InputStream? = null,
        var startTime: Long = System.currentTimeMillis(),
        var command: String = "",
        var workingDirectory: String = ""
    )
    
    class EventBus {
        private val listeners = ConcurrentHashMap<String, MutableList<(Any) -> Unit>>()
        
        fun subscribe(event: String, listener: (Any) -> Unit) {
            listeners.getOrPut(event) { mutableListOf() }.add(listener)
        }
        
        fun publish(event: String, data: Any = Unit) {
            listeners[event]?.forEach { it(data) }
        }
        
        fun unsubscribeAll() { listeners.clear() }
    }
    
    class Logger(private val logsDir: File) {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        
        enum class Level { DEBUG, INFO, WARN, ERROR, FATAL }
        
        fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] [$level] [$tag] $message" +
                (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
            
            // كتابة إلى ملف
            try {
                val logFile = File(logsDir, "vterminal_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.log")
                logFile.parentFile?.mkdirs()
                FileWriter(logFile, true).use { it.append(logEntry + "\n") }
            } catch (_: Exception) {}
            
            // إرسال إلى EventBus
            instance?.eventBus?.publish("log", LogEntry(timestamp, level, tag, message))
        }
        
        data class LogEntry(
            val timestamp: String,
            val level: Level,
            val tag: String,
            val message: String
        )
        
        fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
        fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
        fun w(tag: String, msg: String) = log(Level.WARN, tag, msg)
        fun e(tag: String, msg: String, t: Throwable? = null) = log(Level.ERROR, tag, msg, t)
        fun f(tag: String, msg: String, t: Throwable? = null) = log(Level.FATAL, tag, msg, t)
    }
    
    // ==================== INITIALIZATION ====================
    fun initialize(): Boolean {
        if (isInitialized.get()) return true
        
        try {
            logger.i("VTerminalEngine", "Initializing V-Terminal Pro Engine v3.0...")
            
            // إنشاء المجلدات الأساسية
            listOf(homeDir, tempDir, pluginsDir, scriptsDir, packagesDir, themesDir, logsDir).forEach { it.mkdirs() }
            
            // تنظيف الملفات المؤقتة
            cleanTempFiles()
            
            // تهيئة مدير الإعدادات
            initPreferences()
            
            // فحص البيئة
            checkEnvironment()
            
            isInitialized.set(true)
            logger.i("VTerminalEngine", "Engine initialized successfully")
            return true
        } catch (e: Exception) {
            logger.e("VTerminalEngine", "Failed to initialize engine", e)
            return false
        }
    }
    
    private fun initPreferences() {
        val defaults = mapOf(
            "theme" to "neon",
            "font_size" to "12",
            "max_lines" to "1000",
            "auto_save" to "true",
            "check_updates" to "true",
            "enable_plugins" to "true",
            "enable_scripts" to "true",
            "network_timeout" to "30",
            "max_download_threads" to "4",
            "log_level" to "INFO",
            "language" to "en",
            "shell_path" to "/bin/bash",
            "proot_path" to "proot",
            "ubuntu_path" to "ubuntu"
        )
        defaults.forEach { (key, value) ->
            if (!prefs.contains(key)) prefs.edit().putString(key, value).apply()
        }
    }
    
    private fun checkEnvironment() {
        val info = StringBuilder()
        info.appendLine("=== Environment Check ===")
        info.appendLine("Android Version: ${Build.VERSION.SDK_INT}")
        info.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        info.appendLine("Model: ${Build.MODEL}")
        info.appendLine("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        info.appendLine("Available Processors: ${Runtime.getRuntime().availableProcessors()}")
        info.appendLine("Max Memory: ${Runtime.getRuntime().maxMemory() / 1048576} MB")
        info.appendLine("Free Storage: ${Environment.getDataDirectory().freeSpace / 1048576} MB")
        info.appendLine("Home Directory: ${homeDir.absolutePath}")
        info.appendLine("=========================")
        logger.i("Environment", info.toString())
    }
    
    private fun cleanTempFiles() {
        try {
            tempDir.listFiles()?.forEach { file ->
                if (file.isFile && System.currentTimeMillis() - file.lastModified() > 3600000) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }
    
    // ==================== SESSION MANAGEMENT ====================
    fun createSession(command: String = "/bin/bash", workingDir: String = homeDir.absolutePath): Session {
        val session = Session(
            command = command,
            workingDirectory = workingDir
        )
        activeSessions[session.id] = session
        logger.i("SessionManager", "Session created: ${session.id}")
        return session
    }
    
    fun executeInSession(sessionId: String, command: String): String {
        val session = activeSessions[sessionId] ?: return "Session not found"
        
        return try {
            session.inputStream?.write("$command\n".toByteArray())
            session.inputStream?.flush()
            "Command sent: $command"
        } catch (e: Exception) {
            logger.e("SessionManager", "Failed to execute command in session $sessionId", e)
            "Error: ${e.message}"
        }
    }
    
    fun closeSession(sessionId: String) {
        activeSessions[sessionId]?.process?.destroy()
        activeSessions.remove(sessionId)
        logger.i("SessionManager", "Session closed: $sessionId")
    }
    
    fun closeAllSessions() {
        activeSessions.values.forEach { it.process?.destroy() }
        activeSessions.clear()
        logger.i("SessionManager", "All sessions closed")
    }
    
    // ==================== PROCESS MANAGEMENT ====================
    fun startProcess(command: List<String>, workingDir: File = homeDir): Process? {
        return try {
            val pb = ProcessBuilder(command).apply {
                directory(workingDir)
                redirectErrorStream(true)
            }
            val process = pb.start()
            val id = UUID.randomUUID().toString()
            activeProcesses[id] = process
            logger.i("ProcessManager", "Process started: ${command.joinToString(" ")} (ID: $id)")
            process
        } catch (e: Exception) {
            logger.e("ProcessManager", "Failed to start process: ${command.joinToString(" ")}", e)
            null
        }
    }
    
    fun stopProcess(id: String): Boolean {
        return activeProcesses[id]?.let {
            it.destroy()
            activeProcesses.remove(id)
            logger.i("ProcessManager", "Process stopped: $id")
            true
        } ?: false
    }
    
    fun stopAllProcesses() {
        activeProcesses.values.forEach { it.destroy() }
        activeProcesses.clear()
        logger.i("ProcessManager", "All processes stopped")
    }
    
    // ==================== NETWORK MANAGEMENT ====================
    fun isNetworkAvailable(): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }
    
    fun ping(host: String, count: Int = 4): String {
        return try {
            val process = ProcessBuilder("/system/bin/ping", "-c", count.toString(), host)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Ping failed: ${e.message}"
        }
    }
    
    fun getPublicIP(): String {
        return try {
            val url = URL("https://api.ipify.org")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            "Unavailable"
        }
    }
    
    fun dnsLookup(host: String): List<String> {
        return try {
            InetAddress.getAllByName(host).map { it.hostAddress }
        } catch (e: Exception) {
            listOf("DNS lookup failed: ${e.message}")
        }
    }
    
    // ==================== STORAGE MANAGEMENT ====================
    fun getStorageInfo(): Map<String, Long> {
        return mapOf(
            "total" to Environment.getDataDirectory().totalSpace,
            "free" to Environment.getDataDirectory().freeSpace,
            "used" to (Environment.getDataDirectory().totalSpace - Environment.getDataDirectory().freeSpace)
        )
    }
    
    fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0
        return if (dir.isDirectory) {
            dir.listFiles()?.sumOf { getDirectorySize(it) } ?: 0
        } else {
            dir.length()
        }
    }
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.2f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
    
    // ==================== SECURITY ====================
    fun calculateChecksum(file: File, algorithm: String = "SHA-256"): String {
        return try {
            val digest = java.security.MessageDigest.getInstance(algorithm)
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var len: Int
                while (input.read(buffer).also { len = it } != -1) {
                    digest.update(buffer, 0, len)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    fun encrypt(data: String, key: String): String {
        // AES encryption implementation
        return try {
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = javax.crypto.spec.SecretKeySpec(key.toByteArray().take(32).toByteArray(), "AES")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))
        } catch (e: Exception) {
            "Encryption failed: ${e.message}"
        }
    }
    
    // ==================== SCHEDULER ====================
    fun scheduleTask(delayMs: Long, task: () -> Unit): ScheduledFuture<*> {
        return scheduledExecutor.schedule({
            try { task() }
            catch (e: Exception) { logger.e("Scheduler", "Task failed", e) }
        }, delayMs, TimeUnit.MILLISECONDS)
    }
    
    fun scheduleRepeatingTask(initialDelayMs: Long, periodMs: Long, task: () -> Unit): ScheduledFuture<*> {
        return scheduledExecutor.scheduleAtFixedRate({
            try { task() }
            catch (e: Exception) { logger.e("Scheduler", "Repeating task failed", e) }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS)
    }
    
    // ==================== COMPRESSION ====================
    fun compressDirectory(source: File, dest: File, format: String = "zip"): Boolean {
        return try {
            when (format.lowercase()) {
                "zip" -> {
                    ZipOutputStream(FileOutputStream(dest)).use { zos ->
                        source.walkTopDown().forEach { file ->
                            if (file != source) {
                                val entryName = file.relativeTo(source).path
                                if (file.isDirectory) {
                                    zos.putNextEntry(ZipEntry("$entryName/"))
                                } else {
                                    zos.putNextEntry(ZipEntry(entryName))
                                    FileInputStream(file).use { it.copyTo(zos) }
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            logger.e("Compression", "Failed to compress directory", e)
            false
        }
    }
    
    // ==================== UTILITIES ====================
    fun copyFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            logger.e("Utils", "Failed to copy file", e)
            false
        }
    }
    
    fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            file.delete()
        } catch (e: Exception) {
            logger.e("Utils", "Failed to delete file", e)
            false
        }
    }
    
    fun generateRandomString(length: Int = 32): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
    
    fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }
    
    // ==================== CLEANUP ====================
    fun shutdown() {
        logger.i("VTerminalEngine", "Shutting down engine...")
        closeAllSessions()
        stopAllProcesses()
        mainExecutor.shutdown()
        ioExecutor.shutdown()
        networkExecutor.shutdown()
        scheduledExecutor.shutdown()
        eventBus.unsubscribeAll()
        logger.i("VTerminalEngine", "Engine shutdown complete")
    }
}
