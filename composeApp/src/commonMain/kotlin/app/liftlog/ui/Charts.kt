package app.liftlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.dShort
import app.liftlog.logic.fmtN
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class Series(val pts: List<Pair<Long, Double>>, val color: Color)

private fun niceStep(x: Double): Double {
    if (x <= 0) return 1.0
    val p = 10.0.pow(floor(log10(x))); val f = x / p
    return (if (f <= 1) 1.0 else if (f <= 2) 2.0 else if (f <= 2.5) 2.5 else if (f <= 5) 5.0 else 10.0) * p
}

/** Line chart drawn at real size: y gridlines with labels, first/last date, dots, last point emphasised. */
@Composable
fun LineChart(series: List<Series>, modifier: Modifier = Modifier.fillMaxWidth().height(220.dp)) {
    val p = LocalPal.current
    val tm = rememberTextMeasurer()
    val all = series.flatMap { it.pts }
    Canvas(modifier) {
        val labelStyle = TextStyle(color = p.mu, fontSize = 13.sp)
        if (all.isEmpty()) {
            val l = tm.measure("No sessions in this range", labelStyle)
            drawText(l, topLeft = Offset((size.width - l.size.width) / 2, (size.height - l.size.height) / 2)); return@Canvas
        }
        val padL = 52.dp.toPx(); val padR = 14.dp.toPx(); val padT = 14.dp.toPx(); val padB = 26.dp.toPx()
        var lo = all.minOf { it.second }; var hi = all.maxOf { it.second }
        if (lo == hi) { lo *= 0.9; hi = if (hi == 0.0) 1.0 else hi * 1.1 }
        val step = niceStep((hi - lo) / 4); lo = maxOf(0.0, floor(lo / step) * step); hi = ceil(hi / step) * step
        val x0 = all.minOf { it.first }; val x1 = all.maxOf { it.first }
        val w = size.width - padL - padR; val h = size.height - padT - padB
        fun X(v: Long) = if (x1 == x0) padL + w / 2 else padL + ((v - x0).toDouble() / (x1 - x0) * w).toFloat()
        fun Y(v: Double) = padT + ((1 - (v - lo) / (hi - lo)) * h).toFloat()
        var v = lo
        while (v <= hi + 1e-9) {
            drawLine(p.ink.copy(alpha = 0.12f), Offset(padL, Y(v)), Offset(size.width - padR, Y(v)), 1f)
            val l = tm.measure(fmtN(v), labelStyle); drawText(l, topLeft = Offset(padL - 8.dp.toPx() - l.size.width, Y(v) - l.size.height / 2))
            v += step
        }
        series.forEachIndexed { i, s ->
            if (s.pts.isEmpty()) return@forEachIndexed
            val path = Path(); s.pts.forEachIndexed { j, (x, y) -> if (j == 0) path.moveTo(X(x), Y(y)) else path.lineTo(X(x), Y(y)) }
            if (series.size == 1 && s.pts.size > 1) {
                val area = Path().apply { addPath(path); lineTo(X(s.pts.last().first), Y(lo)); lineTo(X(s.pts.first().first), Y(lo)); close() }
                drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0f)), startY = padT, endY = padT + h))
            }
            drawPath(path, s.color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
            if (s.pts.size <= 60) s.pts.forEach { (x, y) -> drawCircle(s.color, 3.dp.toPx(), Offset(X(x), Y(y))) }
            val last = s.pts.last(); drawCircle(p.bg, 7.5.dp.toPx(), Offset(X(last.first), Y(last.second))); drawCircle(s.color, 5.5.dp.toPx(), Offset(X(last.first), Y(last.second)))
        }
        val a = tm.measure(dShort(x0), labelStyle); drawText(a, topLeft = Offset(padL, size.height - a.size.height))
        if (x1 != x0) { val b = tm.measure(dShort(x1), labelStyle); drawText(b, topLeft = Offset(size.width - padR - b.size.width, size.height - b.size.height)) }
    }
}

