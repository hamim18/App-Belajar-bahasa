package com.belajarbahasa.app

import android.content.Context
import androidx.core.content.edit

/**
 * Simpan IP & port backend Rust di SharedPreferences supaya user tidak perlu
 * ketik ulang tiap buka app. IP lokal (mis. 192.168.1.10) diisi lewat dialog
 * pengaturan di HomeScreen.
 */
object BackendPrefs {
    private const val PREF_NAME = "backend_prefs"
    private const val KEY_IP = "backend_ip"
    private const val KEY_PORT = "backend_port"
    private const val DEFAULT_PORT = "8080"

    fun getIp(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IP, "") ?: ""

    fun getPort(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PORT, DEFAULT_PORT) ?: DEFAULT_PORT

    fun save(context: Context, ip: String, port: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_IP, ip)
            putString(KEY_PORT, port)
        }
    }

    /** Null kalau IP belum pernah diisi -> UI harus tampilkan prompt pengaturan. */
    fun baseUrl(context: Context): String? {
        val ip = getIp(context)
        if (ip.isBlank()) return null
        val port = getPort(context).ifBlank { DEFAULT_PORT }
        return "http://$ip:$port/"
    }
}
