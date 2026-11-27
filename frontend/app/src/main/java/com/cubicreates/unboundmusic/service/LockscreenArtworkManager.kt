/*
 * Package: com.cubicreates.unboundmusic.service
 * File: LockscreenArtworkManager.kt
 * Purpose: Dynamic lockscreen wallpaper manager that backs up the user's original lockscreen
 *          wallpaper (including shared Home+Lock wallpapers) into Go Backend SQLite on launch
 *          and restores that exact wallpaper when music stops or the app closes.
 * Subsystem: Lock Screen Visuals
 * Concurrency: Thread-safe via ioLock + generationCounter; never calls WallpaperManager.clear().
 */

package com.cubicreates.unboundmusic.service

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Looper
import android.util.Log
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.BackendClient
import com.cubicreates.unboundmusic.data.PlaybackStateStore
import com.cubicreates.unboundmusic.data.downloadWallpaperBackupBytesBlocking
import com.cubicreates.unboundmusic.data.getWallpaperBackupMetaBlocking
import com.cubicreates.unboundmusic.data.updateWallpaperBackupIdsBlocking
import com.cubicreates.unboundmusic.data.uploadWallpaperBackupBlocking
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicLong

object LockscreenArtworkManager {

    private const val TAG = "LockscreenArtwork"
    private const val PREFS_NAME = "unbound_lockscreen_prefs"
    private const val KEY_ORIGINAL_BACKUP_EXISTS = "key_orig_backup_exists"
    private const val KEY_IS_ARTWORK_APPLIED = "key_is_artwork_applied"
    private const val KEY_SAVED_LOCK_ID = "key_saved_lock_id"
    private const val KEY_SAVED_SYSTEM_ID = "key_saved_system_id"
    private const val BACKUP_FILE_NAME = "original_lock_wallpaper.bak"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ioLock = Any()
    private val generationCounter = AtomicLong(0L)

    @Volatile
    private var lastAppliedArtworkUrl: String? = null

    @Volatile
    private var isWallpaperApplied = false

    private data class CapturedWallpaper(
        val bytes: ByteArray,
        val mimeType: String,
        val sourceFlag: String,
        val lockId: Long,
        val systemId: Long
    )

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns true if Unbound Music has currently replaced the lockscreen wallpaper with album art.
     */
    fun isArtworkCurrentlyApplied(context: Context): Boolean {
        return isWallpaperApplied || getPrefs(context).getBoolean(KEY_IS_ARTWORK_APPLIED, false)
    }

