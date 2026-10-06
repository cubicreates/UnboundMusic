/*
 * Package: com.cubicreates.unboundmusic.service
 * File: LockscreenArtworkManager.kt
 * Purpose: Dynamic lockscreen wallpaper manager that projects high-fidelity album art
 *          onto the Android lock screen (WallpaperManager.FLAG_LOCK) during playback,
 *          and restores system defaults when music stops.
 * Subsystem: Lock Screen Visuals
 * Concurrency: Thread-safe; all Bitmap transformations and WallpaperManager I/O run on Dispatchers.IO.
 */

package com.cubicreates.unboundmusic.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cubicreates.unboundmusic.data.PlaybackStateStore
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileInputStream

object LockscreenArtworkManager {

    private const val TAG = "LockscreenArtwork"
    private const val PREFS_NAME = "unbound_lockscreen_prefs"
    private const val KEY_ORIGINAL_BACKUP_EXISTS = "key_orig_backup_exists"
    private const val KEY_IS_ARTWORK_APPLIED = "key_is_artwork_applied"
    private const val BACKUP_FILE_NAME = "original_lock_wallpaper.bak"

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var lastAppliedArtworkUrl: String? = null

    @Volatile
    private var isWallpaperApplied = false

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns true if Unbound Music has currently replaced the lockscreen wallpaper with album art.
     */
    fun isArtworkCurrentlyApplied(context: Context): Boolean {
        return isWallpaperApplied || getPrefs(context).getBoolean(KEY_IS_ARTWORK_APPLIED, false)
    }

    /**
     * Backs up the user's current lock screen wallpaper to private app storage before any album
     * art is applied, guaranteeing it can be restored when music stops, the app closes, or RAM is cleared.
     */
    @Synchronized
    private fun backupOriginalWallpaperIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val backupFile = File(context.filesDir, BACKUP_FILE_NAME)

