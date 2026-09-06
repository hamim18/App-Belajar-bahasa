package com.belajarbahasa.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.belajarbahasa.app.BackendPrefs
import com.belajarbahasa.app.network.ApiClient
import com.belajarbahasa.app.network.CreateBabRequest
import com.belajarbahasa.app.network.DaftarIsiNode
import com.belajarbahasa.app.network.ImportBabItem
import com.belajarbahasa.app.network.ImportDaftarIsiRequest
import com.belajarbahasa.app.network.UpdateBabRequest
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Task 4 - Daftar Isi.
 *
 * Catatan desain penting (supaya tidak lupa di task berikutnya):
 * - Tidak ada tracking "selesai/belum" per bab di skema DB saat ini
 *   (progress_baca hanya per materi, bukan per bab), jadi chip "✓ Selesai"
 *   di mockup-learn.html belum diimplementasikan di sini. Kalau nanti
 *   dibutuhkan, perlu kolom/tabel baru dulu di backend.
 * - level & urutan bab dibuat OTOMATIS oleh Android (bukan input manual):
 *   tambah dari toolbar -> level 1, urutan = jumlah bab level-1 + 1.
 *   tambah "Sub-bab" dari menu titik tiga sebuah bab -> level = level
 *   induk + 1, parent_id = bab itu, urutan = jumlah anak induk itu + 1.
 *   Ini menyederhanakan form sesuai filosofi "sederhana namun fleksibel"
 *   di Konsep-program-learn.md. Drag & drop reorder belum ada (V-next).
 * - Import JSON MENGGANTI TOTAL seluruh daftar isi materi ini (backend
 *   melakukan delete-lalu-insert dalam satu transaksi) - sudah diberi
 *   peringatan di dialog import.
 */

private enum class ViewMode(val label: String) {
    BILINGUAL("🌏 Bilingual"),
    SUMBER("Sumber"),
    TARGET("Target"),
}

private data class FlatBab(val node: DaftarIsiNode, val depth: Int)

private fun flatten(nodes: List<DaftarIsiNode>, depth: Int = 0): List<FlatBab> =
    nodes.flatMap { n -> listOf(FlatBab(n, depth)) + flatten(n.sub_bab, depth + 1) }

private fun countAll(nodes: List<DaftarIsiNode>): Int =
    nodes.sumOf { 1 + countAll(it.sub_bab) }

/** Cari node berdasarkan id di seluruh tree (dipakai untuk tahu level induk saat tambah sub-bab). */
private fun findNode(nodes: List<DaftarIsiNode>, id: String): DaftarIsiNode? {
    for (n in nodes) {
        if (n.id == id) return n
        findNode(n.sub_bab, id)?.let { return it }
    }
    return null
}

