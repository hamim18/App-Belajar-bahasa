package com.belajarbahasa.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belajarbahasa.app.BackendPrefs
import com.belajarbahasa.app.network.ApiClient
import com.belajarbahasa.app.network.KamusDetail
import retrofit2.HttpException

/**
 * Task 6 - Halaman Detail Kata (mockup-learn.html bagian 7).
 * bahasaTarget sengaja tidak diminta dari pemanggil - dikirim null ke
 * backend supaya otomatis pakai bahasa default user (sama seperti KamusScreen).
 */
@Composable
fun KamusDetailScreen(
    kataId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val baseUrl = remember { BackendPrefs.baseUrl(context) }

    var detail by remember { mutableStateOf<KamusDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kataId) {
        val url = baseUrl
        if (url == null) {
            errorMsg = "Backend belum diatur."
            isLoading = false
            return@LaunchedEffect
        }
        try {
            detail = ApiClient.get(url).getKamusDetail(kataId)
        } catch (e: HttpException) {
            errorMsg = "Gagal memuat detail kata (HTTP ${e.code()})"
        } catch (e: Exception) {
            errorMsg = "Gagal terhubung ke backend: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.kata_asli ?: "Detail Kata") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                errorMsg != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error) }

                detail != null -> {
                    val d = detail!!
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(d.kata_asli, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        d.reading?.takeIf { it.isNotBlank() }?.let {
                            Text("($it)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            d.terjemahan ?: "(belum ada terjemahan untuk ${d.bahasa_target})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        d.tipe_kata?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (d.is_custom) {
                            Text("Kata custom (ditambahkan manual)", style = MaterialTheme.typography.labelSmall)
                        }

                        d.contoh_kalimat?.takeIf { it.isNotBlank() }?.let { contoh ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("📖 Contoh Kalimat", fontWeight = FontWeight.SemiBold)
                            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(contoh, modifier = Modifier.padding(12.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("📍 Muncul di Materi", fontWeight = FontWeight.SemiBold)
                        if (d.kemunculan.isEmpty()) {
                            Text(
                                "Belum muncul di materi manapun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                                items(d.kemunculan) { k ->
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(k.judul_materi, fontWeight = FontWeight.Medium)
                                            Text(
                                                "${k.judul_bab ?: "(tanpa bab)"} • p.${k.halaman_list.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
