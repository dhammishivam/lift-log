package app.liftlog.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.liftlog.logic.nowMs
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** A record moment: which mascot shows, the headline, what was beaten and a line from the mascot. */
data class Celebration(val mascot: String, val title: String, val line: String, val quip: String, val id: Long = nowMs())

/** Lift Log's own mascots: original characters, one per kind of record. */
object Mascots {
    private val QUIPS = mapOf(
        "titan" to listOf("The bar bent. You didn't.", "Heavier than ever. The plates fear you.", "New weight unlocked. ROAR!"),
        "classic" to listOf("Strength like the golden days.", "Timeless power. Hit a pose!", "That 1RM belongs in a museum."),
        "pump" to listOf("That pump is going on the vlog!", "More reps, more gains, more content.", "Volume king! Hit subscribe on yourself."),
        "champ" to listOf("Champion's day! Take a bow.", "Records broken. Trophy earned.", "That workout belongs on a stage."),
    )

    /** Heaviest weight → Titan, best 1RM → Classic, everything else (volume, reps, time, distance) → Pump. */
    fun forRecord(key: String) = when (key) { "w" -> "titan"; "orm" -> "classic"; else -> "pump" }
    fun quip(m: String) = QUIPS[m]?.random() ?: ""
}

@Composable
fun CelebrationOverlay(st: AppState) {
    val c = st.celebration ?: return
    val p = LocalPal.current
    LaunchedEffect(c.id) { delay(3400); if (st.celebration?.id == c.id) st.celebration = null }
    val img = remember(c.mascot) { st.platform.mascot(c.mascot) }

    val pop = remember(c.id) { Animatable(0.2f) }
    LaunchedEffect(c.id) { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 320f)) }
    val inf = rememberInfiniteTransition(label = "wiggle")
    val wiggle by inf.animateFloat(-5f, 5f, infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rot")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).noRippleTap { st.celebration = null }, contentAlignment = Alignment.Center) {
        Confetti(c.id)
        Column(
            Modifier.padding(28.dp).widthIn(max = 360.dp).fillMaxWidth()
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFF17191C)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (img != null) Image(img, null, Modifier.size(220.dp).clip(RoundedCornerShape(24.dp))
                .graphicsLayer { rotationZ = wiggle; translationY = -10f * bob; val s = 1f + 0.04f * bob; scaleX = s; scaleY = s })
            Txt(c.title, Modifier.padding(top = 14.dp), size = 24.sp, weight = FontWeight.Bold, color = p.pr, align = TextAlign.Center)
            Txt(c.line, Modifier.padding(top = 4.dp), size = 16.sp, weight = FontWeight.SemiBold, color = Color(0xFFF3F5F8), align = TextAlign.Center)
            if (c.quip.isNotEmpty()) Txt("“${c.quip}”", Modifier.padding(top = 8.dp), size = 15.sp, color = Color(0xFF9097A2), align = TextAlign.Center)
        }
    }
}

private class Bit(val x: Float, val delay: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/** Gold, blue and party-coloured confetti falling for about three seconds. */
@Composable
private fun Confetti(key: Long) {
    val colors = listOf(Color(0xFFD99A00), Color(0xFFFFD34D), Color(0xFF3D84FF), Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFF9B7BF5))
    val bits = remember(key) { List(70) { Bit(Random.nextFloat(), Random.nextFloat() * 0.5f, 0.35f + Random.nextFloat() * 0.45f, Random.nextFloat() * 2 - 1, Random.nextFloat() * 720 - 360, colors.random(), 6f + Random.nextFloat() * 8, 10f + Random.nextFloat() * 10) } }
    var t by remember(key) { mutableStateOf(0f) }
    LaunchedEffect(key) { val s = withFrameNanos { it }; while (t < 4f) { withFrameNanos { n -> t = (n - s) / 1e9f } } }
    Canvas(Modifier.fillMaxSize()) {
        bits.forEach { b ->
            val tt = t - b.delay; if (tt <= 0f) return@forEach
            val y = -30f + tt * b.speed * size.height
            if (y > size.height + 30f) return@forEach
            val x = b.x * size.width + sin((tt * 3 + b.x * 10) * PI.toFloat()) * 18f * b.drift
            rotate(b.spin * tt, Offset(x, y)) { drawRect(b.color, Offset(x - b.w / 2, y - b.h / 2), Size(b.w, b.h)) }
        }
    }
}
