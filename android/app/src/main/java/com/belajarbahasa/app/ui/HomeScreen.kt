package com.belajarbahasa.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.belajarbahasa.app.BackendPrefs
import com.belajarbahasa.app.network.ApiClient
import com.belajarbahasa.app.network.CreateFolderRequest
import com.belajarbahasa.app.network.FolderListItem
import com.belajarbahasa.app.network.Materi
import com.belajarbahasa.app.util.formatRelativeTime
import com.belajarbahasa.app.util.queryDisplayName
import com.belajarbahasa.app.util.readUriBytes
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException

/**
 * Task 3 - Halaman Home / File Manager.
 *
 * Navigasi: drill-down per folder (bukan flat seperti gambar statis di
 * mockup-learn.html bagian 1), karena materi.folder_id di DB bersifat
 * NOT NULL - materi selalu ikut satu folder. folderStack menyimpan jejak
 * folder yang sedang dibuka (breadcrumb), kosong berarti sedang di root.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Konfigurasi backend ---
    var backendIp by remember { mutableStateOf(BackendPrefs.getIp(context)) }
    var backendPort by remember { mutableStateOf(BackendPrefs.getPort(context)) }
    var showIpDialog by remember { mutableStateOf(backendIp.isBlank()) }
    val baseUrl = remember(backendIp, backendPort) { BackendPrefs.baseUrl(context) }

    // --- State navigasi folder ---
    val folderStack = remember { mutableStateListOf<FolderListItem>() }
    val currentFolder = folderStack.lastOrNull()

    // --- State data ---
    var subfolders by remember { mutableStateOf<List<FolderListItem>>(emptyList()) }
    var materiList by remember { mutableStateOf<List<Materi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var reloadTrigger by remember { mutableStateOf(0) }

    // --- State dialog ---
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddMateriDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun reload() {
        val url = baseUrl ?: return
        val folderId = currentFolder?.id
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val api = ApiClient.get(url)
                subfolders = api.listFolders(folderId)
                materiList = if (folderId != null) api.listMateriByFolder(folderId) else emptyList()
            } catch (e: HttpException) {
                errorMsg = "Gagal memuat data (HTTP ${e.code()})"
            } catch (e: IOException) {
                errorMsg = "Gagal terhubung ke backend. Cek IP di ⚙️ & pastikan backend jalan."
            } catch (e: Exception) {
                errorMsg = "Terjadi kesalahan: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(baseUrl, currentFolder?.id, reloadTrigger) {
        if (baseUrl != null) reload()
    }

    BackHandler(enabled = folderStack.isNotEmpty()) {
        folderStack.removeAt(folderStack.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (currentFolder == null) "📚 Belajar Bahasa" else currentFolder.nama_folder)
                        if (currentFolder != null) {
                            Text(
                                text = "${currentFolder.jumlah_materi} materi • ${currentFolder.jumlah_subfolder} subfolder",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (folderStack.isNotEmpty()) {
                        IconButton(onClick = { folderStack.removeAt(folderStack.lastIndex) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showIpDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan Backend")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddFolderDialog = true },
                        enabled = baseUrl != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Folder") }

                    Button(
                        onClick = { showAddMateriDialog = true },
                        enabled = baseUrl != null && currentFolder != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Materi") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (baseUrl == null) {
                EmptyState("Backend belum diatur. Tap ikon ⚙️ di kanan atas untuk mengisi IP backend.")
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cari folder / materi...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )

                if (currentFolder != null && folderStack.size > 1) {
                    Text(
                        text = folderStack.joinToString(" / ") { it.nama_folder },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                errorMsg?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { reloadTrigger++ }) { Text("Coba lagi") }
                        }
                    }
                }

                val filteredFolders = subfolders.filter {
                    searchQuery.isBlank() || it.nama_folder.contains(searchQuery, ignoreCase = true)
                }
                val filteredMateri = materiList.filter {
                    searchQuery.isBlank() || it.judul.contains(searchQuery, ignoreCase = true)
                }

                if (!isLoading && errorMsg == null && filteredFolders.isEmpty() && filteredMateri.isEmpty()) {
                    EmptyState(
                        if (currentFolder == null)
                            "Belum ada folder. Tap \"+ Folder\" di bawah untuk membuat folder pertama."
                        else
                            "Folder ini masih kosong. Tambah subfolder atau materi."
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredFolders, key = { it.id }) { folder ->
                        FolderRow(folder = folder, onClick = { folderStack.add(folder) })
                    }
                    items(filteredMateri, key = { it.id }) { materi ->
                        MateriRow(materi = materi)
                    }
                }
            }
        }
    }

    if (showIpDialog) {
        BackendIpDialog(
            initialIp = backendIp,
            initialPort = backendPort,
            onDismiss = { showIpDialog = false },
            onSave = { ip, port ->
                backendIp = ip
                backendPort = port
                BackendPrefs.save(context, ip, port)
                showIpDialog = false
                reloadTrigger++
            }
        )
    }

    if (showAddFolderDialog) {
        AddFolderDialog(
            isSubmitting = isSubmitting,
            onDismiss = { showAddFolderDialog = false },
            onSubmit = { nama, bahasaSumber, bahasaTarget ->
                val url = baseUrl
                if (url == null) return@AddFolderDialog
                scope.launch {
                    isSubmitting = true
                    try {
                        val api = ApiClient.get(url)
                        api.createFolder(
                            CreateFolderRequest(
                                nama_folder = nama,
                                bahasa_sumber = bahasaSumber.ifBlank { null },
                                bahasa_target = bahasaTarget.ifBlank { null },
                                parent_id = currentFolder?.id
                            )
                        )
                        showAddFolderDialog = false
                        reloadTrigger++
                        Toast.makeText(context, "Folder ditambahkan", Toast.LENGTH_SHORT).show()
                    } catch (e: HttpException) {
                        Toast.makeText(context, "Gagal (HTTP ${e.code()}): ${e.message()}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membuat folder: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isSubmitting = false
                    }
                }
            }
        )
    }

    if (showAddMateriDialog && currentFolder != null) {
        val folderId = currentFolder.id
        AddMateriDialog(
            isSubmitting = isSubmitting,
            onDismiss = { showAddMateriDialog = false },
            onSubmit = { uri, judul ->
                val url = baseUrl
                if (url == null) return@AddMateriDialog
                scope.launch {
                    isSubmitting = true
                    try {
                        val filename = queryDisplayName(context, uri) ?: "materi.pdf"
                        val bytes = readUriBytes(context, uri)
                        val fileBody = bytes.toRequestBody("application/pdf".toMediaType())
                        val filePart = MultipartBody.Part.createFormData("file", filename, fileBody)
                        val judulBody = judul.toRequestBody("text/plain".toMediaType())
                        val folderIdBody = folderId.toRequestBody("text/plain".toMediaType())

                        val api = ApiClient.get(url)
                        api.createMateri(judulBody, folderIdBody, filePart)
                        showAddMateriDialog = false
                        reloadTrigger++
                        Toast.makeText(context, "Materi berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    } catch (e: HttpException) {
                        val body = e.response()?.errorBody()?.string()
                        Toast.makeText(context, "Gagal upload (HTTP ${e.code()}): ${body ?: e.message()}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal upload: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isSubmitting = false
                    }
                }
            }
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FolderRow(folder: FolderListItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(folder.nama_folder, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text("${folder.jumlah_materi} materi • ${folder.jumlah_subfolder} subfolder • ${formatRelativeTime(folder.updated_at)}")
            },
            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) }
        )
        HorizontalDivider()
    }
}

@Composable
private fun MateriRow(materi: Materi) {
    Column {
        ListItem(
            headlineContent = { Text(materi.judul, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text("${materi.bahasa_sumber} → ${materi.bahasa_target} • ${materi.total_halaman} halaman")
            },
            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
        )
        HorizontalDivider()
    }
}

@Composable
private fun BackendIpDialog(
    initialIp: String,
    initialPort: String,
    onDismiss: () -> Unit,
    onSave: (ip: String, port: String) -> Unit
) {
    var ip by remember { mutableStateOf(initialIp) }
    var port by remember { mutableStateOf(initialPort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Backend") },
        text = {
            Column {
                Text(
                    "Masukkan IP lokal PC Arch Linux yang menjalankan backend Rust. Pastikan HP & PC di WiFi yang sama.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Backend (contoh: 192.168.1.10)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (ip.isNotBlank() && port.isNotBlank()) onSave(ip.trim(), port.trim())
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun AddFolderDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (nama: String, bahasaSumber: String, bahasaTarget: String) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var bahasaSumber by remember { mutableStateOf("") }
    var bahasaTarget by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Tambah Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bahasaSumber,
                    onValueChange = { bahasaSumber = it },
                    label = { Text("Bahasa Sumber (opsional, mis. ja)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bahasaTarget,
                    onValueChange = { bahasaTarget = it },
                    label = { Text("Bahasa Target (opsional, mis. id)") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = nama.isNotBlank() && !isSubmitting,
                onClick = { onSubmit(nama.trim(), bahasaSumber.trim(), bahasaTarget.trim()) }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Simpan")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Batal") } }
    )
}

@Composable
private fun AddMateriDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (uri: Uri, judul: String) -> Unit
) {
    val context = LocalContext.current
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var judul by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            val name = queryDisplayName(context, uri)
            pickedName = name
            if (judul.isBlank() && name != null) {
                judul = name.removeSuffix(".pdf").removeSuffix(".PDF")
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Tambah Materi") },
        text = {
            Column {
                OutlinedButton(
                    onClick = { launcher.launch(arrayOf("application/pdf")) },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(pickedName ?: "Pilih File PDF") }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = judul,
                    onValueChange = { judul = it },
                    label = { Text("Judul Materi") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSubmitting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Mengupload & menghitung halaman PDF...", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = pickedUri != null && judul.isNotBlank() && !isSubmitting,
                onClick = { pickedUri?.let { onSubmit(it, judul.trim()) } }
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Upload")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Batal") } }
    )
}
