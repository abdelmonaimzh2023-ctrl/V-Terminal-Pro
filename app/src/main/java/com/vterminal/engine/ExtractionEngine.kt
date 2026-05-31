package com.vterminal.engine

import android.content.Context
import android.net.Uri
import java.io.*
import java.util.zip.*

class ExtractionEngine(private val context: Context) {
    
    enum class ArchiveType {
        TAR, TAR_GZ, TAR_XZ, TAR_BZ2, ZIP, RAR, SEVEN_ZIP, UNKNOWN
    }
    
    data class ExtractionProgress(
        val percent: Int,
        val message: String,
        val currentFile: String = ""
    )
    
    private var cancelFlag = false
    private var process: Process? = null
    
    fun detectType(fileName: String): ArchiveType {
        return when {
            fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz") -> ArchiveType.TAR_GZ
            fileName.endsWith(".tar.xz") || fileName.endsWith(".txz") -> ArchiveType.TAR_XZ
            fileName.endsWith(".tar.bz2") || fileName.endsWith(".tbz2") -> ArchiveType.TAR_BZ2
            fileName.endsWith(".tar") -> ArchiveType.TAR
            fileName.endsWith(".zip") -> ArchiveType.ZIP
            fileName.endsWith(".rar") -> ArchiveType.RAR
            fileName.endsWith(".7z") -> ArchiveType.SEVEN_ZIP
            else -> ArchiveType.UNKNOWN
        }
    }
    
    fun extract(
        sourceUri: Uri,
        destPath: String,
        onProgress: (ExtractionProgress) -> Unit,
        onLine: (String) -> Unit
    ): Boolean {
        cancelFlag = false
        val cacheDir = context.cacheDir
        val busybox = "${cacheDir}/busybox"
        
        try {
            // نسخ busybox
            if (!File(busybox).exists()) {
                context.assets.open("busybox").use { it.copyTo(FileOutputStream(busybox)) }
                Runtime.getRuntime().exec("chmod 755 $busybox").waitFor()
            }
            
            // نسخ المصدر إلى cache
            val tempFile = File("${cacheDir}/source_archive")
            onProgress(ExtractionProgress(0, "Copying archive..."))
            context.contentResolver.openInputStream(sourceUri)!!.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buf = ByteArray(65536)
                    var len: Int
                    while (input.read(buf).also { len = it } != -1 && !cancelFlag) {
                        output.write(buf, 0, len)
                    }
                }
            }
            
            if (cancelFlag) { tempFile.delete(); return false }
            onProgress(ExtractionProgress(20, "Analyzing archive..."))
            
            val type = detectType(sourceUri.lastPathSegment ?: "archive.tar")
            
            // فك الضغط حسب النوع
            val result = when (type) {
                ArchiveType.TAR_GZ -> extractTarGz(tempFile, destPath, busybox, onProgress, onLine)
                ArchiveType.TAR_XZ -> extractTarXz(tempFile, destPath, busybox, onProgress, onLine)
                ArchiveType.TAR_BZ2 -> extractTarBz2(tempFile, destPath, busybox, onProgress, onLine)
                ArchiveType.TAR -> extractTar(tempFile, destPath, busybox, onProgress, onLine)
                ArchiveType.ZIP -> extractZip(tempFile, destPath, onProgress, onLine)
                ArchiveType.RAR -> extractRar(tempFile, destPath, busybox, onProgress, onLine)
                ArchiveType.SEVEN_ZIP -> extract7z(tempFile, destPath, busybox, onProgress, onLine)
                else -> {
                    // محاولة كل الطرق
                    tryExtractAll(tempFile, destPath, busybox, onProgress, onLine)
                }
            }
            
