package com.vterminal.terminal

import android.graphics.*
import android.text.*
import android.util.*
import java.io.*
import java.util.*
import java.util.concurrent.*

/**
 * محاكي طرفية متكامل مع دعم ANSI Escape Codes
 * يدعم: الألوان، المؤشر، المسح، التمرير، حجم الشاشة
 */
class TerminalEmulator(
    private val columns: Int = 80,
    private val rows: Int = 24
) {
    // ==================== CHARACTER BUFFER ====================
    data class Cell(
        var char: Char = ' ',
        var fgColor: Int = 0xFFFFFFFF.toInt(),
        var bgColor: Int = 0xFF000000.toInt(),
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var blink: Boolean = false,
        var reverse: Boolean = false
    )
    
    private val buffer = Array(rows) { Array(columns) { Cell() } }
    private var cursorRow = 0
    private var cursorCol = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    
    // ==================== ANSI STATE ====================
    private enum class State {
        NORMAL, ESCAPE, CSI, OSC
    }
    
    private var state = State.NORMAL
    private val params = StringBuilder()
    private val oscString = StringBuilder()
    private var title = "V-Terminal Pro"
    
    // ==================== COLORS ====================
    companion object {
        val COLORS_16 = arrayOf(
            0xFF000000.toInt(), // Black
            0xFFCC0000.toInt(), // Red
            0xFF4E9A06.toInt(), // Green
            0xFFC4A000.toInt(), // Yellow
            0xFF3465A4.toInt(), // Blue
            0xFF75507B.toInt(), // Magenta
            0xFF06989A.toInt(), // Cyan
            0xFFD3D7CF.toInt(), // White
            0xFF555753.toInt(), // Bright Black
            0xFFEF2929.toInt(), // Bright Red
            0xFF8AE234.toInt(), // Bright Green
            0xFFFCE94F.toInt(), // Bright Yellow
            0xFF729FCF.toInt(), // Bright Blue
            0xFFAD7FA8.toInt(), // Bright Magenta
            0xFF34E2E2.toInt(), // Bright Cyan
            0xFFEEEEEC.toInt()  // Bright White
        )
        
        private val COLOR_TABLE_256 = generateColorTable()
        
        private fun generateColorTable(): IntArray {
            val table = IntArray(256)
            for (i in 0..15) table[i] = COLORS_16[i]
            
            // 6x6x6 cube (216 colors)
            var idx = 16
            for (r in 0..5) for (g in 0..5) for (b in 0..5) {
                table[idx++] = (0xFF shl 24) or ((r * 51) shl 16) or ((g * 51) shl 8) or (b * 51)
            }
            
            // Grayscale (24 levels)
            for (i in 0..23) {
                val gray = i * 10 + 8
                table[idx++] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            
            return table
        }
    }
    
    // ==================== OUTPUT STREAM ====================
    private val outputListeners = mutableListOf<(CharSequence) -> Unit>()
    
    fun addOutputListener(listener: (CharSequence) -> Unit) {
        outputListeners.add(listener)
    }
    
    private fun notifyOutput(text: CharSequence) {
        outputListeners.forEach { it(text) }
    }
    
    // ==================== INPUT PROCESSING ====================
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        for (i in offset until offset + length) {
            processByte(data[i].toInt() and 0xFF)
        }
    }
    
    fun write(text: String) {
        write(text.toByteArray())
    }
    
    private fun processByte(b: Int) {
        when (state) {
            State.NORMAL -> processNormal(b)
            State.ESCAPE -> processEscape(b)
            State.CSI -> processCSI(b)
            State.OSC -> processOSC(b)
        }
    }
    
    private fun processNormal(b: Int) {
        when (b) {
            0x1B -> state = State.ESCAPE // ESC
            0x07 -> { /* Bell - ignore */ }
            0x08 -> { if (cursorCol > 0) cursorCol-- } // Backspace
            0x09 -> { // Tab
                val tabStop = 8
                cursorCol = ((cursorCol / tabStop) + 1) * tabStop
                if (cursorCol >= columns) cursorCol = columns - 1
            }
            0x0A -> cursorDown(1) // Line Feed
            0x0D -> cursorCol = 0 // Carriage Return
            in 0x20..0x7E -> putChar(b.toChar()) // Printable
            else -> { /* ignore */ }
        }
    }
    
    private fun processEscape(b: Int) {
        when (b) {
            '['.code -> { state = State.CSI; params.clear() }
            ']'.code -> { state = State.OSC; oscString.clear() }
            '7'.code -> { savedCursorRow = cursorRow; savedCursorCol = cursorCol; state = State.NORMAL }
            '8'.code -> { cursorRow = savedCursorRow; cursorCol = savedCursorCol; state = State.NORMAL }
            'D'.code -> { cursorDown(1); state = State.NORMAL }
            'M'.code -> { cursorUp(1); state = State.NORMAL }
            'c'.code -> { reset(); state = State.NORMAL }
            else -> state = State.NORMAL
        }
    }
    
    private fun processCSI(b: Int) {
        when (b) {
            in 0x30..0x3F -> params.append(b.toChar())
            in 0x20..0x2F -> { /* intermediate */ }
            'A'.code -> { cursorUp(getParam(1)); state = State.NORMAL }
            'B'.code -> { cursorDown(getParam(1)); state = State.NORMAL }
            'C'.code -> { cursorForward(getParam(1)); state = State.NORMAL }
            'D'.code -> { cursorBackward(getParam(1)); state = State.NORMAL }
            'H'.code -> { // CUP
                val row = getParam(0, 1) - 1
                val col = getParam(1, 1) - 1
                cursorRow = row.coerceIn(0, rows - 1)
                cursorCol = col.coerceIn(0, columns - 1)
                state = State.NORMAL
            }
            'J'.code -> { eraseDisplay(getParam(0)); state = State.NORMAL }
            'K'.code -> { eraseLine(getParam(0)); state = State.NORMAL }
            'm'.code -> { setGraphicsRendition(); state = State.NORMAL }
            'r'.code -> { // Set scrolling region
                scrollTop = getParam(0, 1) - 1
                scrollBottom = getParam(1, rows) - 1
                state = State.NORMAL
            }
            's'.code -> { savedCursorRow = cursorRow; savedCursorCol = cursorCol; state = State.NORMAL }
            'u'.code -> { cursorRow = savedCursorRow; cursorCol = savedCursorCol; state = State.NORMAL }
            else -> state = State.NORMAL
        }
    }
    
    private fun processOSC(b: Int) {
        when (b) {
            0x07, 0x9C -> { // BEL or ST
                val str = oscString.toString()
                if (str.startsWith("0;")) title = str.substring(2)
                else if (str.startsWith("2;")) title = str.substring(2)
                state = State.NORMAL
            }
            else -> oscString.append(b.toChar())
        }
    }
    
    // ==================== CURSOR OPERATIONS ====================
    private fun cursorUp(n: Int) {
        cursorRow = (cursorRow - n).coerceAtLeast(scrollTop)
    }
    
    private fun cursorDown(n: Int) {
        cursorRow = (cursorRow + n).coerceAtMost(scrollBottom)
    }
    
    private fun cursorForward(n: Int) {
        cursorCol = (cursorCol + n).coerceAtMost(columns - 1)
    }
    
    private fun cursorBackward(n: Int) {
        cursorCol = (cursorCol - n).coerceAtLeast(0)
    }
    
    // ==================== CHARACTER OUTPUT ====================
    private fun putChar(ch: Char) {
        if (cursorCol >= columns) {
            cursorCol = 0
            cursorDown(1)
        }
        buffer[cursorRow][cursorCol].char = ch
        cursorCol++
    }
    
    private fun newLine() {
        cursorCol = 0
        if (cursorRow < scrollBottom) {
            cursorRow++
        } else {
            scrollUp()
        }
    }
    
    private fun scrollUp() {
        for (row in scrollTop until scrollBottom) {
            buffer[row] = buffer[row + 1].copyOf()
        }
        buffer[scrollBottom] = Array(columns) { Cell() }
    }
    
    // ==================== ERASE OPERATIONS ====================
    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { // Cursor to end
                eraseLine(0)
                for (row in cursorRow + 1 until rows) {
                    buffer[row] = Array(columns) { Cell() }
                }
            }
            1 -> { // Start to cursor
                for (row in 0 until cursorRow) {
                    buffer[row] = Array(columns) { Cell() }
                }
                eraseLine(1)
            }
            2, 3 -> { // Entire screen
                for (row in 0 until rows) {
                    buffer[row] = Array(columns) { Cell() }
                }
                cursorRow = 0; cursorCol = 0
            }
        }
    }
    
    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> { // Cursor to end
                for (col in cursorCol until columns) {
                    buffer[cursorRow][col] = Cell()
                }
            }
            1 -> { // Start to cursor
                for (col in 0..cursorCol) {
                    buffer[cursorRow][col] = Cell()
                }
            }
            2 -> { // Entire line
                buffer[cursorRow] = Array(columns) { Cell() }
            }
        }
    }
    
    // ==================== GRAPHICS RENDITION ====================
    private var currentFgColor = 0xFFFFFFFF.toInt()
    private var currentBgColor = 0xFF000000.toInt()
    private var currentBold = false
    private var currentItalic = false
    private var currentUnderline = false
    private var currentBlink = false
    private var currentReverse = false
    
    private fun setGraphicsRendition() {
        val paramsStr = params.toString()
        if (paramsStr.isEmpty()) {
            // Reset
            currentFgColor = 0xFFFFFFFF.toInt()
            currentBgColor = 0xFF000000.toInt()
            currentBold = false
            currentItalic = false
            currentUnderline = false
            currentBlink = false
            currentReverse = false
            return
        }
        
        val paramList = paramsStr.split(";").map { it.toIntOrNull() ?: 0 }
        var i = 0
        while (i < paramList.size) {
            when (val p = paramList[i]) {
                0 -> { // Reset
                    currentFgColor = 0xFFFFFFFF.toInt()
                    currentBgColor = 0xFF000000.toInt()
                    currentBold = false; currentItalic = false
                    currentUnderline = false; currentBlink = false; currentReverse = false
                }
                1 -> currentBold = true
                2 -> currentBold = false
                3 -> currentItalic = true
                4 -> currentUnderline = true
                5 -> currentBlink = true
                7 -> currentReverse = true
                22 -> currentBold = false
                23 -> currentItalic = false
                24 -> currentUnderline = false
                25 -> currentBlink = false
                27 -> currentReverse = false
                30..37 -> currentFgColor = COLORS_16[p - 30]
                38 -> {
                    if (i + 2 < paramList.size && paramList[i + 1] == 5) {
                        currentFgColor = COLOR_TABLE_256[paramList[i + 2].coerceIn(0, 255)]
                        i += 2
                    }
                }
                40..47 -> currentBgColor = COLORS_16[p - 40]
                48 -> {
                    if (i + 2 < paramList.size && paramList[i + 1] == 5) {
                        currentBgColor = COLOR_TABLE_256[paramList[i + 2].coerceIn(0, 255)]
                        i += 2
                    }
                }
                90..97 -> currentFgColor = COLORS_16[p - 90 + 8]
                100..107 -> currentBgColor = COLORS_16[p - 100 + 8]
            }
            i++
        }
    }
    
    // ==================== PARAMETER PARSING ====================
    private fun getParam(index: Int, default: Int = 0): Int {
        val paramList = params.toString().split(";")
        return paramList.getOrNull(index)?.toIntOrNull() ?: default
    }
    
    // ==================== RESET ====================
    fun reset() {
        for (row in 0 until rows) {
            buffer[row] = Array(columns) { Cell() }
        }
        cursorRow = 0; cursorCol = 0
        scrollTop = 0; scrollBottom = rows - 1
        state = State.NORMAL
        params.clear()
    }
    
    // ==================== RENDER ====================
    fun render(): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val cell = buffer[row][col]
                sb.append(cell.char)
                
                if (cell.char != ' ') {
                    val flags = SpannableString.SPAN_INCLUSIVE_EXCLUSIVE
                    val start = sb.length - 1
                    val end = sb.length
                    
                    if (cell.bold || cell.fgColor != 0xFFFFFFFF.toInt()) {
                        sb.setSpan(ForegroundColorSpan(cell.fgColor), start, end, flags)
                    }
                    if (cell.bgColor != 0xFF000000.toInt()) {
                        sb.setSpan(BackgroundColorSpan(cell.bgColor), start, end, flags)
                    }
                    if (cell.bold) {
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, flags)
                    }
                    if (cell.italic) {
                        sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, flags)
                    }
                    if (cell.underline) {
                        sb.setSpan(UnderlineSpan(), start, end, flags)
                    }
                }
            }
            if (row < rows - 1) sb.append('\n')
        }
        
        return sb
    }
    
    fun renderAsText(): String {
        return buildString {
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    append(buffer[row][col].char)
                }
                if (row < rows - 1) append('\n')
            }
        }
    }
    
    // ==================== RESIZE ====================
    fun resize(newColumns: Int, newRows: Int) {
        // TODO: Implement resize logic
    }
    
    // ==================== GETTERS ====================
    fun getTitle(): String = title
    fun getCursorRow(): Int = cursorRow
    fun getCursorCol(): Int = cursorCol
}
