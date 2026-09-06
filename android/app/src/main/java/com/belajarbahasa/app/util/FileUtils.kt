package com.belajarbahasa.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Ambil nama file asli dari Uri hasil ActivityResultContracts.OpenDocument(). */
fun queryDisplayName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

/**
 * Baca seluruh isi file dari Uri ke ByteArray di background thread.
 * Untuk MVP ini cukup (batas upload backend 50MB), tidak perlu streaming.
 */
suspend fun readUriBytes(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("Tidak bisa membaca file dari lokasi ini")
}
