package com.vterminal

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var terminalOutput: TextView
    private lateinit var terminalInput: EditText
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalOutput = findViewById(R.id.terminalOutput)
        terminalInput = findViewById(R.id.terminalInput)
        scrollView = findViewById(R.id.scrollView)
        val sendBtn: Button = findViewById(R.id.sendBtn)

        sendBtn.setOnClickListener { sendCommand() }
        terminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendCommand()
                true
            } else false
        }

        appendOutput("V-Terminal Pro v1.0\n")
        appendOutput("Ubuntu Shell Ready.\n\n")
    }

    private fun sendCommand() {
        val cmd = terminalInput.text.toString()
        if (cmd.isNotEmpty()) {
            appendOutput("root@ubuntu:~# $cmd\n")
            appendOutput("[OK] Command received: $cmd\n")
            terminalInput.text.clear()
        }
    }

    private fun appendOutput(text: String) {
        runOnUiThread {
            terminalOutput.append(text)
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
