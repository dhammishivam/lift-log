package app.liftlog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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
import app.liftlog.logic.weeklyLevel
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
/** One shape of the artwork: its path, bounds, and the fibre angle (degrees) if it is a muscle. */
private class Piece(val path: Path, val b: androidx.compose.ui.geometry.Rect, val fib: Float?)

/** Fibre direction per muscle (degrees, y down) on the figure's left half; the right half mirrors it. */
private val FIB = mapOf("Chest" to 15f, "Abs" to 90f, "Obliques" to 45f, "Front delts" to 80f, "Side delts" to 85f, "Rear delts" to 80f,
    "Biceps" to 100f, "Triceps" to 100f, "Forearms" to 105f, "Traps" to -27f, "Quads" to 80f, "Adductors" to 112f, "Abductors" to 100f,
    "Calves" to 90f, "Hamstrings" to 88f, "Glutes" to 140f, "Lats" to 59f, "Upper back" to 17f, "Lower back" to 90f)

/** A side of the body map with its paths parsed once. */
private class ParsedView(val v: BodyView) {
    private fun parse(d: String) = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath()
    private val cx get() = v.vb[0] + v.vb[2] / 2
    private fun piece(d: String, m: String?): Piece {
        val pth = parse(d); val b = pth.getBounds()
        val f = m?.let { FIB[it] }?.let { if (b.center.x < cx) it else 180f - it }
        return Piece(pth, b, f?.let { kotlin.math.round((it - 90f) / 5f) * 5f })
    }
    val baseParts: List<Piece> by lazy { v.base.map { piece(it, null) } }
    val base: Path by lazy { Path().apply { baseParts.forEach { addPath(it.path) } } }
    val pieces: Map<String, List<Piece>> by lazy { v.regions.mapValues { (m, ds) -> ds.map { piece(it, m) } } }
    val regions: Map<String, List<Path>> by lazy { pieces.mapValues { (_, ps) -> ps.map { it.path } } }
}

/** Light from the upper left: a soft highlight that falls off into a darker rim, so each shape reads as rounded. */
private fun DrawScope.volume(pc: Piece) {
    val b = pc.b; val r = maxOf(1f, 0.8f * (b.width + b.height) / 2f)
    drawPath(pc.path, Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.28f), 0.42f to Color.White.copy(alpha = 0f), 0.78f to Color.Black.copy(alpha = 0.16f), 1f to Color.Black.copy(alpha = 0.42f),
        center = Offset(b.left + b.width * 0.4f, b.top + b.height * 0.3f), radius = r))
}

/** Muscle fibres: fine dark lines with a faint light edge, clipped to the shape and run along the muscle. */
private fun DrawScope.fibres(pc: Piece) {
    val a = pc.fib ?: return; val b = pc.b; val c = b.center
    val rr = kotlin.math.sqrt(b.width * b.width + b.height * b.height) / 2f + 12f
    clipPath(pc.path) {
        rotate(a, c) {
            var x = c.x - rr
            while (x < c.x + rr) {
                drawLine(Color.Black.copy(alpha = 0.2f), Offset(x, c.y - rr), Offset(x, c.y + rr), 2.4f)
                drawLine(Color.White.copy(alpha = 0.07f), Offset(x + 5.5f, c.y - rr), Offset(x + 5.5f, c.y + rr), 1.4f)
                x += 11f
            }
        }
    }
}

private val FRONT_V by lazy { ParsedView(BodyPaths.front) }
private val BACK_V by lazy { ParsedView(BodyPaths.back) }
/** Side delts have no shape of their own in the artwork, so they show through the front and rear delts. */
private val ALSO = mapOf("Front delts" to "Side delts", "Rear delts" to "Side delts")

