package app.liftlog

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.FileProvider
import app.liftlog.data.LiveInfo
import app.liftlog.logic.ShareCard
import app.liftlog.logic.WidgetInfo
import app.liftlog.logic.count
import app.liftlog.logic.widgetNumbers
import java.io.File

/**
 * Taps on the notification and the widget reach the app through here. They run on the main thread.
 * A tap that arrives before the app has loaded its data waits until [attach].
 */
object LiveBridge {
    const val EXTRA = "liftlog_action"
    private var handler: ((String) -> Unit)? = null
    private val pending = mutableListOf<String>()

    /** Returns false when the app isn't running, so there is nothing to act on. */
    fun send(action: String): Boolean {
        val h = handler ?: run { pending += action; return false }
        h(action); return true
    }
    fun attach(h: (String) -> Unit) { handler = h; val p = pending.toList(); pending.clear(); p.forEach(h) }
    fun detach() { handler = null }
}

/* ---------- "workout in progress" notification ---------- */

object LiveNotifier {
    private const val CHANNEL = "live"
    const val ID = 8

    private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun show(ctx: Context, i: LiveInfo?) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (i == null) { nm.cancel(ID); return }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Workout time, rest timer and the next set while you train"; setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(ctx, 20, Intent(ctx, MainActivity::class.java).putExtra(LiveBridge.EXTRA, "open")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags())
        fun act(label: String, action: String, rc: Int) = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_stat_timer), label,
            PendingIntent.getBroadcast(ctx, rc, Intent(ctx, LiveActionReceiver::class.java).putExtra(LiveBridge.EXTRA, action), flags()),
        ).build()
        val resting = i.restEnd != null && i.restEnd > System.currentTimeMillis()
        val text = i.next?.let { "Next: $it" } ?: if (i.total > 0) "All ${i.total} sets done. Finish when you're ready." else "Add an exercise to get going."
        val b = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (resting) "Resting · ${i.title}" else i.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSubText(if (i.total > 0) "${i.done} of ${count(i.total, "set")}" else null)
            .setOngoing(true).setOnlyAlertOnce(true).setAutoCancel(false)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setShowWhen(true).setUsesChronometer(true)
        if (resting) {
            b.setWhen(i.restEnd!!).setChronometerCountDown(true)
            b.addAction(act("−15s", "minus", 21)); b.addAction(act("+15s", "plus", 22)); b.addAction(act("Skip rest", "skip", 23))
        } else {
            b.setWhen(i.start)
            if (i.next != null) b.addAction(act("Complete set", "done", 24))
        }
        if (i.total > 0) b.setProgress(i.total, i.done, false)
        // Android 16: show as a Live Update (status bar chip with the timer).
        if (Build.VERSION.SDK_INT >= 36) runCatching { b.setRequestPromotedOngoing(true) }
        runCatching { nm.notify(ID, b.build()) }
    }
}

/** Buttons on the notification. If the app has been closed there is no workout state in memory to change. */
class LiveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.getStringExtra(LiveBridge.EXTRA) ?: return
        if (!LiveBridge.send(a)) LiveNotifier.show(context, null)
    }
}

/* ---------- home screen widget ---------- */

class LiftWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = render(context, manager, appWidgetIds)

    companion object {
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

        /** Stores what the widget needs (so it can redraw on its own, e.g. when a new week starts) and redraws it. */
        fun save(ctx: Context, i: WidgetInfo) {
            prefs(ctx).edit()
                .putString("weeks", i.weeks.joinToString(","))
                .putString("recent", i.recent.joinToString(";") { "${it.first}:${it.second}" })
                .putInt("goal", i.goal ?: 0)
                .putLong("active", i.activeStart ?: 0L)
                .putString("rid", i.nextRoutineId).putString("rname", i.nextRoutineName)
                .apply()
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, LiftWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, m, ids)
        }

        private fun open(ctx: Context, rc: Int, action: String) = PendingIntent.getActivity(ctx, rc,
            Intent(ctx, MainActivity::class.java).putExtra(LiveBridge.EXTRA, action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val p = prefs(ctx); val now = System.currentTimeMillis()
            val weeks = p.getString("weeks", "")!!.split(',').mapNotNull { it.toLongOrNull() }
            val recent = p.getString("recent", "")!!.split(';').mapNotNull { s -> s.split(':').takeIf { it.size == 2 }?.let { (a, b) -> a.toLongOrNull()?.let { x -> b.toLongOrNull()?.let { y -> x to y } } } }
            val n = widgetNumbers(weeks, recent, now)
            val goal = p.getInt("goal", 0); val active = p.getLong("active", 0L)
            val rid = p.getString("rid", null); val rname = p.getString("rname", null)
            val v = RemoteViews(ctx.packageName, R.layout.widget_lift)
            v.setTextViewText(R.id.w_big, if (goal > 0) "${n.workouts} of $goal" else count(n.workouts, "workout"))
            val mins = if (n.minutes >= 60) "${n.minutes / 60}h ${n.minutes % 60}m" else "${n.minutes}m"
            v.setTextViewText(R.id.w_sub, (if (goal > 0) "workouts this week" else "this week") + (if (n.minutes > 0) " · $mins" else ""))
            v.setTextViewText(R.id.w_streak, if (n.streak > 0) "🔥 ${n.streak}-week streak" else "Start a streak this week")
            if (active > 0) {
                v.setTextViewText(R.id.w_btn_text, "Resume workout")
                v.setViewVisibility(R.id.w_chrono, View.VISIBLE)
                v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() - (now - active), null, true)
                v.setOnClickPendingIntent(R.id.w_btn, open(ctx, 31, "open"))
            } else {
                v.setViewVisibility(R.id.w_chrono, View.GONE)
                v.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime(), null, false)
                if (rid != null && rname != null) {
                    v.setTextViewText(R.id.w_btn_text, "Start $rname")
                    v.setOnClickPendingIntent(R.id.w_btn, open(ctx, 31, "start:$rid"))
                } else {
                    v.setTextViewText(R.id.w_btn_text, "Start workout")
                    v.setOnClickPendingIntent(R.id.w_btn, open(ctx, 31, "empty"))
                }
            }
            v.setOnClickPendingIntent(R.id.w_root, open(ctx, 30, "home"))
            ids.forEach { m.updateAppWidget(it, v) }
        }
    }
}

