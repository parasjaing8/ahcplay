package com.aihomecloud.ahcplayer.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private const val TAG = "SecurePrefs"

/**
 * Shared EncryptedSharedPreferences factory with a plaintext fallback for devices whose
 * Keystore is broken/unavailable. [isEncrypted] reports whether every prefs file created
 * so far is actually encrypted, so callers can warn the user if a fallback occurred.
 */
object SecurePrefs {
    var isEncrypted: Boolean = true
        private set

    fun create(context: Context, name: String): SharedPreferences = try {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            name, masterKey, context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable for '$name', falling back to plain", e)
        isEncrypted = false
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