/** Shades for levels 0–4 of the volume map: untrained, then light to strong; 4 is above the productive range. */
@Composable
fun volumeShades(): List<Color> {
    val p = LocalPal.current
    return listOf(lerp(p.s2, p.ink, 0.12f), lerp(p.s2, p.ac, 0.3f), lerp(p.s2, p.ac, 0.62f), p.ac, lerp(p.ac, Color.White, 0.45f))
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
fun BodyMap(levels: Map<String, Int>, shades: List<Color>, maxWidth: androidx.compose.ui.unit.Dp = 460.dp, sel: String? = null, onTap: ((String?) -> Unit)? = null) {
    val p = LocalPal.current
    fun lv(r: String) = maxOf(levels[r] ?: 0, ALSO[r]?.let { levels[it] } ?: 0).coerceIn(0, shades.lastIndex)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
            listOf("Front" to FRONT_V, "Back" to BACK_V).forEach { (label, pv) ->
                val vb = pv.v.vb
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    var w by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
                    val tap = if (onTap == null) Modifier else Modifier.pointerInput(pv) {
                        detectTapGestures { o -> val k = vb[2] / w; onTap(regionAt(pv.regions, vb[0] + o.x * k, vb[1] + o.y * k)) }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(vb[2] / vb[3])) {
                        // A soft shadow under the figure lifts it off the page.
                        Canvas(Modifier.matchParentSize().offset(y = 3.dp).blur(8.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)) {
                            val k = size.width / vb[2]
                            scale(k, k, pivot = Offset.Zero) { translate(-vb[0], -vb[1]) {
                                drawPath(pv.base, Color.Black.copy(alpha = 0.55f)); pv.regions.values.forEach { ps -> ps.forEach { drawPath(it, Color.Black.copy(alpha = 0.55f)) } }
                            } }
                        }
                        Canvas(Modifier.matchParentSize().then(tap)) {
                            w = size.width; val k = size.width / vb[2]
                            scale(k, k, pivot = Offset.Zero) {
                                translate(-vb[0], -vb[1]) {
                                    drawPath(pv.base, lerp(p.s2, p.ink, 0.22f))
                                    pv.baseParts.forEach { volume(it) }
                                    pv.pieces.forEach { (m, ps) ->
                                        val c = shades[lv(m)]
                                        ps.forEach { pc -> drawPath(pc.path, c); fibres(pc); volume(pc); drawPath(pc.path, p.bg, style = Stroke(width = 1.6f)) }
                                    }
                                    if (sel != null) pv.pieces.forEach { (m, ps) ->
                                        if (m == sel || ALSO[m] == sel) ps.forEach { drawPath(it.path, p.ink, style = Stroke(width = 7f)) }
                                    }
                                }
                            }
                        }
                    }
                    Mu(label, size = 13.sp)
                }
            }
        }
    }
}

/** Which region contains the point (in the artwork's coordinates), if any. */
private fun regionAt(parts: Map<String, List<Path>>, x: Float, y: Float): String? {
    val dot = Path().apply { addOval(androidx.compose.ui.geometry.Rect(x - 4f, y - 4f, x + 4f, y + 4f)) }
    return parts.entries.lastOrNull { (_, ps) -> ps.any { pth -> !Path().apply { op(pth, dot, androidx.compose.ui.graphics.PathOperation.Intersect) }.isEmpty } }?.key
}

/** A key for a shaded map: a swatch per level with its label. */
@Composable
fun MapLegend(shades: List<Color>, labels: List<String>) {
    Spacer(Modifier.height(8.dp))
    FlowRowX {
        labels.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(shades[i], androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(5.dp)); Mu(l, size = 12.sp)
            }
        }
    }
    Spacer(Modifier.height(6.dp)); Mu(BODY_CREDIT, size = 10.sp)
}

/** The body artwork is MIT-licensed; its licence asks for this notice. */
const val BODY_CREDIT = "Body art: react-native-body-highlighter © 2022 ELABBASSI Hicham (MIT)"


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

