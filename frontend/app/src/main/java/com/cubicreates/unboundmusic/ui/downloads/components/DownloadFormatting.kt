/*
 * Package: com.cubicreates.unboundmusic.ui.downloads.components
 * File: DownloadFormatting.kt
 * Purpose: Formats byte sizes and transfer speeds for download UI.
 */

package com.cubicreates.unboundmusic.ui.downloads.components

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1000.0) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1000.0) {
        String.format("%.1f MB/s", kb / 1024.0)
    } else {
        String.format("%.0f KB/s", kb)
    }
}
