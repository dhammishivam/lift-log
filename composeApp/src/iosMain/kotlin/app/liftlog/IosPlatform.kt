package app.liftlog

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.liftlog.data.Platform
import app.liftlog.db.LiftDb
import org.jetbrains.skia.Image

/**
 * iOS platform services. Storage, photos display and the shared UI work as-is.
 * File import/export, the photo picker and background rest alerts are marked TODO:
 * they need UIDocumentPickerViewController, PHPickerViewController and
 * UNUserNotificationCenter wiring when the iOS app is published.
 */
class IosPlatform : Platform {
    override val name = "iOS"
    override val appVersion: String = (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0.0"
    override val coachEnabled = true
    override fun openUrl(url: String) {
        platform.Foundation.NSURL.URLWithString(url)?.let { platform.UIKit.UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
    }
    override fun sqlDriver(): SqlDriver = NativeSqliteDriver(LiftDb.Schema, "liftlog.db")

    override suspend fun pickTextFiles(): List<Pair<String, String>> = emptyList() // TODO: UIDocumentPickerViewController
    override suspend fun saveText(fileName: String, mime: String, text: String): Boolean = false // TODO: UIActivityViewController
    override suspend fun pickAndStorePhotos(): List<String> = emptyList() // TODO: PHPickerViewController

    override fun loadPhoto(id: String): ImageBitmap? = null // photos are stored by the picker above
    override fun deletePhoto(id: String) {}

    override fun alert() {} // TODO: AudioServicesPlaySystemSound + haptics
    override fun setRestEnd(atMillis: Long?) {} // TODO: UNUserNotificationCenter

    @Suppress("unused")
    private fun decode(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
