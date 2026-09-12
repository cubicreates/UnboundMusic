/*
 * Package: com.cubicreates.unboundmusic.data
 * File: SessionStore.kt
 * Purpose: Persistent disk storage for YouTube Music session credentials, surviving RAM clears and reboots.
 * Subsystem: Authentication & Persistence
 */

package com.cubicreates.unboundmusic.data

import android.content.Context
import android.content.SharedPreferences

class SessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("unbound_ytm_session", Context.MODE_PRIVATE)

    var cookie: String?
        get() = prefs.getString(KEY_COOKIE, null)
        set(value) = prefs.edit().putString(KEY_COOKIE, value).apply()

    var accountName: String?
        get() = prefs.getString(KEY_ACCOUNT_NAME, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_NAME, value).apply()

    var avatarUrl: String?
        get() = prefs.getString(KEY_AVATAR_URL, null)
        set(value) = prefs.edit().putString(KEY_AVATAR_URL, value).apply()

    val hasActiveSession: Boolean
        get() = !cookie.isNullOrBlank()

    fun saveSession(rawCookie: String, name: String? = null, avatar: String? = null) {
        prefs.edit().apply {
            putString(KEY_COOKIE, rawCookie)
            name?.let { putString(KEY_ACCOUNT_NAME, it) }
            avatar?.let { putString(KEY_AVATAR_URL, it) }
            apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COOKIE = "ytm_cookie"
        private const val KEY_ACCOUNT_NAME = "ytm_account_name"
        private const val KEY_AVATAR_URL = "ytm_avatar_url"

        @Volatile
        private var instance: SessionStore? = null

        fun getInstance(context: Context): SessionStore {
            return instance ?: synchronized(this) {
                instance ?: SessionStore(context).also { instance = it }
            }
        }
    }
}
