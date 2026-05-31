package com.vterminal.ui

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*

class MediaPlayerActivity : AppCompatActivity() {
    private var player: MediaPlayer? = null
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        
        statusText = TextView(this).apply { text = "No media loaded"; setTextColor(0xFFE0E0E0.toInt()) }
        root.addView(statusText)
        
        val playBtn = Button(this).apply { text = "PLAY"; setOnClickListener { player?.start() } }
        root.addView(playBtn)
        
        val pauseBtn = Button(this).apply { text = "PAUSE"; setOnClickListener { player?.pause() } }
        root.addView(pauseBtn)
        
        val stopBtn = Button(this).apply { text = "STOP"; setOnClickListener { player?.stop(); player?.reset() } }
        root.addView(stopBtn)
        
        setContentView(root)
    }

    fun loadMedia(uri: Uri) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(this@MediaPlayerActivity, uri)
            prepare()
            statusText.text = "Loaded: ${uri.lastPathSegment}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
