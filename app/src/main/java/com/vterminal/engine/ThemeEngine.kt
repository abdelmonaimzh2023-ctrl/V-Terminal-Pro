package com.vterminal.engine

import android.graphics.*
import kotlin.math.*

data class TerminalTheme(
    val name: String,
    val bgColor: Int,
    val textColor: Int,
    val accentColor: Int,
    val promptColor: Int,
    val errorColor: Int,
    val warningColor: Int,
    val successColor: Int,
    val inputBgColor: Int,
    val headerBgColor: Int,
    val progressBgColor: Int,
    val progressFgColor: Int,
    val fontSize: Float = 12f,
    val lineSpacing: Float = 1.2f
)

object ThemeEngine {
    
    // 20 ثيم جاهز
    val themes = mapOf(
        "matrix" to TerminalTheme(
            "Matrix", 0xFF000000.toInt(), 0xFF00FF00.toInt(), 0xFF00CC00.toInt(),
            0xFF00FF00.toInt(), 0xFFFF0000.toInt(), 0xFFFFAA00.toInt(), 0xFF00FF00.toInt(),
            0xFF0D0D0D.toInt(), 0xFF0A0A0A.toInt(), 0xFF1A1A1A.toInt(), 0xFF00FF00.toInt()
        ),
        "ocean" to TerminalTheme(
            "Ocean Deep", 0xFF001122.toInt(), 0xFF00CCFF.toInt(), 0xFF0088CC.toInt(),
            0xFF00AAFF.toInt(), 0xFFFF4444.toInt(), 0xFFFFAA00.toInt(), 0xFF00FFAA.toInt(),
            0xFF001A33.toInt(), 0xFF001122.toInt(), 0xFF002244.toInt(), 0xFF00CCFF.toInt()
        ),
        "sunset" to TerminalTheme(
            "Sunset", 0xFF1A0A0A.toInt(), 0xFFFF8866.toInt(), 0xFFFF5522.toInt(),
            0xFFFF6644.toInt(), 0xFFFF0000.toInt(), 0xFFFFCC00.toInt(), 0xFF66FF66.toInt(),
            0xFF2A1A1A.toInt(), 0xFF1A0A0A.toInt(), 0xFF2A1515.toInt(), 0xFFFF6644.toInt()
        ),
        "cyberpunk" to TerminalTheme(
            "Cyberpunk 2077", 0xFF0A0A0F.toInt(), 0xFFFF00FF.toInt(), 0xFFFF00AA.toInt(),
            0xFFFF00CC.toInt(), 0xFFFF0000.toInt(), 0xFFFFAA00.toInt(), 0xFF00FFAA.toInt(),
            0xFF1A1A2E.toInt(), 0xFF0A0A1A.toInt(), 0xFF1A1A3E.toInt(), 0xFFFF00FF.toInt()
        ),
        "midnight" to TerminalTheme(
            "Midnight Blue", 0xFF000011.toInt(), 0xFF4466FF.toInt(), 0xFF2244CC.toInt(),
            0xFF3355EE.toInt(), 0xFFFF4444.toInt(), 0xFFFFAA00.toInt(), 0xFF44FF44.toInt(),
            0xFF000022.toInt(), 0xFF000011.toInt(), 0xFF000033.toInt(), 0xFF4466FF.toInt()
        ),
        "forest" to TerminalTheme(
            "Forest", 0xFF001100.toInt(), 0xFF44CC44.toInt(), 0xFF228822.toInt(),
            0xFF33AA33.toInt(), 0xFFFF4444.toInt(), 0xFFFFAA00.toInt(), 0xFF88FF88.toInt(),
            0xFF002200.toInt(), 0xFF001100.toInt(), 0xFF003300.toInt(), 0xFF44CC44.toInt()
        ),
        "candy" to TerminalTheme(
            "Candy", 0xFF1A001A.toInt(), 0xFFFF88FF.toInt(), 0xFFFF44CC.toInt(),
            0xFFFF66DD.toInt(), 0xFFFF0000.toInt(), 0xFFFFAA00.toInt(), 0xFF66FF66.toInt(),
            0xFF2A002A.toInt(), 0xFF1A001A.toInt(), 0xFF3A003A.toInt(), 0xFFFF88FF.toInt()
        ),
        "steel" to TerminalTheme(
            "Steel", 0xFF1A1A1A.toInt(), 0xFFCCCCCC.toInt(), 0xFF888888.toInt(),
            0xFFAAAAAA.toInt(), 0xFFFF4444.toInt(), 0xFFFFAA00.toInt(), 0xFF66FF66.toInt(),
            0xFF2A2A2A.toInt(), 0xFF1A1A1A.toInt(), 0xFF333333.toInt(), 0xFFCCCCCC.toInt()
        ),
        "gold" to TerminalTheme(
            "Gold", 0xFF1A1A00.toInt(), 0xFFFFCC00.toInt(), 0xFFCC8800.toInt(),
            0xFFDDAA00.toInt(), 0xFFFF0000.toInt(), 0xFFFF6600.toInt(), 0xFF66FF66.toInt(),
            0xFF2A2A00.toInt(), 0xFF1A1A00.toInt(), 0xFF3A3A00.toInt(), 0xFFFFCC00.toInt()
        ),
        "arctic" to TerminalTheme(
            "Arctic", 0xFFFFFFFF.toInt().inv(), 0xFFFFFFFF.toInt(), 0xFFCCCCCC.toInt(),
            0xFFEEEEEE.toInt(), 0xFFFF4444.toInt(), 0xFFFFAA00.toInt(), 0xFF44FF44.toInt(),
            0xFFF0F0F0.toInt(), 0xFFFAFAFA.toInt(), 0xFFE0E0E0.toInt(), 0xFFFFFFFF.toInt()
        ),
        "neon" to TerminalTheme(
            "Neon Nights", 0xFF0A0A0F.toInt(), 0xFF00FFCC.toInt(), 0xFFFF0055.toInt(),
            0xFF00FFCC.toInt(), 0xFFFF0000.toInt(), 0xFFFFAA00.toInt(), 0xFF00FF88.toInt(),
            0xFF1A1A2E.toInt(), 0xFF0A0A1A.toInt(), 0xFF1A1A3E.toInt(), 0xFF00FFCC.toInt()
        ),
        "dracula" to TerminalTheme(
            "Dracula", 0xFF282A36.toInt(), 0xFFF8F8F2.toInt(), 0xFFBD93F9.toInt(),
            0xFF50FA7B.toInt(), 0xFFFF5555.toInt(), 0xFFFFB86C.toInt(), 0xFF50FA7B.toInt(),
            0xFF44475A.toInt(), 0xFF282A36.toInt(), 0xFF44475A.toInt(), 0xFFBD93F9.toInt()
        ),
        "monokai" to TerminalTheme(
            "Monokai", 0xFF272822.toInt(), 0xFFF8F8F2.toInt(), 0xFFA6E22E.toInt(),
            0xFFF92672.toInt(), 0xFFFF0000.toInt(), 0xFFFD971F.toInt(), 0xFFA6E22E.toInt(),
            0xFF3E3D32.toInt(), 0xFF272822.toInt(), 0xFF3E3D32.toInt(), 0xFFA6E22E.toInt()
        ),
        "solarized" to TerminalTheme(
            "Solarized Dark", 0xFF002B36.toInt(), 0xFF839496.toInt(), 0xFF268BD2.toInt(),
            0xFF2AA198.toInt(), 0xFFDC322F.toInt(), 0xFFB58900.toInt(), 0xFF859900.toInt(),
            0xFF073642.toInt(), 0xFF002B36.toInt(), 0xFF073642.toInt(), 0xFF268BD2.toInt()
        ),
        "gruvbox" to TerminalTheme(
            "Gruvbox Dark", 0xFF282828.toInt(), 0xFFEBDBB2.toInt(), 0xFFD79921.toInt(),
            0xFF83A598.toInt(), 0xFFFB4934.toInt(), 0xFFFABD2F.toInt(), 0xFFB8BB26.toInt(),
            0xFF3C3836.toInt(), 0xFF282828.toInt(), 0xFF3C3836.toInt(), 0xFFD79921.toInt()
        ),
        "nord" to TerminalTheme(
            "Nord", 0xFF2E3440.toInt(), 0xFFD8DEE9.toInt(), 0xFF88C0D0.toInt(),
            0xFF81A1C1.toInt(), 0xFFBF616A.toInt(), 0xFFD08770.toInt(), 0xFFA3BE8C.toInt(),
            0xFF3B4252.toInt(), 0xFF2E3440.toInt(), 0xFF3B4252.toInt(), 0xFF88C0D0.toInt()
        ),
        "tokyo" to TerminalTheme(
            "Tokyo Night", 0xFF1A1B26.toInt(), 0xFFA9B1D6.toInt(), 0xFF7AA2F7.toInt(),
            0xFFBB9AF7.toInt(), 0xFFF7768E.toInt(), 0xFFE0AF68.toInt(), 0xFF9ECE6A.toInt(),
            0xFF24283B.toInt(), 0xFF1A1B26.toInt(), 0xFF24283B.toInt(), 0xFF7AA2F7.toInt()
        ),
        "catppuccin" to TerminalTheme(
            "Catppuccin", 0xFF1E1E2E.toInt(), 0xFFCDD6F4.toInt(), 0xFFCBA6F7.toInt(),
            0xFF89B4FA.toInt(), 0xFFF38BA8.toInt(), 0xFFFAB387.toInt(), 0xFFA6E3A1.toInt(),
            0xFF313244.toInt(), 0xFF1E1E2E.toInt(), 0xFF313244.toInt(), 0xFFCBA6F7.toInt()
        ),
        "everforest" to TerminalTheme(
            "Everforest", 0xFF2B3339.toInt(), 0xFFD3C6AA.toInt(), 0xFFA7C080.toInt(),
            0xFF7FBBB3.toInt(), 0xFFE67E80.toInt(), 0xFFDBBC7F.toInt(), 0xFFA7C080.toInt(),
            0xFF3A4248.toInt(), 0xFF2B3339.toInt(), 0xFF3A4248.toInt(), 0xFFA7C080.toInt()
        ),
        "rosepine" to TerminalTheme(
            "Rosé Pine", 0xFF191724.toInt(), 0xFFE0DEF4.toInt(), 0xFFEB6F92.toInt(),
            0xFF31748F.toInt(), 0xFFEB6F92.toInt(), 0xFFF6C177.toInt(), 0xFF9CCFD8.toInt(),
            0xFF26233A.toInt(), 0xFF191724.toInt(), 0xFF26233A.toInt(), 0xFFEB6F92.toInt()
        )
    )
    
    private var currentTheme = themes["neon"]!!
    
    fun getCurrentTheme(): TerminalTheme = currentTheme
    
    fun setTheme(name: String): Boolean {
        return if (themes.containsKey(name)) {
            currentTheme = themes[name]!!
            true
        } else false
    }
    
    fun getThemeNames(): List<String> = themes.keys.toList()
    
    fun generateBackground(theme: TerminalTheme = currentTheme): Paint {
        return Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 2000f,
                theme.bgColor, darken(theme.bgColor, 0.8f),
                Shader.TileMode.CLAMP
            )
        }
    }
    
    fun generateGlowEffect(color: Int, radius: Float = 20f): Paint {
        return Paint().apply {
            this.color = color
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.OUTER)
            alpha = 100
        }
    }
    
    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF) * factor
        val g = ((color shr 8) and 0xFF) * factor
        val b = (color and 0xFF) * factor
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
}