@Composable
fun DaftarIsiScreen(
    materiId: String,
    judulMateri: String,
    bahasaSumber: String,
    bahasaTarget: String,
    totalHalaman: Int,
    onBack: () -> Unit,
    onOpenPdf: (startHalaman: Int?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = remember { BackendPrefs.baseUrl(context) }

    var tree by remember { mutableStateOf<List<DaftarIsiNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var viewMode by remember { mutableStateOf(ViewMode.BILINGUAL) }

    // Dialog & aksi state
    var showAddDialog by remember { mutableStateOf(false) }
    var addParentId by remember { mutableStateOf<String?>(null) }
    var editingNode by remember { mutableStateOf<DaftarIsiNode?>(null) }
    var deletingNode by remember { mutableStateOf<DaftarIsiNode?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun reload() {
        val url = baseUrl ?: return
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                tree = ApiClient.get(url).getDaftarIsi(materiId)
            } catch (e: HttpException) {
                errorMsg = "Gagal memuat daftar isi (HTTP ${e.code()})"
            } catch (e: IOException) {
                errorMsg = "Gagal terhubung ke backend. Cek IP di pengaturan Home."
            } catch (e: Exception) {
                errorMsg = "Terjadi kesalahan: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(baseUrl, reloadTrigger) {
        if (baseUrl != null) reload()
    }

    val flatList = remember(tree) { flatten(tree) }
    val totalBab = remember(tree) { countAll(tree) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(judulMateri, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "$bahasaSumber → $bahasaTarget • $totalBab bab",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        floatingActionButton = {
            if (totalHalaman > 0) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenPdf(null) },
                    icon = { Text("📖") },
                    text = { Text("Baca") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showImportDialog = true }) { Text("Import JSON") }
                OutlinedButton(
                    onClick = {
                        addParentId = null
                        editingNode = null
                        showAddDialog = true
                    }
                ) { Text("Tambah Bab") }
                OutlinedButton(
                    onClick = {
                        val url = baseUrl ?: return@OutlinedButton
                        scope.launch {
                            try {
                                val res = ApiClient.get(url).exportDaftarIsi(materiId)
                                exportText = prettyImportJson(res.bahasa_sumber, res.bahasa_target, res.daftar_isi)
                                showExportDialog = true
                            } catch (e: Exception) {
                                errorMsg = "Gagal export: ${e.message}"
                            }
                        }
                    }
                ) { Text("Export") }
            }

            // Toggle bilingual / sumber / target
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ViewMode.values().forEach { mode ->
                    val selected = viewMode == mode
                    TextButton(onClick = { viewMode = mode }) {
                        Text(
                            mode.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            errorMsg?.let { msg ->
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { reloadTrigger++ }) { Text("Coba lagi") }
                }
            }

            if (!isLoading && errorMsg == null && flatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada daftar isi. Tap \"Tambah Bab\" atau \"Import JSON\" untuk mulai.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(flatList, key = { it.node.id }) { flat ->
                    BabRow(
                        flat = flat,
                        viewMode = viewMode,
                        onOpen = { onOpenPdf(flat.node.halaman_awal) },
                        onTambahSubBab = {
                            addParentId = flat.node.id
                            editingNode = null
                            showAddDialog = true
                        },
                        onEdit = {
                            addParentId = flat.node.parent_id
                            editingNode = flat.node
                            showAddDialog = true
                        },
                        onDelete = { deletingNode = flat.node },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        val parentNode = addParentId?.let { findNode(tree, it) }
        val siblingCount = if (addParentId == null) {
            tree.size
        } else {
            parentNode?.sub_bab?.size ?: 0
        }
        AddEditBabDialog(
            initial = editingNode,
            isSubBab = addParentId != null,
            isSubmitting = isSubmitting,
            onDismiss = { showAddDialog = false },
            onSubmit = { judulSumber, judulTarget, halamanAwal, halamanAkhir ->
                val url = baseUrl ?: return@AddEditBabDialog
                scope.launch {
                    isSubmitting = true
                    try {
                        val api = ApiClient.get(url)
                        val existing = editingNode
                        if (existing != null) {
                            api.updateBab(
                                existing.id,
                                UpdateBabRequest(
                                    judul_sumber = judulSumber,
                                    judul_target = judulTarget,
                                    halaman_awal = halamanAwal,
                                    halaman_akhir = halamanAkhir,
                                ),
                            )
                        } else {
                            val level = (parentNode?.level ?: 0) + 1
                            api.createBab(
                                materiId,
                                CreateBabRequest(
                                    judul_sumber = judulSumber,
                                    judul_target = judulTarget,
                                    halaman_awal = halamanAwal,
                                    halaman_akhir = halamanAkhir,
                                    level = level,
                                    parent_id = addParentId,
                                    urutan = siblingCount + 1,
                                ),
                            )
                        }
                        showAddDialog = false
                        reloadTrigger++
                    } catch (e: HttpException) {
                        errorMsg = "Gagal menyimpan bab (HTTP ${e.code()}): ${e.message()}"
                    } catch (e: Exception) {
                        errorMsg = "Gagal menyimpan bab: ${e.message}"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
        )
    }

    deletingNode?.let { node ->
        AlertDialog(
            onDismissRequest = { deletingNode = null },
            title = { Text("Hapus Bab") },
            text = {
                Text(
                    "Hapus \"${node.judul_target ?: node.judul_sumber}\"? " +
                        if (node.sub_bab.isNotEmpty())
                            "Semua sub-bab di dalamnya (${countAll(node.sub_bab)} item) juga ikut terhapus."
                        else "Tindakan ini tidak bisa dibatalkan."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = baseUrl
                    val id = node.id
                    deletingNode = null
                    if (url != null) {
                        scope.launch {
                            try {
                                ApiClient.get(url).deleteBab(id)
                                reloadTrigger++
                            } catch (e: Exception) {
                                errorMsg = "Gagal menghapus: ${e.message}"
                            }
                        }
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingNode = null }) { Text("Batal") }
            },
        )
    }

    if (showImportDialog) {
        ImportJsonDialog(
            bahasaSumber = bahasaSumber,
            bahasaTarget = bahasaTarget,
            judulMateri = judulMateri,
            isSubmitting = isSubmitting,
            onDismiss = { showImportDialog = false },
            onImport = { request ->
                val url = baseUrl ?: return@ImportJsonDialog
                scope.launch {
                    isSubmitting = true
                    try {
                        ApiClient.get(url).importDaftarIsi(materiId, request)
                        showImportDialog = false
                        reloadTrigger++
                    } catch (e: HttpException) {
                        errorMsg = "Gagal import (HTTP ${e.code()}): ${e.message()}"
                    } catch (e: Exception) {
                        errorMsg = "Gagal import: ${e.message}"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
        )
    }

    if (showExportDialog && exportText != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Daftar Isi") },
            text = {
                Text(
                    exportText.orEmpty(),
                    modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exportText.orEmpty()))
                }) { Text("Salin") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Tutup") }
            },
        )
    }
}

@Composable
private fun BabRow(
    flat: FlatBab,
    viewMode: ViewMode,
    onOpen: () -> Unit,
    onTambahSubBab: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val node = flat.node
    val icon = if (flat.depth == 0) "📖" else "▸"

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuExpanded = true }
                .padding(start = (12 + flat.depth * 24).dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, modifier = Modifier.width(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (viewMode != ViewMode.TARGET) {
                    Text(
                        node.judul_sumber ?: "(tanpa judul)",
                        fontWeight = if (flat.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                if (viewMode != ViewMode.SUMBER) {
                    Text(
                        node.judul_target ?: "(tanpa terjemahan)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "p.${node.halaman_awal}-${node.halaman_akhir}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onOpen) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Buka PDF dari bab ini",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu bab")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Tambah Sub-bab") },
                        onClick = { menuExpanded = false; onTambahSubBab() },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun AddEditBabDialog(
    initial: DaftarIsiNode?,
    isSubBab: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (judulSumber: String, judulTarget: String, halamanAwal: Int, halamanAkhir: Int) -> Unit,
) {
    var judulSumber by remember { mutableStateOf(initial?.judul_sumber.orEmpty()) }
    var judulTarget by remember { mutableStateOf(initial?.judul_target.orEmpty()) }
    var halamanAwal by remember { mutableStateOf(initial?.halaman_awal?.toString().orEmpty()) }
    var halamanAkhir by remember { mutableStateOf(initial?.halaman_akhir?.toString().orEmpty()) }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Edit Bab"
                    isSubBab -> "Tambah Sub-bab"
                    else -> "Tambah Bab"
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = judulSumber,
                    onValueChange = { judulSumber = it },
                    label = { Text("Judul (bahasa sumber)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = judulTarget,
                    onValueChange = { judulTarget = it },
                    label = { Text("Judul (terjemahan)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = halamanAwal,
                        onValueChange = { halamanAwal = it.filter { c -> c.isDigit() } },
                        label = { Text("Hal. awal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = halamanAkhir,
                        onValueChange = { halamanAkhir = it.filter { c -> c.isDigit() } },
                        label = { Text("Hal. akhir") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
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
                    val awal = halamanAwal.toIntOrNull()
                    val akhir = halamanAkhir.toIntOrNull()
                    when {
                        judulSumber.isBlank() -> formError = "Judul (bahasa sumber) wajib diisi"
                        awal == null || akhir == null -> formError = "Halaman awal & akhir wajib diisi angka"
                        awal > akhir -> formError = "Halaman awal tidak boleh lebih besar dari akhir"
                        else -> onSubmit(judulSumber.trim(), judulTarget.trim(), awal, akhir)
                    }
                },
            ) { Text(if (isSubmitting) "Menyimpan..." else "Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun ImportJsonDialog(
    bahasaSumber: String,
    bahasaTarget: String,
    judulMateri: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onImport: (ImportDaftarIsiRequest) -> Unit,
) {
    var pastedText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<ImportDaftarIsiRequest?>(null) }
    var showPrompt by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val jsonParser = remember { Json { ignoreUnknownKeys = true } }

    fun doParse() {
        parseError = null
        parsed = null
        try {
            val result = jsonParser.decodeFromString<ImportDaftarIsiRequest>(pastedText)
            if (result.bahasa_sumber != bahasaSumber || result.bahasa_target != bahasaTarget) {
                parseError = "bahasa_sumber/bahasa_target JSON (${result.bahasa_sumber}/${result.bahasa_target}) " +
                    "harus sama dengan bahasa materi ini ($bahasaSumber/$bahasaTarget)"
                return
            }
            if (result.daftar_isi.isEmpty()) {
                parseError = "daftar_isi kosong, tidak ada yang diimport"
                return
            }
            parsed = result
        } catch (e: Exception) {
            parseError = "JSON tidak valid: ${e.message}"
        }
    }

    if (showPrompt) {
        val promptText = buildAiPrompt(bahasaSumber, bahasaTarget, judulMateri)
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text("Prompt untuk AI (ChatGPT/Claude)") },
            text = {
                Text(
                    promptText,
                    modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(promptText)) }) { Text("Salin Prompt") }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) { Text("Tutup") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Daftar Isi (JSON)") },
        text = {
            Column {
                Text(
                    "⚠️ Import akan MENGGANTI seluruh daftar isi materi ini yang sudah ada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showPrompt = true }) {
                    Text("Lihat contoh prompt AI untuk generate JSON")
                }
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it; parsed = null; parseError = null },
                    label = { Text("Tempel JSON di sini") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                parseError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                parsed?.let { p ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "✅ JSON valid: ${p.daftar_isi.size} bab utama ditemukan (termasuk sub-bab: " +
                            "${p.daftar_isi.sumOf { countImportItem(it) }} total).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            val currentParsed = parsed
            if (currentParsed != null) {
                Button(enabled = !isSubmitting, onClick = { onImport(currentParsed) }) {
                    Text(if (isSubmitting) "Mengimpor..." else "Konfirmasi Import")
                }
            } else {
                Button(onClick = { doParse() }) { Text("Preview") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

private fun countImportItem(item: ImportBabItem): Int = 1 + item.sub_bab.sumOf { countImportItem(it) }

/** Bentuk teks JSON rapi untuk ditampilkan di dialog export (indent 2 spasi, manual - tanpa dependency baru). */
private fun prettyImportJson(bahasaSumber: String, bahasaTarget: String, items: List<ImportBabItem>): String {
    val sb = StringBuilder()
    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
    fun writeItem(item: ImportBabItem, indent: String) {
        sb.append("$indent{\n")
        sb.append("$indent  \"judul_sumber\": \"${esc(item.judul_sumber)}\",\n")
        sb.append("$indent  \"judul_target\": \"${esc(item.judul_target)}\",\n")
        sb.append("$indent  \"halaman_awal\": ${item.halaman_awal},\n")
        sb.append("$indent  \"halaman_akhir\": ${item.halaman_akhir},\n")
        sb.append("$indent  \"level\": ${item.level},\n")
        sb.append("$indent  \"urutan\": ${item.urutan}")
        if (item.sub_bab.isNotEmpty()) {
            sb.append(",\n$indent  \"sub_bab\": [\n")
            item.sub_bab.forEachIndexed { i, sub ->
                writeItem(sub, "$indent    ")
                sb.append(if (i < item.sub_bab.lastIndex) ",\n" else "\n")
            }
            sb.append("$indent  ]\n")
        } else {
            sb.append("\n")
        }
        sb.append("$indent}")
    }
    sb.append("{\n")
    sb.append("  \"bahasa_sumber\": \"${esc(bahasaSumber)}\",\n")
    sb.append("  \"bahasa_target\": \"${esc(bahasaTarget)}\",\n")
    sb.append("  \"daftar_isi\": [\n")
    items.forEachIndexed { i, item ->
        writeItem(item, "    ")
        sb.append(if (i < items.lastIndex) ",\n" else "\n")
    }
    sb.append("  ]\n}")
    return sb.toString()
}

private fun buildAiPrompt(bahasaSumber: String, bahasaTarget: String, judulMateri: String): String = """
Buatkan daftar isi dari buku "$judulMateri" berikut dalam format JSON.

Informasi:
- Bahasa sumber: $bahasaSumber
- Bahasa target: $bahasaTarget

RULES:
1. Ekstrak semua bab dan sub-bab dari daftar isi
2. Untuk setiap entry, berikan:
   - judul_sumber: judul asli dalam bahasa sumber
   - judul_target: terjemahan dalam bahasa target
   - halaman_awal: halaman mulai
   - halaman_akhir: halaman akhir (bisa perkiraan)
   - level: 1 untuk bab, 2 untuk sub-bab
   - urutan: nomor urut

Format JSON (bahasa_sumber & bahasa_target HARUS persis "$bahasaSumber" dan "$bahasaTarget"):
{
  "bahasa_sumber": "$bahasaSumber",
  "bahasa_target": "$bahasaTarget",
  "daftar_isi": [
    {
      "judul_sumber": "...",
      "judul_target": "...",
      "halaman_awal": 0,
      "halaman_akhir": 0,
      "level": 1,
      "urutan": 1,
      "sub_bab": [...]
    }
  ]
}

TEXT DAFTAR ISI:
[Tempelkan teks daftar isi di sini]
""".trimIndent()