/* ---------- share image ---------- */

object ShareImage {
    private const val W = 1080; private const val H = 1350

    fun share(act: Activity, c: ShareCard): Boolean = runCatching {
        val bmp = draw(c)
        val dir = File(act.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "lift-log-workout.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "${c.title}: ${c.duration}, ${c.volume}. Logged with Lift Log.")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        act.startActivity(Intent.createChooser(send, "Share workout"))
        true
    }.getOrDefault(false)

    fun draw(c: ShareCard): Bitmap {
        val bg = if (c.dark) 0xFF0D0E10.toInt() else 0xFFFFFFFF.toInt()
        val s1 = if (c.dark) 0xFF17191C.toInt() else 0xFFF2F3F5.toInt()
        val ink = if (c.dark) 0xFFF3F5F8.toInt() else 0xFF101215.toInt()
        val mu = if (c.dark) 0xFF9097A2.toInt() else 0xFF5F6670.toInt()
        val ac = if (c.dark) 0xFF3D84FF.toInt() else 0xFF1764FF.toInt()
        val pr = 0xFFD99A00.toInt()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.drawColor(bg)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fun tp(size: Float, color: Int, b: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (b) bold else Typeface.DEFAULT; textAlign = align
        }
        fun text(s: String, x: Float, y: Float, p: TextPaint, max: Float) = cv.drawText(TextUtils.ellipsize(s, p, max, TextUtils.TruncateAt.END).toString(), x, y, p)
        val L = 72f; val R = W - 72f; val inner = R - L

        // Accent bar and brand
        cv.drawRect(0f, 0f, W.toFloat(), 14f, Paint().apply { color = ac })
        text("LIFT LOG", L, 118f, tp(34f, ac, true).apply { letterSpacing = 0.18f }, inner)
        text(c.title, L, 210f, tp(72f, ink, true), inner)
        text(c.date, L, 268f, tp(36f, mu), inner)

        // Stats card
        val card = RectF(L, 312f, R, 498f)
        cv.drawRoundRect(card, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s1 })
        val stats = listOf("Duration" to c.duration, "Volume" to c.volume, "Sets" to c.sets, "Records" to c.records.size.toString())
        val cw = inner / stats.size
        stats.forEachIndexed { k, (label, value) ->
            val x = L + cw * k + 34f
            text(label, x, 380f, tp(30f, mu), cw - 40f)
            text(value, x, 452f, tp(52f, if (label == "Records" && c.records.isNotEmpty()) pr else ink, true), cw - 40f)
        }
        var y = 560f
        if (c.compare.isNotEmpty()) { text(c.compare, L, y, tp(34f, mu), inner); y += 70f } else y += 10f

        // Exercises: name on the left, best set on the right
        val maxLines = if (c.records.isNotEmpty()) 5 else 7
        val shown = c.lines.take(maxLines); val extra = c.more + (c.lines.size - shown.size)
        val nameP = tp(38f, ink, true); val detP = tp(32f, mu, align = Paint.Align.RIGHT)
        shown.forEach { l ->
            text(l.name, L, y, nameP, inner * 0.55f)
            text(l.detail, R, y, detP, inner * 0.44f)
            y += 68f
        }
        if (extra > 0) { text("and ${count(extra, "more exercise")}", L, y, tp(32f, mu), inner); y += 68f }

        // Records
        if (c.records.isNotEmpty()) {
            y += 12f
            val top = y - 52f; val lines = c.records.take(3)
            val box = RectF(L, top, R, top + 92f + lines.size * 56f)
            cv.drawRoundRect(box, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (pr and 0x00FFFFFF) or 0x24000000 })
            text("🏆 " + (if (c.records.size == 1) "New record" else "${c.records.size} new records"), L + 32f, top + 62f, tp(38f, pr, true), inner - 64f)
            lines.forEachIndexed { k, r -> text("${r.exercise} · ${r.what}", L + 32f, top + 122f + k * 56f, tp(32f, ink), inner - 64f) }
        }

        text("Logged with Lift Log", W / 2f, H - 64f, tp(30f, mu, align = Paint.Align.CENTER), inner)
        return bmp
    }
}