    /**
     * Captures the user's current original lockscreen wallpaper into Go SQLite (and synced local mirror)
     * on app launch or after permission changes, ONLY when Unbound's dynamic artwork is not currently active.
     */
    fun captureOriginalWallpaperOnLaunch(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            synchronized(ioLock) {
                try {
                    val client = DaemonManager.getInstance(appCtx).client
                    if (isArtworkCurrentlyApplied(appCtx)) {
                        // Never capture Unbound's own album art as the user's original wallpaper!
                        if (!PlaybackStateStore.isPlaying(appCtx)) {
                            restoreOriginalWallpaperLocked(appCtx, client, timeoutMs = 3000L)
                        }
                        return@synchronized
                    }
                    syncOriginalWallpaperToSqliteLocked(appCtx, client, forceCaptureIfMissing = true)
                } catch (e: Throwable) {
                    Log.w(TAG, "captureOriginalWallpaperOnLaunch note: ${e.message}")
                }
            }
        }
    }

    private fun syncOriginalWallpaperToSqliteLocked(
        appCtx: Context,
        client: BackendClient,
        forceCaptureIfMissing: Boolean
    ): Boolean {
        val prefs = getPrefs(appCtx)
        val backupFile = File(appCtx.filesDir, BACKUP_FILE_NAME)
        val wallpaperManager = WallpaperManager.getInstance(appCtx)
        val currentLockId = getWallpaperIdCompat(wallpaperManager, WallpaperManager.FLAG_LOCK)
        val currentSysId = getWallpaperIdCompat(wallpaperManager, WallpaperManager.FLAG_SYSTEM)

        var meta = client.getWallpaperBackupMetaBlocking()

        // Promote any existing local backup into Go SQLite first
        if ((meta == null || !meta.hasBackup) && backupFile.exists() && backupFile.length() > 0L) {
            val localBytes = runCatching { backupFile.readBytes() }.getOrNull()
            if (localBytes != null && localBytes.isNotEmpty()) {
                val uploaded = client.uploadWallpaperBackupBlocking(
                    bytes = localBytes,
                    mimeType = "image/png",
                    sourceFlag = "migrated_local",
                    lockId = prefs.getLong(KEY_SAVED_LOCK_ID, currentLockId),
                    systemId = prefs.getLong(KEY_SAVED_SYSTEM_ID, currentSysId)
                )
                if (uploaded != null && uploaded.hasBackup) {
                    meta = uploaded
                }
            }
        }

        if (meta != null && meta.hasBackup) {
            val savedLockId = meta.lockWallpaperId
            val savedSysId = meta.systemWallpaperId
            val localLockId = prefs.getLong(KEY_SAVED_LOCK_ID, -2L)
            val localSysId = prefs.getLong(KEY_SAVED_SYSTEM_ID, -2L)

            val unchangedVsSqlite = savedLockId != -2L &&
                currentLockId == savedLockId &&
                currentSysId == savedSysId
            val unchangedVsLocal = localLockId != -2L &&
                currentLockId == localLockId &&
                currentSysId == localSysId

            if (unchangedVsSqlite || unchangedVsLocal) {
                if (!backupFile.exists() || backupFile.length() == 0L) {
                    val sqliteBytes = client.downloadWallpaperBackupBytesBlocking(3000L)
                    if (sqliteBytes != null && sqliteBytes.isNotEmpty()) {
                        writeBackupMirrorAtomic(backupFile, sqliteBytes)
                    }
                }
                prefs.edit().putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, true).commit()
                return true
            }
            Log.i(TAG, "Detected user changed phone wallpaper externally (lockId $savedLockId->$currentLockId, sysId $savedSysId->$currentSysId); refreshing SQLite backup.")
        } else if (!forceCaptureIfMissing && backupFile.exists() && backupFile.length() > 0L) {
            return true
        }

        val captured = captureCurrentUserWallpaperBytes(appCtx, wallpaperManager, currentLockId, currentSysId)
        if (captured != null && captured.bytes.isNotEmpty()) {
            writeBackupMirrorAtomic(backupFile, captured.bytes)
            val uploaded = client.uploadWallpaperBackupBlocking(
                bytes = captured.bytes,
                mimeType = captured.mimeType,
                sourceFlag = captured.sourceFlag,
                lockId = captured.lockId,
                systemId = captured.systemId
            )
            prefs.edit()
                .putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, true)
                .putLong(KEY_SAVED_LOCK_ID, captured.lockId)
                .putLong(KEY_SAVED_SYSTEM_ID, captured.systemId)
                .commit()
            Log.i(
                TAG,
                "Original lockscreen wallpaper backed up (${captured.bytes.size} bytes, source=${captured.sourceFlag}, sqlite=${uploaded?.hasBackup == true})."
            )
            return true
        }

        val hasMirror = backupFile.exists() && backupFile.length() > 0L
        if (!hasMirror && meta?.hasBackup != true) {
            Log.w(TAG, "Unable to read current user wallpaper from OS; skipping lockscreen overwrite to protect custom wallpaper.")
        }
        return hasMirror || (meta?.hasBackup == true)
    }

    @SuppressLint("MissingPermission")
    private fun captureCurrentUserWallpaperBytes(
        appCtx: Context,
        wallpaperManager: WallpaperManager,
        lockId: Long,
        systemId: Long
    ): CapturedWallpaper? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Tier 1: Separate custom lock-screen wallpaper file
            readWallpaperPfd(wallpaperManager, WallpaperManager.FLAG_LOCK)?.let { bytes ->
                return CapturedWallpaper(
                    bytes = bytes,
                    mimeType = detectImageMime(bytes),
                    sourceFlag = "lock",
                    lockId = lockId,
                    systemId = systemId
                )
            }
            // Tier 2: Shared "Home and Lock screens" custom wallpaper file
            readWallpaperPfd(wallpaperManager, WallpaperManager.FLAG_SYSTEM)?.let { bytes ->
                return CapturedWallpaper(
                    bytes = bytes,
                    mimeType = detectImageMime(bytes),
                    sourceFlag = "system_shared",
                    lockId = lockId,
                    systemId = systemId
                )
            }
        }

        // Tier 3: Drawable fallback (peekDrawable / drawable)
        val drawable: Drawable? = try {
            wallpaperManager.peekDrawable() ?: wallpaperManager.drawable
        } catch (e: Throwable) {
            Log.w(TAG, "Drawable wallpaper read note: ${e.message}")
            null
        }
        if (drawable != null) {
            val metrics = appCtx.resources.displayMetrics
            val targetW = drawable.intrinsicWidth.takeIf { it > 0 } ?: metrics.widthPixels.coerceAtLeast(720)
            val targetH = drawable.intrinsicHeight.takeIf { it > 0 } ?: metrics.heightPixels.coerceAtLeast(1280)
            val bmp = Bitmap.createBitmap(targetW.coerceAtMost(2160), targetH.coerceAtMost(3840), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty()) {
                return CapturedWallpaper(
                    bytes = bytes,
                    mimeType = "image/png",
                    sourceFlag = "drawable",
                    lockId = lockId,
                    systemId = systemId
                )
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun readWallpaperPfd(wallpaperManager: WallpaperManager, flag: Int): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            val pfd = wallpaperManager.getWallpaperFile(flag) ?: return null
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    val bytes = input.readBytes()
                    if (bytes.isNotEmpty()) bytes else null
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "getWallpaperFile(flag=$flag) failed (${e.message})")
            null
        }
    }

    private fun getWallpaperIdCompat(wallpaperManager: WallpaperManager, flag: Int): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return -1L
        return try {
            wallpaperManager.getWallpaperId(flag).toLong()
        } catch (_: Throwable) {
            -1L
        }
    }

    private fun detectImageMime(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        return "image/png"
    }

    private fun writeBackupMirrorAtomic(targetFile: File, bytes: ByteArray) {
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tmpFile.outputStream().use { it.write(bytes) }
        if (!tmpFile.renameTo(targetFile)) {
            targetFile.writeBytes(bytes)
            tmpFile.delete()
        }
    }

    /**
     * Downloads, formats, and projects the track's album artwork onto the phone's lock screen.
     */
    fun updateLockscreenArtwork(context: Context, track: TrackItem?) {
        val appCtx = context.applicationContext
        if (track == null || track.coverUrl.isBlank()) {
            restoreOriginalLockscreenArtwork(appCtx, synchronous = false)
            return
        }

        if (!PlaybackStateStore.isLockscreenWallpaperEnabled(appCtx)) {
            if (isArtworkCurrentlyApplied(appCtx)) {
                restoreOriginalLockscreenArtwork(appCtx, synchronous = false)
            }
            return
        }

        val coverUrl = track.coverUrl
        if (coverUrl == lastAppliedArtworkUrl && isWallpaperApplied) {
            return
        }

        val myGen = generationCounter.incrementAndGet()
        scope.launch {
            try {
                val wallpaperManager = WallpaperManager.getInstance(appCtx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !wallpaperManager.isSetWallpaperAllowed) {
                    Log.w(TAG, "Wallpaper changes are restricted on this device.")
                    return@launch
                }

                // 1. Fetch artwork bitmap using Coil
                val request = ImageRequest.Builder(appCtx)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()

                val result = Coil.imageLoader(appCtx).execute(request)
                if (result !is SuccessResult) {
                    Log.w(TAG, "Failed to load artwork bitmap for lock screen: $coverUrl")
                    return@launch
                }

                val sourceBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (sourceBitmap == null || sourceBitmap.isRecycled) {
                    return@launch
                }

                synchronized(ioLock) {
                    // Abort if playback paused/stopped/closed or a newer track/restore superseded this request
                    if (myGen != generationCounter.get() ||
                        !PlaybackStateStore.isLockscreenWallpaperEnabled(appCtx) ||
                        !PlaybackStateStore.isPlaying(appCtx)
                    ) {
                        return@synchronized
                    }

                    val client = DaemonManager.getInstance(appCtx).client
                    val hasVerifiedBackup = if (isArtworkCurrentlyApplied(appCtx)) {
                        true
                    } else {
                        syncOriginalWallpaperToSqliteLocked(appCtx, client, forceCaptureIfMissing = true)
                    }

                    // Hard invariant: never overwrite FLAG_LOCK unless original wallpaper is safely backed up
                    if (!hasVerifiedBackup) {
                        Log.w(TAG, "Aborting lockscreen artwork update because original wallpaper could not be backed up.")
                        return@synchronized
                    }

                    val metrics = appCtx.resources.displayMetrics
                    val screenWidth = metrics.widthPixels.coerceAtLeast(720)
                    val screenHeight = metrics.heightPixels.coerceAtLeast(1280)
                    val composite = createCompositeLockscreenBitmap(sourceBitmap, screenWidth, screenHeight)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(composite, null, true, WallpaperManager.FLAG_LOCK)
                    } else {
                        wallpaperManager.setBitmap(composite)
                    }
                    composite.recycle()

                    getPrefs(appCtx).edit().putBoolean(KEY_IS_ARTWORK_APPLIED, true).commit()
                    lastAppliedArtworkUrl = coverUrl
                    isWallpaperApplied = true
                    Log.i(TAG, "Lock screen wallpaper successfully updated for '${track.title}'.")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not update lock screen wallpaper: ${e.message}")
            }
        }
    }

    /**
     * Restores the user's saved original lock-screen wallpaper from Go SQLite (with local mirror fallback).
     * Never deletes the SQLite backup and never calls WallpaperManager.clear().
     */
    fun restoreOriginalLockscreenArtwork(context: Context, synchronous: Boolean = false) {
        val appCtx = context.applicationContext
        generationCounter.incrementAndGet()
        if (!isArtworkCurrentlyApplied(appCtx)) {
            return
        }

        val action = { timeoutMs: Long ->
            synchronized(ioLock) {
                try {
                    val client = DaemonManager.getInstance(appCtx).client
                    restoreOriginalWallpaperLocked(appCtx, client, timeoutMs)
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not restore lock screen wallpaper: ${e.message}")
                }
            }
        }

        if (synchronous) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                val t = Thread({ action(500L) }, "LockscreenRestoreThread")
                t.start()
                try {
                    t.join(1500L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            } else {
                action(600L)
            }
        } else {
            scope.launch { action(3000L) }
        }
    }

    private fun restoreOriginalWallpaperLocked(appCtx: Context, client: BackendClient, timeoutMs: Long) {
        if (!isArtworkCurrentlyApplied(appCtx)) {
            return
        }

        val prefs = getPrefs(appCtx)
        val backupFile = File(appCtx.filesDir, BACKUP_FILE_NAME)
        val wallpaperManager = WallpaperManager.getInstance(appCtx)

        // 1. Primary source: Go Backend SQLite
        var originalBytes = client.downloadWallpaperBackupBytesBlocking(timeoutMs)
        if (originalBytes != null && originalBytes.isNotEmpty()) {
            if (!backupFile.exists() || backupFile.length() == 0L) {
                runCatching { writeBackupMirrorAtomic(backupFile, originalBytes!!) }
            }
        } else if (backupFile.exists() && backupFile.length() > 0L) {
            // 2. Emergency fallback: synced local mirror during daemon/process teardown
            originalBytes = runCatching { backupFile.readBytes() }.getOrNull()
        }

        if (originalBytes != null && originalBytes.isNotEmpty()) {
            var restored = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    ByteArrayInputStream(originalBytes).use { bis ->
                        wallpaperManager.setStream(bis, null, true, WallpaperManager.FLAG_LOCK)
                    }
                    restored = true
                } catch (streamErr: Throwable) {
                    Log.w(TAG, "setStream restore failed (${streamErr.message}); retrying via decoded Bitmap.")
                    val bmp = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                    if (bmp != null) {
                        try {
                            wallpaperManager.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
                            restored = true
                        } finally {
                            bmp.recycle()
                        }
                    }
                }
            } else {
                ByteArrayInputStream(originalBytes).use { bis ->
                    wallpaperManager.setStream(bis)
                }
                restored = true
            }

            if (restored) {
                val newLockId = getWallpaperIdCompat(wallpaperManager, WallpaperManager.FLAG_LOCK)
                val newSysId = getWallpaperIdCompat(wallpaperManager, WallpaperManager.FLAG_SYSTEM)
                prefs.edit()
                    .putBoolean(KEY_IS_ARTWORK_APPLIED, false)
                    .putBoolean(KEY_ORIGINAL_BACKUP_EXISTS, true)
                    .putLong(KEY_SAVED_LOCK_ID, newLockId)
                    .putLong(KEY_SAVED_SYSTEM_ID, newSysId)
                    .commit()
                lastAppliedArtworkUrl = null
                isWallpaperApplied = false
                runCatching { client.updateWallpaperBackupIdsBlocking(newLockId, newSysId) }
                Log.i(TAG, "Successfully restored original lockscreen wallpaper (${originalBytes.size} bytes, newLockId=$newLockId).")
                return
            }
        }

        Log.w(TAG, "Original wallpaper bytes unavailable for restore; leaving lockscreen untouched (never calling clear()).")
        prefs.edit().putBoolean(KEY_IS_ARTWORK_APPLIED, false).commit()
        lastAppliedArtworkUrl = null
        isWallpaperApplied = false
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
        val artTop = targetHeight * 0.38f - (artSize / 2f)
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#404CD6FB")
        }

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(artwork, null, artRect, fgPaint)
        canvas.drawRoundRect(artRect, 16f, 16f, borderPaint)

        return composite
    }
}
