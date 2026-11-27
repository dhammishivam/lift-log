package app.liftlog

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.liftlog.data.LiveInfo
import app.liftlog.data.Platform
import app.liftlog.logic.ShareCard
import app.liftlog.logic.WidgetInfo
import app.liftlog.db.LiftDb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class AndroidPlatform(private val act: ComponentActivity) : Platform {
    override val name = "Android"
    override val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    override val coachEnabled = BuildConfig.COACH
    override fun openUrl(url: String) {
        runCatching { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    private val ctx: Context get() = act.applicationContext

    private var openDocs: CompletableDeferred<List<Uri>>? = null
    private var createDoc: CompletableDeferred<Uri?>? = null
    private var pickMedia: CompletableDeferred<List<Uri>>? = null

    private val openLauncher = act.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { openDocs?.complete(it) }
    private val csvLauncher = act.registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { createDoc?.complete(it) }
    private val jsonLauncher = act.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { createDoc?.complete(it) }
    private val mediaLauncher = act.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { pickMedia?.complete(it) }
    private val permLauncher = act.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun sqlDriver(): SqlDriver = AndroidSqliteDriver(LiftDb.Schema, ctx, "liftlog.db")

    override suspend fun pickTextFiles(): List<Pair<String, String>> {
        val d = CompletableDeferred<List<Uri>>(); openDocs = d
        openLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream", "application/vnd.ms-excel"))
        val uris = d.await()
        return withContext(Dispatchers.IO) {
            uris.mapNotNull { u ->
                runCatching {
                    val name = u.lastPathSegment?.substringAfterLast('/') ?: "file"
                    name to (ctx.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } ?: "")
                }.getOrNull()
            }
        }
    }

    override suspend fun saveText(fileName: String, mime: String, text: String): Boolean {
        val d = CompletableDeferred<Uri?>(); createDoc = d
        if (mime == "application/json") jsonLauncher.launch(fileName) else csvLauncher.launch(fileName)
        val uri = d.await() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) }; true }.getOrDefault(false)
        }
    }

    private val photoDir get() = File(ctx.filesDir, "photos").apply { mkdirs() }

    override suspend fun pickAndStorePhotos(): List<String> {
        val d = CompletableDeferred<List<Uri>>(); pickMedia = d
        mediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        val uris = d.await()
        return withContext(Dispatchers.IO) { uris.mapNotNull { storePhoto(it) } }
    }

    /** Downscales to 1440px on the long side, keeps orientation, saves as JPEG. */
    private fun storePhoto(uri: Uri): String? = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1440) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = runCatching {
            val ex = androidx.exifinterface.media.ExifInterface(bytes.inputStream())
            when (ex.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        }.getOrDefault(0f)
        val k = minOf(1f, 1440f / maxOf(bmp.width, bmp.height))
        if (k < 1f || rot != 0f) {
            val m = Matrix().apply { postScale(k, k); postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val id = UUID.randomUUID().toString() + ".jpg"
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        File(photoDir, id).writeBytes(out.toByteArray()); id
    }.getOrNull()

    override fun loadPhoto(id: String): ImageBitmap? = runCatching {
        val f = File(photoDir, id); if (!f.exists()) return null
        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    }.getOrNull()

    override fun deletePhoto(id: String) { runCatching { File(photoDir, id).delete() } }

    override fun alert() {
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
        }
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP2, 400) }
    }

    /* ---------- rest alert while the app is in the background ---------- */
    private var restEnd: Long? = null
    private var foreground = true
    private val prefs by lazy { ctx.getSharedPreferences("liftlog", Context.MODE_PRIVATE) }

    /** Asks once for notification permission (rest alerts and the workout notification need it). */
    private fun askNotifications() {
        if (!prefs.getBoolean("askedNotif", false) && Build.VERSION.SDK_INT >= 33 &&
            act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("askedNotif", true).apply(); runCatching { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    override fun setRestEnd(atMillis: Long?) {
        restEnd = atMillis
        if (atMillis != null) askNotifications()
        if (!foreground) schedule()
    }

    override fun celebrate() {
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 35, 70, 60), -1))
        }
    }

    override fun liveWorkout(info: LiveInfo?) {
        if (info != null) askNotifications()
        LiveNotifier.show(ctx, info)
    }

    override fun setActionHandler(handler: (String) -> Unit) = LiveBridge.attach(handler)

    override fun updateWidget(info: WidgetInfo) { runCatching { LiftWidget.save(ctx, info) } }

    override val receivesShares: Boolean get() = true
    override val devBuild: Boolean get() = act.packageName.endsWith(".dev")
    // Builds without google-services.json have no Firebase app, so getInstance() throws: then there is nothing to switch.
    override fun setCrashReports(on: Boolean) { runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = on } }

    override fun openApp(pkg: String, link: String?): Boolean {
        // A deep link first (it lands closer to the right screen), then the app's normal start screen.
        if (link != null && runCatching {
                act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)).setPackage(pkg)); true
            }.getOrDefault(false)) return true
        val i = act.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { act.startActivity(i); true }.getOrDefault(false)
    }

    override fun shareCard(card: ShareCard): Boolean = ShareImage.share(act, card)
    override fun shareText(text: String, title: String): Boolean = runCatching {
        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title)); true
    }.getOrDefault(false)

    override fun mascot(kind: String): ImageBitmap? {
        val res = when (kind) { "titan" -> R.drawable.mascot_titan; "classic" -> R.drawable.mascot_classic; "pump" -> R.drawable.mascot_pump; "champ" -> R.drawable.mascot_champ; else -> return null }
        return runCatching { BitmapFactory.decodeResource(act.resources, res)?.asImageBitmap() }.getOrNull()
    }
    /** Rest is over: a buzz in the app, or the lock-screen "Rest over" alert when Lift Log is in the background. */
    override fun restOver() {
        if (foreground) alert() else { cancel(); RestOver.post(ctx) }
    }
    fun onForeground() { foreground = true; cancel(); RestOver.clear(ctx) }
    fun onBackground() { foreground = false; schedule() }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, RestAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun schedule() {
        val at = restEnd ?: return
        if (at <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
        }
    }
    private fun cancel() { runCatching { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending()) } }
}

/** Posts "Rest over" when the rest timer ends while Lift Log isn't running its own timer (e.g. it was stopped). */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestOver.post(context)
}