        // If album art is already currently active or a backup already exists, never overwrite!
        if (isWallpaperApplied || prefs.getBoolean(KEY_IS_ARTWORK_APPLIED, false) || backupFile.exists()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                val pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK)
                if (pfd != null) {
                    pfd.use { descriptor ->
                        FileInputStream(descriptor.fileDescriptor).use { input ->
                            backupFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    prefs.edit()
                        .putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, true)
                        .apply()
                    Log.i(TAG, "Original custom lock screen wallpaper backed up (${backupFile.length()} bytes).")
                } else {
                    // No distinct custom lock wallpaper file set (lock screen uses system/home default)
                    prefs.edit()
                        .putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, false)
                        .apply()
                    Log.i(TAG, "No separate custom lock screen wallpaper; system default will be restored on cleanup.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not read original lock wallpaper file (${e.message}), will clear() on restore.")
                prefs.edit().putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, false).apply()
            }
        } else {
            prefs.edit().putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, false).apply()
        }
    }

    /**
     * Downloads, formats, and projects the track's album artwork onto the phone's lock screen.
     */
    fun updateLockscreenArtwork(context: Context, track: TrackItem?) {
        if (track == null || track.coverUrl.isBlank()) {
            restoreOriginalLockscreenArtwork(context, synchronous = false)
            return
        }

        if (!PlaybackStateStore.isLockscreenWallpaperEnabled(context)) {
            if (isArtworkCurrentlyApplied(context)) {
                restoreOriginalLockscreenArtwork(context, synchronous = false)
            }
            return
        }

        val coverUrl = track.coverUrl
        if (coverUrl == lastAppliedArtworkUrl && isWallpaperApplied) {
            return // Avoid duplicate wallpaper writes for the same song
        }

        scope.launch {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !wallpaperManager.isSetWallpaperAllowed) {
                    Log.w(TAG, "Wallpaper changes are restricted on this device.")
                    return@launch
                }

                // 1. Fetch artwork bitmap using Coil
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false) // Needed for software Canvas drawing
                    .build()

                val result = Coil.imageLoader(context).execute(request)
                if (result !is SuccessResult) {
                    Log.w(TAG, "Failed to load artwork bitmap for lock screen: $coverUrl")
                    return@launch
                }

                val drawable = result.drawable
                val sourceBitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (sourceBitmap == null || sourceBitmap.isRecycled) {
                    return@launch
                }

                // 2. Ensure user's original wallpaper is safely backed up before overwriting
                backupOriginalWallpaperIfNeeded(context)

                // 3. Determine phone screen dimensions
                val metrics = context.resources.displayMetrics
                val screenWidth = metrics.widthPixels.coerceAtLeast(720)
                val screenHeight = metrics.heightPixels.coerceAtLeast(1280)

                // 4. Render Studio Brutalist Lock Screen Composition
                val composite = createCompositeLockscreenBitmap(sourceBitmap, screenWidth, screenHeight)

                // 5. Apply specifically to the lock screen only (WallpaperManager.FLAG_LOCK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wallpaperManager.setBitmap(
                        composite,
                        null,
                        true,
                        WallpaperManager.FLAG_LOCK
                    )
                } else {
                    wallpaperManager.setBitmap(composite)
                }

                getPrefs(context).edit().putBoolean(KEY_IS_ARTWORK_APPLIED, true).apply()
                lastAppliedArtworkUrl = coverUrl
                isWallpaperApplied = true
                Log.i(TAG, "Lock screen wallpaper successfully updated for '${track.title}'.")
            } catch (e: Throwable) {
                Log.w(TAG, "Could not update lock screen wallpaper: ${e.message}")
            }
        }
    }

    /**
     * Restores the phone's original lock screen wallpaper.
     * If a custom photo was backed up before playback, restores that exact photo;
     * otherwise clears FLAG_LOCK so Android automatically restores the system default / home screen wallpaper.
     */
    fun restoreOriginalLockscreenArtwork(context: Context, synchronous: Boolean = false) {
        val prefs = getPrefs(context)
        val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
        val isApplied = isWallpaperApplied || prefs.getBoolean(KEY_IS_ARTWORK_APPLIED, false)
        if (!isApplied && !backupFile.exists()) {
            return
        }

        val restoreAction = {
            synchronized(this) {
                try {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val hasBackup = prefs.getBoolean(KEY_ORIGINAL_BACKUP_EXISTS, false) && backupFile.exists() && backupFile.length() > 0

                    if (hasBackup && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            FileInputStream(backupFile).use { fis ->
                                wallpaperManager.setStream(fis, null, true, WallpaperManager.FLAG_LOCK)
                            }
                            Log.i(TAG, "Successfully restored original custom lock screen wallpaper from backup.")
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed to restore custom wallpaper stream (${e.message}); clearing lock layer.")
                            wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                        } else {
                            wallpaperManager.clear()
                        }
                        Log.i(TAG, "Cleared lock screen wallpaper, restored system default.")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not restore lock screen wallpaper: ${e.message}")
                } finally {
                    try {
                        if (backupFile.exists()) backupFile.delete()
                    } catch (_: Throwable) {}

                    prefs.edit()
                        .putBoolean(KEY_IS_ARTWORK_APPLIED, false)
                        .putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, false)
                        .apply()

                    lastAppliedArtworkUrl = null
                    isWallpaperApplied = false
                }
            }
        }

        if (synchronous) {
            restoreAction()
        } else {
            scope.launch { restoreAction() }
        }
    }

    /**
     * Backward-compatible alias for [restoreOriginalLockscreenArtwork].
     */
    fun clearLockscreenArtwork(context: Context, synchronous: Boolean = false) {
        restoreOriginalLockscreenArtwork(context, synchronous)
    }

    /**
     * Creates an OLED-optimized wallpaper:
     * - Darkened background matching display aspect ratio
     * - Centered high-resolution album square with soft vignette and corner curvature
     * - Ample negative space so system clock and notifications remain completely legible.
     */
    private fun createCompositeLockscreenBitmap(
        artwork: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val composite = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)

        // 1. Dark ambient background fill
        canvas.drawColor(Color.parseColor("#0A0C0E"))

        // 2. Draw scaled background backdrop with heavy darkening for readability
        val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 75 // ~30% opacity
        }
        val bgDestRect = Rect(0, 0, targetWidth, targetHeight)
        canvas.drawBitmap(artwork, null, bgDestRect, bgPaint)

        // 3. Dark gradient scrim to protect lock screen clock and notifications
        val scrimPaint = Paint().apply {
            color = Color.parseColor("#99050709") // 60% dark overlay
        }
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), scrimPaint)

        // 4. Centered Artwork Dimensions (scaled to ~70% screen width)
        val artSize = (targetWidth * 0.72f).toInt()
        val artLeft = (targetWidth - artSize) / 2f
        // Positioned slightly above center (at ~42% height) to leave room for clock and unlock slider
        val artTop = targetHeight * 0.38f - (artSize / 2f)
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)

        // Subtle drop shadow / border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#404CD6FB") // 25% neon cyan accent
        }

        // Draw crisp foreground artwork
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(artwork, null, artRect, fgPaint)
        canvas.drawRoundRect(artRect, 16f, 16f, borderPaint)

        return composite
    }
}