/** Weekly sets for one muscle as bars, with the 10–20 sets growth range shaded behind them. */
@Composable
fun WeekBars(weeks: List<Double>, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val top = maxOf(22.0, weeks.maxOrNull() ?: 0.0).toFloat()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.ui.text.TextStyle(color = p.mu, fontSize = 10.sp)
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val h = size.height - 14.dp.toPx(); val n = weeks.size; val gap = 4.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
        fun y(v: Float) = h - v / top * h
        drawRect(lerp(p.s2, p.ok, 0.14f), Offset(0f, y(20f)), androidx.compose.ui.geometry.Size(size.width, y(10f) - y(20f)))
        weeks.forEachIndexed { i, v ->
            val x = i * (bw + gap); val lvl = weeklyLevel(v)
            val c = if (v <= 0.0) p.s2 else if (lvl >= 3) p.ac else lerp(p.s2, p.ac, 0.5f)
            val top1 = if (v <= 0.0) h - 3f else y(v.toFloat())
            drawRoundRect(c, Offset(x, top1), androidx.compose.ui.geometry.Size(bw, h - top1), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
        drawText(measurer, "${n} wk ago", Offset(0f, h + 2.dp.toPx()), style)
        val t = measurer.measure("This week", style); drawText(t, topLeft = Offset(size.width - t.size.width, h + 2.dp.toPx()))
        val g = measurer.measure("10–20", style); drawText(g, topLeft = Offset(size.width - g.size.width - 2.dp.toPx(), y(20f) + 1.dp.toPx()))
    }
}

private val BACK_SIDE = setOf("Lats", "Upper back", "Lower back", "Rear delts", "Triceps", "Hamstrings", "Glutes", "Calves", "Abductors")

/** Where a muscle thumbnail looks: the view and the area around the muscles an exercise works most. */
private class ThumbFocus(val pv: ParsedView, val area: androidx.compose.ui.geometry.Rect)

private fun thumbFocus(t: Map<String, Double>): ThumbFocus? {
    if (t.isEmpty()) return null
    val top = t.values.max(); val main = t.filterValues { it >= top }.keys
    val pv = if (main.count { it in BACK_SIDE } > main.size / 2) BACK_V else FRONT_V
    // Side delts have no shape of their own: aim at the delts they show through.
    val shown = main.flatMap { r -> listOf(r) + ALSO.filterValues { it == r }.keys }
    // Aim at the figure's left side: a whole pair (both biceps) would zoom out too far. Midline shapes count too.
    val mid = pv.v.vb[0] + pv.v.vb[2] / 2
    val all = shown.flatMap { pv.pieces[it].orEmpty() }.map { it.b }
    val rects = all.filter { it.center.x < mid + 20f }.ifEmpty { all }
    if (rects.isEmpty()) return null
    return ThumbFocus(pv, rects.reduce { a, b -> androidx.compose.ui.geometry.Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) })
}

/**
 * A small picture of the body zoomed onto what an exercise works: the main muscle in the accent colour, helpers
 * lighter. Cardio and other exercises with no muscle map show a heart instead.
 */
@Composable
fun MuscleThumb(x: app.liftlog.model.Exercise, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val t = androidx.compose.runtime.remember(x.id, x.name, x.muscle) { app.liftlog.logic.muscleTargets(x) }
    val f = androidx.compose.runtime.remember(t) { thumbFocus(t) }
    if (f == null) { Box(modifier, contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Ic.heart, null, Modifier.size(20.dp), tint = p.mu) }; return }
    val top = t.values.max()
    Canvas(modifier) {
        val a = f.area; val side = maxOf(a.width, a.height, 200f) * 1.5f
        val k = size.width / side
        scale(k, k, pivot = Offset.Zero) {
            translate(-(a.center.x - side / 2), -(a.center.y - side / 2)) {
                drawPath(f.pv.base, lerp(p.s2, p.ink, 0.26f))
                f.pv.pieces.forEach { (m, ps) ->
                    val w = maxOf(t[m] ?: 0.0, ALSO[m]?.let { t[it] } ?: 0.0)
                    val c = when { w >= top -> p.ac; w > 0 -> lerp(p.s2, p.ac, 0.5f); else -> lerp(p.s2, p.ink, 0.14f) }
                    ps.forEach { pc -> drawPath(pc.path, c); drawPath(pc.path, p.s2, style = Stroke(width = 3f)) }
                }
            }
        }
    }
}
