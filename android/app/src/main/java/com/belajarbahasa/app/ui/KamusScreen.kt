package com.belajarbahasa.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belajarbahasa.app.BackendPrefs
import com.belajarbahasa.app.network.ApiClient
import com.belajarbahasa.app.network.KamusListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Task 6 - Halaman Kamus (mockup-learn.html bagian 6).
 *
 * bahasaSumber/bahasaTarget SENGAJA tidak diminta sebagai parameter dari
 * pemanggil (MainActivity) - dikirim null ke backend, yang otomatis pakai
 * bahasa default user (lihat backend/src/handlers/kamus.rs -> default_bahasa).
 * Label bahasa di app bar diisi belakangan dari hasil pencarian pertama.
 *
 * Catatan: fitur ⭐ Favorit di mockup BELUM diimplementasikan - tidak ada
 * tabel/kolom "favorit" di skema DB (migrations/0001_init.sql). Kalau nanti
 * dibutuhkan, perlu tabel baru dulu (misal kamus_favorit per user).
 */
@Composable
fun KamusScreen(
    onBack: () -> Unit,
    onOpenDetail: (kataId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = remember { BackendPrefs.baseUrl(context) }

    var query by remember { mutableStateOf("") }
    var kamusItems by remember { mutableStateOf<List<KamusListItem>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var tipeFilter by remember { mutableStateOf<String?>(null) }

    fun reload() {
        val url = baseUrl
        if (url == null) {
            errorMsg = "Backend belum diatur."
            isLoading = false
            return
        }
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val result = ApiClient.get(url).searchKamus(
                    q = query.trim().ifBlank { null },
                    tipeKata = tipeFilter,
                    limit = 100
                )
                kamusItems = result.items
                total = result.total
            } catch (e: HttpException) {
                errorMsg = "Gagal memuat kamus (HTTP ${e.code()})"
            } catch (e: Exception) {
                errorMsg = "Gagal terhubung ke backend: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(query, tipeFilter) {
        delay(300)
        reload()
    }

    val subtitle = kamusItems.firstOrNull()?.let { "${it.bahasa_sumber} → ${it.bahasa_target}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("📖 Kamus")
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cari kata...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                listOf(null, "N", "V", "Adj", "Adv").forEach { tipe ->
                    TextButton(onClick = { tipeFilter = tipe }) {
                        Text(
                            tipe ?: "Semua",
                            color = if (tipeFilter == tipe) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            when {
                isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                errorMsg != null -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error) }

                kamusItems.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Belum ada kata di kamus.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(kamusItems, key = { it.id }) { item ->
                            KamusEntryRow(item = item, onClick = { onOpenDetail(item.id) })
                            HorizontalDivider()
                        }
                        item {
                            Text(
                                "Total: $total kata",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KamusEntryRow(item: KamusListItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                item.kata_asli + (item.reading?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Column {
                Text(item.terjemahan ?: "(belum ada terjemahan)")
                Text(
                    "${item.tipe_kata ?: "-"} • ${item.jumlah_materi} materi • ${item.jumlah_muncul} muncul",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
