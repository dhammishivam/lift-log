package app.liftlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.drawscope.scale
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
/** Parsed body map paths (in a 200 × 420 box), built once. */
// Both halves in one path, so no hairline seam shows down the middle.
private val SIL_P by lazy { listOf(androidx.compose.ui.graphics.Path().apply { BodyPaths.sil.forEach { d -> addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()) } }) }
private val FRONT_P by lazy { BodyPaths.front.mapValues { (_, ds) -> ds.map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() } } }
private val BACK_P by lazy { BodyPaths.back.mapValues { (_, ds) -> ds.map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() } } }

/** Shades for levels 0–4 of the volume map: untrained, then light to strong; 4 is above the productive range. */
@Composable
fun volumeShades(): List<Color> {
    val p = LocalPal.current
    return listOf(lerp(p.s2, p.ink, 0.12f), lerp(p.s2, p.ac, 0.38f), lerp(p.s2, p.ac, 0.7f), p.ac, lerp(p.ac, Color.White, 0.38f))
}

/** Shades for the recovery map: fresh, then 48–72 h, 24–48 h and under 24 h since trained. */
@Composable
fun recoveryShades(): List<Color> {
    val p = LocalPal.current
    return listOf(lerp(p.s2, p.ink, 0.12f), Color(0xFFE9C46A), Color(0xFFF08C3A), Color(0xFFE5484D), Color(0xFFE5484D))
}

/**
 * Front and back anatomy. Each region is drawn in shades[levels[region]]; [sel] is outlined and [onTap] reports
 * which region was tapped (null for none), so the map works as a picker.
 */
@Composable
fun BodyMap(levels: Map<String, Int>, shades: List<Color>, maxWidth: androidx.compose.ui.unit.Dp = 380.dp, sel: String? = null, onTap: ((String?) -> Unit)? = null) {
    val p = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.widthIn(max = maxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Front" to FRONT_P, "Back" to BACK_P).forEach { (label, parts) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    var w by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
                    val tap = if (onTap == null) Modifier else Modifier.pointerInput(parts) {
                        detectTapGestures { o -> onTap(regionAt(parts, o.x * 200f / w, o.y * 200f / w)) }
                    }
                    Canvas(Modifier.fillMaxWidth().aspectRatio(200f / 430f).then(tap)) {
                        w = size.width; val k = size.width / 200f
                        scale(k, k, pivot = Offset.Zero) {
                            SIL_P.forEach { drawPath(it, p.s2) }
                            parts.forEach { (m, paths) ->
                                val c = shades[(levels[m] ?: 0).coerceIn(0, shades.lastIndex)]
                                paths.forEach { path ->
                                    drawPath(path, c)
                                    drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)))
                                }
                            }
                            parts.forEach { (m, paths) ->
                                paths.forEach { path -> drawPath(path, if (m == sel) p.ink else p.bg, style = Stroke(width = if (m == sel) 2.4f else 0.9f)) }
                            }
                        }
                    }
                    Mu(label, size = 13.sp)
                }
            }
        }
    }
}

/** Which region of [parts] contains the point (in the 200 × 430 drawing box), if any. */
private fun regionAt(parts: Map<String, List<Path>>, x: Float, y: Float): String? {
    val dot = Path().apply { addOval(androidx.compose.ui.geometry.Rect(x - 1.5f, y - 1.5f, x + 1.5f, y + 1.5f)) }
    return parts.entries.lastOrNull { (_, ps) -> ps.any { pth -> !Path().apply { op(pth, dot, androidx.compose.ui.graphics.PathOperation.Intersect) }.isEmpty } }?.key
}

/** A key for a shaded map: a swatch per level with its label. */
@Composable
fun MapLegend(shades: List<Color>, labels: List<String>) {
    FlowRowX(Modifier.padding(top = 8.dp)) {
        labels.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(shades[i], androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(5.dp)); Mu(l, size = 12.sp)
            }
        }
    }
}

/** Training balance across the six main groups: this period filled, the one before as an outline. */
@Composable
fun BalanceRadar(cur: Map<String, Int>, prev: Map<String, Int>, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val axes = listOf("Back", "Chest", "Core", "Shoulders", "Arms", "Legs")
    val mx = maxOf(1, axes.maxOf { maxOf(cur[it] ?: 0, prev[it] ?: 0) }).toFloat()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.ui.text.TextStyle(color = p.mu, fontSize = 12.sp)
    Canvas(modifier.fillMaxWidth().aspectRatio(1.25f)) {
        val c = Offset(size.width / 2, size.height / 2 + 4.dp.toPx()); val r = minOf(size.width, size.height) * 0.36f
        fun pt(i: Int, f: Float): Offset { val a = (-90.0 + 60.0 * i) * kotlin.math.PI / 180; return Offset(c.x + (r * f * kotlin.math.cos(a)).toFloat(), c.y + (r * f * kotlin.math.sin(a)).toFloat()) }
        fun poly(f: (Int) -> Float) = androidx.compose.ui.graphics.Path().apply { axes.indices.forEach { i -> val q = pt(i, f(i)); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }; close() }
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { g -> drawPath(poly { g }, p.ln, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
        axes.indices.forEach { i -> drawLine(p.ln, c, pt(i, 1f), 1.dp.toPx()) }
        drawPath(poly { (prev[axes[it]] ?: 0) / mx }, p.mu, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
        val cp = poly { (cur[axes[it]] ?: 0) / mx }
        drawPath(cp, p.ac.copy(alpha = 0.28f)); drawPath(cp, p.ac, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        axes.forEachIndexed { i, a ->
            val t = measurer.measure(a, style); val q = pt(i, 1.2f)
            drawText(t, topLeft = Offset(q.x - t.size.width / 2, q.y - t.size.height / 2))
        }
    }
}

/** "Muscles worked" for one workout: the body map and credited sets per muscle (helpers count half). */
@Composable
fun MusclesWorked(cnt: Map<String, Double>) {
    if (cnt.isEmpty()) return
    val p = LocalPal.current; val shades = volumeShades()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Txt("Muscles worked", size = 17.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        BodyMap(cnt.mapValues { app.liftlog.logic.sessionLevel(it.value) }, shades, maxWidth = 240.dp)
        val mx = maxOf(1.0, cnt.values.maxOrNull() ?: 0.0)
        cnt.forEach { (m, n) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(m, Modifier.width(100.dp), size = 15.sp)
                Box(Modifier.weight(1f).height(10.dp).background(p.s2, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxWidth((n / mx).toFloat()).fillMaxHeight().background(shades[app.liftlog.logic.sessionLevel(n)], androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
                }
                Txt(app.liftlog.logic.setsLabel(n), Modifier.width(40.dp), size = 15.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Mu("Sets per muscle; muscles that help count half a set.", Modifier.padding(top = 6.dp), size = 13.sp)
    }
}
