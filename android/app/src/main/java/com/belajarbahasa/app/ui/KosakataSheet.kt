package com.belajarbahasa.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belajarbahasa.app.BackendPrefs
import com.belajarbahasa.app.network.ApiClient
import com.belajarbahasa.app.network.CreateKosakataRequest
import com.belajarbahasa.app.network.KamusListItem
import com.belajarbahasa.app.network.KosakataItem
import com.belajarbahasa.app.network.UpdateKosakataRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Task 6 - Modal Kosakata per halaman (ModalBottomSheet, sesuai mockup-learn.html
 * bagian 4 & 5). Dibuka dari tombol "📝 Kosakata" di toolbar PdfViewerScreen.
 *
 * Keputusan desain: dipilih ModalBottomSheet (bukan Dialog fullscreen atau layar
 * terpisah) supaya konteks halaman PDF yang sedang dibaca tidak hilang, dan bisa
 * di-swipe-dismiss dengan cepat.
 *
 * babId BOLEH null (kosakata_konteks.bab_id nullable di DB) - terjadi kalau
 * halaman saat ini tidak masuk rentang bab manapun di Daftar Isi (misal daftar
 * isi belum lengkap). Kosakata tetap bisa ditambahkan, hanya tidak ter-assign
 * ke bab tertentu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KosakataSheet(
    materiId: String,
    bahasaSumber: String,
    bahasaTarget: String,
    halaman: Int,
    babId: String?,
    judulBab: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = remember { BackendPrefs.baseUrl(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var items by remember { mutableStateOf<List<KosakataItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<KosakataItem?>(null) }
    var deletingItem by remember { mutableStateOf<KosakataItem?>(null) }

    fun reload() {
        val url = baseUrl ?: return
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                items = ApiClient.get(url).listKosakataHalaman(materiId, halaman, babId)
            } catch (e: HttpException) {
                errorMsg = "Gagal memuat kosakata (HTTP ${e.code()})"
            } catch (e: Exception) {
                errorMsg = "Gagal terhubung ke backend: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(reloadTrigger) { reload() }

    // Cari di kata_asli (kanji), reading (furigana), MAUPUN terjemahan -
    // tiga-tiganya, sesuai permintaan (sebelumnya reading terlewat).
    val filtered = items.filter {
        searchQuery.isBlank() ||
            it.kata_asli.contains(searchQuery, ignoreCase = true) ||
            (it.reading?.contains(searchQuery, ignoreCase = true) == true) ||
            (it.terjemahan?.contains(searchQuery, ignoreCase = true) == true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "📝 Kosakata - Halaman $halaman",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (judulBab != null) {
                        Text(
                            judulBab,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah kosakata")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Cari kosakata...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                errorMsg != null -> Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error)

                filtered.isEmpty() -> Text(
                    "Belum ada kosakata di halaman ini.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                else -> LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        KosakataRow(
                            item = item,
                            onEdit = { editingItem = item },
                            onDelete = { deletingItem = item }
                        )
                        HorizontalDivider()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Total: ${items.size} kosakata di halaman ini",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showAddDialog) {
        AddKosakataDialog(
            bahasaSumber = bahasaSumber,
            bahasaTarget = bahasaTarget,
            materiId = materiId,
            babId = babId,
            halaman = halaman,
            onDismiss = { showAddDialog = false },
            onSaved = {
                showAddDialog = false
                reloadTrigger++
            }
        )
    }

    editingItem?.let { item ->
        EditKosakataDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSaved = {
                editingItem = null
                reloadTrigger++
            }
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Hapus Kosakata") },
            text = { Text("Hapus \"${item.kata_asli}\" dari halaman $halaman?") },
            confirmButton = {
                TextButton(onClick = {
                    val url = baseUrl ?: return@TextButton
                    scope.launch {
                        try {
                            ApiClient.get(url).deleteKosakata(item.id)
                            deletingItem = null
                            reloadTrigger++
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal menghapus: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingItem = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun KosakataRow(item: KosakataItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(item.kata_asli, fontWeight = FontWeight.SemiBold)
                item.reading?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "($it)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(item.terjemahan ?: "(belum ada terjemahan)", style = MaterialTheme.typography.bodySmall)
            if (item.halaman_terkait.size > 1) {
                Text(
                    "📄 Muncul di halaman: ${item.halaman_terkait.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.catatan_pribadi?.takeIf { it.isNotBlank() }?.let {
                Text("📌 $it", style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Dialog Tambah Kosakata (mockup bagian 5). Sugesti kamus diambil dari
 * GET /api/kamus?q=... setiap kali kata_asli diketik (debounce 300ms lewat
 * LaunchedEffect(kataAsli)).
 */
@Composable
private fun AddKosakataDialog(
    bahasaSumber: String,
    bahasaTarget: String,
    materiId: String,
    babId: String?,
    halaman: Int,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = remember { BackendPrefs.baseUrl(context) }

    var kataAsli by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<KamusListItem>>(emptyList()) }
    var selectedExisting by remember { mutableStateOf<KamusListItem?>(null) }
    var terjemahan by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf("") }
    var tipeKata by remember { mutableStateOf("") }
    var catatan by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kataAsli) {
        selectedExisting = null
        if (kataAsli.trim().isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        val url = baseUrl ?: return@LaunchedEffect
        try {
            val result = ApiClient.get(url).searchKamus(
                q = kataAsli.trim(),
                bahasaSumber = bahasaSumber,
                bahasaTarget = bahasaTarget,
                limit = 5
            )
            suggestions = result.items
        } catch (_: Exception) {
            // sugesti bersifat opsional - kegagalan tidak menghalangi user mengetik manual
        }
    }

    fun applySuggestion(item: KamusListItem) {
        selectedExisting = item
        kataAsli = item.kata_asli
        terjemahan = item.terjemahan.orEmpty()
        reading = item.reading.orEmpty()
        tipeKata = item.tipe_kata.orEmpty()
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("✚ Tambah Kosakata") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = kataAsli,
                    onValueChange = { kataAsli = it; formError = null },
                    label = { Text("Kata ($bahasaSumber)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Sugesti dari kamus:", style = MaterialTheme.typography.labelSmall)
                    suggestions.forEach { sugg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applySuggestion(sugg) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${sugg.kata_asli}${sugg.reading?.let { " ($it)" } ?: ""} - ${sugg.terjemahan ?: "?"}")
                            if (sugg.jumlah_muncul > 0) {
                                Text(
                                    "Sudah ada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = terjemahan,
                    onValueChange = { terjemahan = it; formError = null },
                    label = { Text("Terjemahan ($bahasaTarget)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reading,
                        onValueChange = { reading = it },
                        label = { Text("Reading") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tipeKata,
                        onValueChange = { tipeKata = it },
                        label = { Text("Tipe (N/V/Adj)") },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan Pribadi (opsional)") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                formError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val kata = kataAsli.trim()
                    if (kata.isBlank()) {
                        formError = "Kata wajib diisi"
                        return@Button
                    }
                    if (selectedExisting == null && terjemahan.isBlank()) {
                        formError = "Kata baru, terjemahan wajib diisi"
                        return@Button
                    }
                    val url = baseUrl ?: return@Button
                    isSubmitting = true
                    scope.launch {
                        try {
                            ApiClient.get(url).createKosakata(
                                CreateKosakataRequest(
                                    kata_asli = kata,
                                    materi_id = materiId,
                                    bab_id = babId,
                                    halaman = halaman,
                                    catatan_pribadi = catatan.ifBlank { null },
                                    terjemahan = terjemahan.ifBlank { null },
                                    reading = reading.ifBlank { null },
                                    tipe_kata = tipeKata.ifBlank { null }
                                )
                            )
                            onSaved()
                        } catch (e: HttpException) {
                            val body = e.response()?.errorBody()?.string()
                            formError = "Gagal (HTTP ${e.code()}): ${body ?: e.message()}"
                        } catch (e: Exception) {
                            formError = "Gagal menyimpan: ${e.message}"
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Tambahkan")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Batal") } }
    )
}

/**
 * Dialog Edit - hanya catatan pribadi yang bisa diubah dari sini.
 * Edit terjemahan/reading/tipe_kata dilakukan lewat halaman Kamus Detail
 * (PUT /api/kamus/{id}), karena itu mengubah kata secara global, bukan
 * hanya baris kosakata_konteks di halaman ini.
 */
@Composable
private fun EditKosakataDialog(
    item: KosakataItem,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = remember { BackendPrefs.baseUrl(context) }
    var catatan by remember { mutableStateOf(item.catatan_pribadi.orEmpty()) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Edit Catatan - ${item.kata_asli}") },
        text = {
            OutlinedTextField(
                value = catatan,
                onValueChange = { catatan = it },
                label = { Text("Catatan Pribadi") },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val url = baseUrl ?: return@Button
                    isSubmitting = true
                    scope.launch {
                        try {
                            ApiClient.get(url).updateKosakata(item.id, UpdateKosakataRequest(catatan_pribadi = catatan))
                            onSaved()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            ) { Text(if (isSubmitting) "Menyimpan..." else "Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Batal") } }
    )
}