/* ---------- body heat map: an original simple figure, front and back ---------- */
private sealed class Sh { data class E(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Sh(); data class R(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : Sh() }
private fun e(cx: Number, cy: Number, rx: Number, ry: Number) = Sh.E(cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat())
private fun r(x: Number, y: Number, w: Number, h: Number, rr: Number) = Sh.R(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), rr.toFloat())
private val SIL = listOf(Sh.E(100f, 34f, 20f, 20f), r(90, 50, 20, 18, 5), r(64, 64, 72, 122, 26), r(70, 168, 60, 46, 18), r(38, 72, 24, 80, 12), r(138, 72, 24, 80, 12),
    r(34, 142, 22, 70, 11), r(144, 142, 22, 70, 11), r(70, 200, 28, 186, 14), r(102, 200, 28, 186, 14))
private val FRONT = listOf("Shoulders" to listOf(e(60, 84, 14, 13), e(140, 84, 14, 13)), "Chest" to listOf(e(84, 99, 18, 14), e(116, 99, 18, 14)),
    "Biceps" to listOf(e(50, 119, 10, 23), e(150, 119, 10, 23)), "Forearms" to listOf(e(45, 176, 9, 27), e(155, 176, 9, 27)), "Abs" to listOf(r(88, 117, 24, 62, 9)),
    "Quads" to listOf(e(84, 258, 13, 44), e(116, 258, 13, 44)), "Adductors" to listOf(e(94, 232, 4.5, 20), e(106, 232, 4.5, 20)), "Calves" to listOf(e(84, 332, 9, 28), e(116, 332, 9, 28)))
private val BACK = listOf("Traps" to listOf(e(100, 74, 25, 12)), "Shoulders" to listOf(e(60, 86, 13, 12), e(140, 86, 13, 12)),
    "Back" to listOf(e(82, 118, 16, 30), e(118, 118, 16, 30), r(92, 140, 16, 36, 6)), "Triceps" to listOf(e(50, 117, 10, 23), e(150, 117, 10, 23)),
    "Forearms" to listOf(e(45, 176, 9, 27), e(155, 176, 9, 27)), "Glutes" to listOf(e(86, 198, 15, 16), e(114, 198, 15, 16)),
    "Hamstrings" to listOf(e(84, 258, 12, 40), e(116, 258, 12, 40)), "Calves" to listOf(e(84, 328, 11, 28), e(116, 328, 11, 28)))

@Composable
fun BodyMap(cnt: Map<String, Int>, maxWidth: androidx.compose.ui.unit.Dp = 340.dp, legend: Boolean = true) {
    val p = LocalPal.current
    val mx = maxOf(1, cnt.values.maxOrNull() ?: 0)
    fun fill(m: String): Color { val v = cnt[m] ?: 0; return if (v == 0) p.s2 else lerp(p.s2, p.ac, 0.25f + 0.75f * v / mx) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.widthIn(max = maxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Front" to FRONT, "Back" to BACK).forEach { (label, parts) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(156f / 384f)) {
                        val k = size.width / 156f
                        fun DrawScope.shape(s: Sh, c: Color) = when (s) {
                            is Sh.E -> drawOval(c, Offset((s.cx - s.rx - 22) * k, (s.cy - s.ry - 8) * k), Size(2 * s.rx * k, 2 * s.ry * k))
                            is Sh.R -> drawRoundRect(c, Offset((s.x - 22) * k, (s.y - 8) * k), Size(s.w * k, s.h * k), CornerRadius(s.r * k))
                        }
                        SIL.forEach { shape(it, p.s1) }
                        parts.forEach { (m, shapes) -> shapes.forEach { shape(it, fill(m)) } }
                    }
                    Mu(label, size = 13.sp)
                }
            }
        }
        if (legend) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Mu("Fewer sets", size = 13.sp); Spacer(Modifier.width(8.dp))
            Canvas(Modifier.width(90.dp).height(8.dp)) { drawRoundRect(Brush.horizontalGradient(listOf(p.s2, p.ac)), cornerRadius = CornerRadius(4.dp.toPx())) }
            Spacer(Modifier.width(8.dp)); Mu("More", size = 13.sp)
        }
    }
}

/** "Muscles worked" for one workout: a small body map and the sets per muscle. */
@Composable
fun MusclesWorked(cnt: Map<String, Int>) {
    if (cnt.isEmpty()) return
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Txt("Muscles worked", size = 17.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        BodyMap(cnt, maxWidth = 230.dp, legend = false)
        val mx = maxOf(1, cnt.values.maxOrNull() ?: 0)
        cnt.forEach { (m, n) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(m, Modifier.width(100.dp), size = 15.sp)
                Box(Modifier.weight(1f).height(10.dp).background(p.s2, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / mx).fillMaxHeight().background(p.ac, androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
                }
                Txt("$n", Modifier.width(36.dp), size = 15.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Mu("Working sets per main muscle.", Modifier.padding(top = 6.dp), size = 13.sp)
    }
}
