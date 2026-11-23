package app.liftlog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp

@Immutable
data class Pal(
    val bg: Color, val s1: Color, val s2: Color, val ln: Color, val ink: Color, val mu: Color,
    val ac: Color, val acs: Color, val ok: Color, val oks: Color,
    val wu: Color = Color(0xFFE8930A), val dr: Color = Color(0xFF9B7BF5), val fa: Color = Color(0xFFEF4444), val pr: Color = Color(0xFFD99A00),
    val dark: Boolean,
)

val LightPal = Pal(Color(0xFFFFFFFF), Color(0xFFF2F3F5), Color(0xFFE5E7EB), Color(0xFFE3E5E9), Color(0xFF101215), Color(0xFF5F6670),
    Color(0xFF1764FF), Color(0xFFE7EEFF), Color(0xFF16A34A), Color(0xFFE2F7E9), dark = false)
val DarkPal = Pal(Color(0xFF0D0E10), Color(0xFF17191C), Color(0xFF23262B), Color(0xFF2A2E34), Color(0xFFF3F5F8), Color(0xFF9097A2),
    Color(0xFF3D84FF), Color(0xFF132446), Color(0xFF22C55E), Color(0xFF0F2A1A), dark = true)

val LocalPal = staticCompositionLocalOf { LightPal }
val LocalLb = staticCompositionLocalOf { false }

@Composable
fun LiftTheme(lb: Boolean, theme: String?, content: @Composable () -> Unit) {
    val dark = when (theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(primary = p.ac, background = p.bg, surface = p.bg, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = p.ac, background = p.bg, surface = p.bg, onSurface = p.ink, onBackground = p.ink)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPal provides p, LocalLb provides lb, content = content)
    }
}

/** Stroke icons built from the same path data as the web app. */
private fun icon(name: String, vararg paths: String, fill: Boolean = false): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = if (fill) null else SolidColor(Color.Black),
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

object Ic {
    val plus = icon("plus", "M12 5v14M5 12h14")
    val check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val dots = icon("dots", circle(5f, 12f, 1.3f), circle(12f, 12f, 1.3f), circle(19f, 12f, 1.3f), fill = true)
    val down = icon("down", "M6 9l6 6 6-6")
    val left = icon("left", "M15 6l-6 6 6 6")
    val right = icon("right", "M9 6l6 6-6 6")
    val clock = icon("clock", circle(12f, 13f, 8f), "M12 9v4l2.5 2.5M9 2h6")
    val trophy = icon("trophy", "M8 4h8v5a4 4 0 0 1-8 0zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8M10 17h4")
    val gear = icon("gear", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    val dumb = icon("dumb", "M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12")
    val heart = icon("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val cal = icon("cal", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 10h18M8 3v4M16 3v4")
    val chart = icon("chart", "M4 4v16h16M8 15l4-4 3 3 5-6")
    val search = icon("search", circle(11f, 11f, 6f), "M20 20l-4.5-4.5")
    val x = icon("x", "M6 6l12 12M18 6L6 18")
    val ruler = icon("ruler", "M3.5 16.5l13-13 4 4-13 13z", "M8 12l2 2M11 9l2 2M14 6l2 2")
    val photo = icon("photo", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", circle(9f, 10f, 2f), "M21 16l-5-5-8 8")
    val up = icon("up", "M12 19V5M6 11l6-6 6 6")
    val pin = icon("pin", "M12 16v6M8 3h8l-1.5 6L18 13H6l3.5-4z")
    val play = icon("play", "M8 5l11 7-11 7z")
    val stop = icon("stop", "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z")
    val chat = icon("chat", "M4 5h16v11H9l-5 4z")
    val share = icon("share", circle(18f, 5f, 2.5f), circle(6f, 12f, 2.5f), circle(18f, 19f, 2.5f), "M8.2 10.9l7.6-4.6M8.2 13.1l7.6 4.6")
    val warn = icon("warn", "M12 4l9 16H3zM12 10v4M12 17.5v.5")
    val import = icon("import", "M12 4v11M7 10l5 5 5-5M4 17v2a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-2")
    val user = icon("user", circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0")
}
