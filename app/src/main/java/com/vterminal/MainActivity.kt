package com.vterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VTerminalTheme {
                TerminalScreen()
            }
        }
    }
}

@Composable
fun VTerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content,
        colors = darkColorScheme(
            background = Color(0xFF0A0A0F),
            surface = Color(0xFF1A1A2E),
            primary = Color(0xFF00FFCC),
            secondary = Color(0xFFFF0055),
        )
    )
}

@Composable
fun TerminalScreen() {
    var outputLines by remember { mutableStateOf(listOf<String>()) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) listState.animateScrollToItem(outputLines.lastIndex)
    }

    fun addOutput(line: String) {
        outputLines = outputLines + line
        if (outputLines.size > 500) outputLines = outputLines.drop(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "V-Terminal Pro",
            color = Color(0xFF00FFCC),
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Ubuntu Shell • Kotlin • Compose 2026",
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Output area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0D0E15), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            items(outputLines) { line ->
                Text(
                    text = line,
                    color = Color(0xFFE0F7FA),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "root@ubuntu:~#",
                color = Color(0xFFFF0055),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Button(
                onClick = {
                    if (inputText.isNotEmpty()) {
                        addOutput("root@ubuntu:~# $inputText")
                        addOutput("[OK] Command received: $inputText")
                        inputText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SEND", color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}
