/*
 * Package: com.cubicreates.unboundmusic.ui.account
 * File: YouTubeLoginSheet.kt
 * Purpose: Full-screen modal housing embedded Android WebView for Google / YouTube authentication,
 *          live loading progress indicator, automatic cookie extraction, and manual cookie paste fallback.
 * Subsystem: Native Account UI / SimpMusic Architecture Elevation
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

// Full OAuth / ServiceLogin URL following SimpMusic's proven authentication pattern:
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
    onCookieExtracted: (String) -> Unit,
    onAccountAuthenticated: ((cookie: String, accountName: String, avatarUrl: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var hasExtracted by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var showManualCookieDialog by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    fun finishExtraction(mergedCookie: String, accountName: String = "", avatarUrl: String = "") {
        if (hasExtracted) return
        hasExtracted = true
        if (onAccountAuthenticated != null) {
            onAccountAuthenticated(mergedCookie, accountName, avatarUrl)
        } else {
            onCookieExtracted(mergedCookie)
        }
        onDismiss()
    }

    fun doExtractCookies() {
        if (hasExtracted) return
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val ytmCookie = cookieManager.getCookie("https://music.youtube.com/") ?: ""
        val wwwYtCookie = cookieManager.getCookie("https://www.youtube.com/") ?: ""
        val ytCookie = cookieManager.getCookie("https://youtube.com/") ?: ""
        // Keep music.youtube.com cookies highest priority so account/domain tokens from
        // www.youtube.com or accounts.google.com never overwrite YouTube Music's active session.
        var merged = mergeCookieStrings(ytCookie, wwwYtCookie, ytmCookie)
        if (!merged.contains("SAPISID=") &&
            !merged.contains("__Secure-1PAPISID=") &&
            !merged.contains("__Secure-3PAPISID=")
        ) {
            val googleCookie = cookieManager.getCookie("https://accounts.google.com/") ?: ""
            merged = mergeCookieStrings(googleCookie, ytCookie, wwwYtCookie, ytmCookie)
        }

        if (merged.contains("SAPISID=") ||
            merged.contains("__Secure-1PAPISID=") ||
            merged.contains("__Secure-3PAPISID=") ||
            merged.contains("SID=")
        ) {
            val wv = webViewInstance
            if (wv != null && onAccountAuthenticated != null) {
                var handled = false
                val fallbackHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val fallbackRunnable = Runnable {
                    if (!handled) {
                        handled = true
                        finishExtraction(merged)
                    }
                }
                fallbackHandler.postDelayed(fallbackRunnable, 650L)

                val jsScript = """
                    (function() {
                        try {
                            var img = document.querySelector('ytmusic-settings-button img, #avatar-btn img, yt-img-shadow#img img');
                            var avatar = img ? (img.src || '') : '';
                            var btn = document.querySelector('ytmusic-settings-button, #avatar-btn');
                            var label = '';
                            if (btn) {
                                label = btn.getAttribute('aria-label') || '';
                            }
                            if (!label && img && img.alt) label = img.alt;
                            if (label && /^(account|open account|profile|menu|settings)/i.test(label.trim())) label = '';
                            return JSON.stringify({ name: (label || '').trim(), avatar: (avatar || '').trim() });
                        } catch (e) {
                            return '{}';
                        }
                    })();
                """.trimIndent()

                wv.evaluateJavascript(jsScript) { rawJson ->
                    if (handled) return@evaluateJavascript
                    handled = true
                    fallbackHandler.removeCallbacks(fallbackRunnable)
                    var extractedName = ""
                    var extractedAvatar = ""
                    try {
                        val cleaned = rawJson?.trim()?.let {
                            if (it.startsWith("\"") && it.endsWith("\"")) {
                                org.json.JSONTokener(it).nextValue().toString()
                            } else {
                                it
                            }
                        } ?: "{}"
                        val obj = org.json.JSONObject(cleaned)
                        extractedName = obj.optString("name", "").trim()
                        extractedAvatar = obj.optString("avatar", "").trim()
                    } catch (_: Throwable) {}
                    finishExtraction(merged, extractedName, extractedAvatar)
                }
            } else {
                finishExtraction(merged)
            }
        }
    }

    // Direct Manual Paste Dialog (SimpMusic DevLogInBottomSheet counterpart)
    if (showManualCookieDialog) {
        ManualCookiePasteDialog(
            onDismiss = { showManualCookieDialog = false },
            onCookieSubmitted = { rawCookie ->
                showManualCookieDialog = false
                hasExtracted = true
                onCookieExtracted(rawCookie)
                onDismiss()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = UnboundBackground,
        contentColor = OnSurface
    ) {
        val isAuthenticatedDomain = currentUrl.contains("music.youtube.com")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .background(UnboundBackground)
                .navigationBarsPadding()
        ) {
            // Spacious Header Bar (zero overlapping circles)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = "Sign in to YouTube Music",
                        color = OnSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isAuthenticatedDomain) {
                            "Account authenticated"
                        } else {
                            "Sign in with Google to sync library"
                        },
                        color = if (isAuthenticatedDomain) UnboundSecondary else OnSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderCircleButton(
                        icon = Icons.Default.Key,
                        contentDescription = "Paste Cookie",
                        onClick = { showManualCookieDialog = true }
                    )
                    HeaderCircleButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        onClick = { webViewInstance?.reload() }
                    )
                    HeaderCircleButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onDismiss
                    )
                }
            }

            // Dedicated Sync Banner once redirected to YouTube Music
            if (isAuthenticatedDomain) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = UnboundSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Authenticated! Finalizing session sync...",
                            color = UnboundSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { doExtractCookies() },
                            colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = OnPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync",
                                color = OnPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Top Linear Progress Bar
            if (isLoading && progress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = UnboundPrimary,
                    trackColor = Color.Transparent,
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // WebView Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White) // Keep white page background for Google login readability
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
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

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progress = newProgress / 100f
                                }
                            }

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

                                    // Intercept cookies the instant navigation reaches music.youtube.com
                                    if (url != null && (url.startsWith("https://music.youtube.com") || url.contains("music.youtube.com"))) {
                                        doExtractCookies()
                                        view?.postDelayed({ doExtractCookies() }, 600L)
                                    }
                                }
                            }

                            loadUrl(LOG_IN_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Direct Manual Cookie Paste Dialog matching SimpMusic DevLogInBottomSheet pattern.
 */
@Composable
private fun ManualCookiePasteDialog(
    onDismiss: () -> Unit,
    onCookieSubmitted: (String) -> Unit
) {
    var rawCookieText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manual Cookie Authentication",
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                fontSize = 17.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "If Google sign-in is blocked by Google on your device, paste your YouTube Music cookie header directly below.",
                    color = OnSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = rawCookieText,
                    onValueChange = { rawCookieText = it },
                    placeholder = { Text("Paste SAPISID=...; SID=... here", fontSize = 12.sp, color = OnSurfaceVariant) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UnboundPrimary,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                rawCookieText = clip
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            modifier = Modifier.size(16.dp),
                            tint = UnboundPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste from Clipboard", color = UnboundPrimary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawCookieText.isNotBlank()) {
                        onCookieSubmitted(rawCookieText.trim())
                    }
                },
                enabled = rawCookieText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Authenticate", color = OnPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceVariant)
            }
        },
        containerColor = SurfaceGlassHighest,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun HeaderCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(UnboundSurfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = OnSurface,
            modifier = Modifier.size(18.dp)
        )
    }
}
