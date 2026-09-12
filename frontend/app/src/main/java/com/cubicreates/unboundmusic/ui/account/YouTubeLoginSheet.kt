/*
 * Package: com.cubicreates.unboundmusic.ui.account
 * File: YouTubeLoginSheet.kt
 * Purpose: Modal bottom sheet housing an embedded Android WebView for Google / YouTube authentication and cookie extraction.
 * Subsystem: Native Account UI
 * Concurrency: Thread-safe UI component using Compose state.
 */

package com.cubicreates.unboundmusic.ui.account

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// Full OAuth / ServiceLogin URL following proven SimpMusic pattern:
// Logs into Google ServiceLogin with youtube service & music template, handles the signin on youtube.com,
// and redirects straight to music.youtube.com where full session cookies are minted.
private const val LOG_IN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F%26feature%3D__FEATURE__&hl=en"

private const val MODERN_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/**
 * Combines multiple cookie strings into a single deduplicated semicolon-delimited cookie header.
 */
private fun mergeCookieStrings(vararg cookieStrings: String?): String {
    val map = LinkedHashMap<String, String>()
    cookieStrings.filterNotNull().forEach { cookieStr ->
        cookieStr.split(";").forEach { part ->
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val k = trimmed.substring(0, eq).trim()
                val v = trimmed.substring(eq + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) {
                    map[k] = v
                }
            }
        }
    }
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginSheet(
    onDismiss: () -> Unit,
    onCookieExtracted: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isLoading by remember { mutableStateOf(true) }
    var hasExtracted by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }

    fun doExtractCookies() {
        if (hasExtracted) return
        val cookieManager = CookieManager.getInstance()
        val ytmCookie = cookieManager.getCookie("https://music.youtube.com") ?: ""
        val ytCookie = cookieManager.getCookie("https://youtube.com") ?: ""
        val merged = mergeCookieStrings(ytmCookie, ytCookie)
        if (merged.contains("SAPISID=") || merged.contains("__Secure-3PAPISID=")) {
            hasExtracted = true
            onCookieExtracted(merged)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0A0A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(Color(0xFF0A0A0A))
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connect YouTube Music",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (currentUrl.contains("music.youtube.com")) "Signed in! Tap Sync Now or wait for auto-sync" else "Sign in with your Google account to sync your music",
                        color = if (currentUrl.contains("music.youtube.com")) Color(0xFF00FF66) else Color(0xFF888888),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Explicit manual sync button whenever on YouTube Music domain
                if (currentUrl.contains("music.youtube.com")) {
                    Button(
                        onClick = { doExtractCookies() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = Color.Black,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = MODERN_MOBILE_USER_AGENT
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let { currentUrl = it }

                                    // Only extract cookies once navigation reaches music.youtube.com
                                    if (url != null && (url.startsWith("https://music.youtube.com") || url.contains("music.youtube.com"))) {
                                        doExtractCookies()
                                    }
                                }
                            }

                            loadUrl(LOG_IN_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00FF66),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }
    }
}
