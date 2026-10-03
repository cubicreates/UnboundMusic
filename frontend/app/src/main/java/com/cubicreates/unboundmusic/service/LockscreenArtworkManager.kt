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

object LockscreenArtworkManager {

    private const val TAG = "LockscreenArtwork"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var lastAppliedArtworkUrl: String? = null

    @Volatile
    private var isWallpaperApplied = false

    /**
     * Downloads, formats, and projects the track's album artwork onto the phone's lock screen.
     */
    fun updateLockscreenArtwork(context: Context, track: TrackItem?) {
        if (track == null || track.coverUrl.isBlank()) {
            clearLockscreenArtwork(context)
            return
        }

        if (!PlaybackStateStore.isLockscreenWallpaperEnabled(context)) {
            if (isWallpaperApplied) {
                clearLockscreenArtwork(context)
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

                // 2. Determine phone screen dimensions
                val metrics = context.resources.displayMetrics
                val screenWidth = metrics.widthPixels.coerceAtLeast(720)
                val screenHeight = metrics.heightPixels.coerceAtLeast(1280)

                // 3. Render Studio Brutalist Lock Screen Composition
                val composite = createCompositeLockscreenBitmap(sourceBitmap, screenWidth, screenHeight)

                // 4. Apply specifically to the lock screen only (WallpaperManager.FLAG_LOCK)
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

                lastAppliedArtworkUrl = coverUrl
                isWallpaperApplied = true
                Log.i(TAG, "Lock screen wallpaper successfully updated for '${track.title}'.")
            } catch (e: Throwable) {
                Log.w(TAG, "Could not update lock screen wallpaper: ${e.message}")
            }
        }
    }

    /**
     * Clears the custom lock screen wallpaper, restoring the user's system wallpaper.
     */
    fun clearLockscreenArtwork(context: Context) {
        if (!isWallpaperApplied && lastAppliedArtworkUrl == null) return

        scope.launch {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                } else {
                    wallpaperManager.clear()
                }
                lastAppliedArtworkUrl = null
                isWallpaperApplied = false
                Log.i(TAG, "Lock screen wallpaper restored to system default.")
            } catch (e: Throwable) {
                Log.w(TAG, "Could not clear lock screen wallpaper: ${e.message}")
            }
        }
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