            tempFile.delete()
            return result
        } catch (e: Exception) {
            onLine("[ERROR] ${e.message}")
            return false
        }
    }
    
    private fun extractTarGz(file: File, dest: String, busybox: String, 
                              onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        onProgress(ExtractionProgress(30, "Decompressing gzip..."))
        
        // طريقة 1: gunzip منفصل
        val tarFile = File("${file.parent}/archive.tar")
        try {
            val gunzip = ProcessBuilder(busybox, "gunzip", "-c", file.absolutePath)
                .redirectOutput(tarFile)
                .start()
            gunzip.waitFor()
            
            if (tarFile.length() > 0) {
                return extractTar(tarFile, dest, busybox, onProgress, onLine)
            }
        } catch (e: Exception) {
            onLine("[WARN] gunzip failed, trying tar -xzf")
        }
        
        // طريقة 2: tar -xzf مباشرة
        return extractWithTarCommand(file, dest, busybox, "-xzf", onProgress, onLine)
    }
    
    private fun extractTarXz(file: File, dest: String, busybox: String,
                              onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        return extractWithTarCommand(file, dest, busybox, "-xJf", onProgress, onLine)
    }
    
    private fun extractTarBz2(file: File, dest: String, busybox: String,
                               onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        return extractWithTarCommand(file, dest, busybox, "-xjf", onProgress, onLine)
    }
    
    private fun extractTar(file: File, dest: String, busybox: String,
                            onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        return extractWithTarCommand(file, dest, busybox, "-xf", onProgress, onLine)
    }
    
    private fun extractWithTarCommand(file: File, dest: String, busybox: String, flag: String,
                                       onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        onProgress(ExtractionProgress(40, "Extracting files..."))
        try {
            val cmd = listOf(busybox, "tar", flag, file.absolutePath, "-C", dest)
            val pb = ProcessBuilder(cmd).redirectErrorStream(true)
            process = pb.start()
            
            var count = 0
            process!!.inputStream.bufferedReader().forEachLine { line ->
                if (cancelFlag) return@forEachLine
                count++
                onLine(line)
                if (count % 100 == 0) {
                    onProgress(ExtractionProgress(40 + (count * 50 / 50000).coerceAtMost(50), 
                        "Extracting... $count files", line))
                }
            }
            
            val exitCode = process!!.waitFor()
            onProgress(ExtractionProgress(95, "Extraction exit code: $exitCode"))
            return File(dest, "bin/bash").exists() || File(dest, "bin/sh").exists()
        } catch (e: Exception) {
            onLine("[FATAL] ${e.message}")
            return false
        }
    }
    
    private fun extractZip(file: File, dest: String,
                            onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        onProgress(ExtractionProgress(30, "Extracting ZIP..."))
        try {
            val zis = ZipInputStream(FileInputStream(file))
            var entry = zis.nextEntry
            var count = 0
            while (entry != null && !cancelFlag) {
                count++
                val entryFile = File(dest, entry.name)
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { zis.copyTo(it) }
                }
                if (count % 50 == 0) {
                    onProgress(ExtractionProgress(30 + (count * 60 / 10000).coerceAtMost(60), 
                        "Extracting... $count files", entry.name))
                }
                entry = zis.nextEntry
            }
            zis.close()
            return File(dest, "bin/bash").exists()
        } catch (e: Exception) {
            onLine("[ZIP ERROR] ${e.message}")
            return false
        }
    }
    
    private fun extractRar(file: File, dest: String, busybox: String,
                            onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        return extractWithTarCommand(file, dest, busybox, "-xf", onProgress, onLine)
    }
    
    private fun extract7z(file: File, dest: String, busybox: String,
                           onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        return extractWithTarCommand(file, dest, busybox, "-xf", onProgress, onLine)
    }
    
    private fun tryExtractAll(file: File, dest: String, busybox: String,
                               onProgress: (ExtractionProgress) -> Unit, onLine: (String) -> Unit): Boolean {
        val methods = listOf("-xf", "-xzf", "-xJf", "-xjf")
        for (method in methods) {
            if (cancelFlag) return false
            onLine("[TRY] Attempting tar $method...")
            File(dest).mkdirs()
            // حذف المحتويات السابقة إذا فشلت
            if (extractWithTarCommand(file, dest, busybox, method, onProgress, onLine)) {
                return true
            }
        }
        return false
    }
    
    fun cancel() {
        cancelFlag = true
        process?.destroy()
    }
}
