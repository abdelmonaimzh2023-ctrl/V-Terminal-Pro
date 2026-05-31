package com.vterminal.ui

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*

class FileManagerActivity : AppCompatActivity() {
    private lateinit var fileList: LinearLayout
    private lateinit var pathText: TextView
    private var currentPath = Environment.getExternalStorageDirectory().absolutePath

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        
        pathText = TextView(this).apply { text = currentPath; setTextColor(0xFF00FFCC.toInt()); textSize = 14f }
        root.addView(pathText)
        
        fileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(fileList)
        
        scroll.addView(root)
        setContentView(scroll)
        loadFiles(currentPath)
    }

    private fun loadFiles(path: String) {
        currentPath = path
        pathText.text = path
        fileList.removeAllViews()
        
        // زر العودة
        if (path != "/") {
            addFileItem(".. [UP]", File(path).parent ?: "/", true)
        }
        
        val files = File(path).listFiles()?.sortedBy { it.isDirectory } ?: return
        for (file in files) {
            addFileItem(file.name, file.absolutePath, file.isDirectory)
        }
    }

    private fun addFileItem(name: String, path: String, isDir: Boolean) {
        val btn = Button(this).apply {
            text = if (isDir) "[DIR] $name" else "[FILE] $name"
            setTextColor(if (isDir) 0xFFFFCC00.toInt() else 0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A1A2E.toInt())
            textSize = 11f
            setOnClickListener { if (isDir) loadFiles(path) else openFile(path) }
        }
        fileList.addView(btn)
    }

    private fun openFile(path: String) {
        Toast.makeText(this, "Opening: $path", Toast.LENGTH_SHORT).show()
        // يمكن إضافة عارض نصوص هنا
    }
}
