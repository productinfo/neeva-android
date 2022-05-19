package com.neeva.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.WorkerThread
import com.neeva.app.browsing.FileEncrypter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.chromium.weblayer.Tab

/**
 * Manages thumbnails for each tab to display in the tab switcher.  These thumbnails are created by
 * WebLayer and persisted into our cache directory.
 */
abstract class TabScreenshotManager(filesDir: File) {
    companion object {
        private val TAG = TabScreenshotManager::class.simpleName
        private const val DIRECTORY_TAB_SCREENSHOTS = "tab_screenshots"

        // Copied from https://source.chromium.org/chromium/chromium/src/+/main:weblayer/browser/tab_impl.h;drc=242da5037807dde3daf097ba74f875db83b8b613;l=76
        enum class ScreenshotErrors {
            NONE,
            SCALE_OUT_OF_RANGE,
            TAB_NOT_ACTIVE,
            WEB_CONTENTS_NOT_VISIBLE,
            NO_SURFACE,
            NO_RENDER_WIDGET_HOST_VIEW,
            NO_WINDOW_ANDROID,
            EMPTY_VIEWPORT,
            HIDDEN_BY_CONTROLS,
            SCALED_TO_EMPTY,
            CAPTURE_FAILED,
            BITMAP_ALLOCATION_FAILED
        }
    }

    private val tabScreenshotDirectory = File(filesDir, DIRECTORY_TAB_SCREENSHOTS)

    fun getTabScreenshotFile(guid: String) = File(tabScreenshotDirectory, "tab_$guid.jpg")
    fun getTabScreenshotFile(tab: Tab) = getTabScreenshotFile(tab.guid)

    /** Takes a screenshot of the given [tab]. */
    fun captureAndSaveScreenshot(tab: Tab?, onCompleted: () -> Unit = {}) {
        if (tab == null || tab.isDestroyed || tab.navigationController.navigationListSize == 0) {
            onCompleted()
            return
        }

        val tabGuid = tab.guid
        val captureStack = Throwable()

        tab.captureScreenShot(0.5f) { thumbnail, errorCode ->
            if (errorCode != 0) {
                val errorName = ScreenshotErrors.values().getOrNull(errorCode)?.name
                Log.w(
                    TAG,
                    "Failed to create tab thumbnail: Tab=$tab, Error=$errorCode $errorName",
                    captureStack
                )
                onCompleted()
                return@captureScreenShot
            }

            val file = getTabScreenshotFile(tabGuid)
            if (file.exists()) file.delete()

            BitmapIO.saveBitmap(tabScreenshotDirectory, file, ::getOutputStream) {
                thumbnail?.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }

            onCompleted()
        }
    }

    @WorkerThread
    fun cleanCacheDirectory(liveTabGuids: List<String>) {
        val liveTabFiles = liveTabGuids.map { guid -> getTabScreenshotFile(guid) }

        try {
            val dir = tabScreenshotDirectory
            if (!dir.exists()) return

            dir.listFiles()
                ?.filterNot { liveTabFiles.contains(it) }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup tab screenshot directory", e)
        }
    }

    @WorkerThread
    fun deleteScreenshot(tabId: String) {
        try {
            val file = getTabScreenshotFile(tabId)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete thumbnail for $tabId", e)
        }
    }

    @WorkerThread
    fun restoreScreenshot(tabId: String): Bitmap? {
        val file = getTabScreenshotFile(tabId)
        return BitmapIO.loadBitmap(file, ::getInputStream)
    }

    abstract fun getInputStream(file: File): InputStream
    abstract fun getOutputStream(file: File): OutputStream
}

/** Caches unencrypted screenshots of tabs. */
class RegularTabScreenshotManager(filesDir: File) : TabScreenshotManager(filesDir) {
    override fun getInputStream(file: File) = FileInputStream(file)
    override fun getOutputStream(file: File) = FileOutputStream(file)
}

/** Caches screenshots of tabs and encrypts them so that they can't be accessed by outside apps. */
class IncognitoTabScreenshotManager(
    appContext: Context,
    filesDir: File
) : TabScreenshotManager(filesDir) {
    private val encrypter: FileEncrypter = FileEncrypter(appContext)

    override fun getInputStream(file: File): InputStream = encrypter.getInputStream(file)
    override fun getOutputStream(file: File): OutputStream = encrypter.getOutputStream(file)
}
