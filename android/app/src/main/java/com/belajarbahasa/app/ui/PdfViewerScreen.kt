package com.belajarbahasa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.belajarbahasa.app.BackendPrefs
import com.belajarbahasa.app.network.ApiClient
import com.belajarbahasa.app.network.Bookmark
import com.belajarbahasa.app.network.CreateBookmarkRequest
import com.belajarbahasa.app.network.DaftarIsiNode
import com.belajarbahasa.app.network.UpdateProgressRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Task 5 - PDF Viewer.
 *
 * Keputusan desain penting (dicatat supaya tidak lupa di task berikutnya):
 * - SENGAJA memakai android.graphics.pdf.PdfRenderer bawaan Android
 *   (tersedia sejak API 21, minSdk project ini 26), BUKAN library pihak
 *   ketiga seperti AndroidPdfViewer/PDF.js yang disebut di
 *   Konsep-program-learn.md. Alasannya: tidak perlu tambah dependency +
 *   repository JitPack baru (device dev CLI-only, supaya build tetap
 *   sesederhana mungkin). Konsekuensinya: navigasi halaman (swipe),
 *   zoom (pinch/double-tap/tombol +-), dan toggle toolbar diimplementasi
 *   manual di file ini dengan Compose (HorizontalPager + gesture),
 *   bukan otomatis dari library.
 * - File PDF didownload SEKALI dari backend (GET /api/materi/:id/file) lalu
 *   di-cache di cacheDir/pdf_cache/<materiId>.pdf. Kalau file cache sudah
 *   ada, tidak didownload ulang - ada tombol "Muat Ulang PDF" di app bar
 *   untuk memaksa download ulang (misal kalau file di server pernah diganti
 *   - meski fitur ganti file materi belum ada di Task 2/3).
 * - Progress baca (halaman_terakhir) disimpan ke backend dengan debounce
 *   800ms setelah pindah halaman - best effort, kegagalan simpan progress
 *   TIDAK dianggap fatal (tidak memblokir baca), sesuai filosofi
 *   "offline-first" di Konsep-program-learn.md.
 * - Tombol "Kosakata" di toolbar membuka ModalBottomSheet KosakataSheet
 *   (Task 6) untuk halaman yang sedang dibaca. bab_id untuk kosakata di-resolve
 *   otomatis dari daftarIsiTree (lihat findBabForHalaman) - boleh null kalau
 *   halaman ini tidak masuk rentang bab manapun.
 * - Rendering PDF dijalankan di single-thread dispatcher terpisah
 *   (bukan Dispatchers.IO biasa) karena PdfRenderer/PdfRenderer.Page
 *   tidak aman diakses dari lebih dari satu thread bersamaan.
 */

private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 3.5f
private const val DOUBLE_TAP_SCALE = 2.5f

@Composable
fun PdfViewerScreen(
    materiId: String,
    judulMateri: String,
    totalHalamanAwal: Int,
    bahasaSumber: String,
    bahasaTarget: String,
    /** null = lanjutkan otomatis dari progress terakhir (dipakai tombol "Baca"),
     *  angka eksplisit = langsung ke halaman itu (dipakai tombol ▶ per bab). */
    startHalaman: Int?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl = remember { BackendPrefs.baseUrl(context) }

    // Dispatcher khusus untuk semua operasi PdfRenderer - sengaja single
    // thread supaya tidak ada dua operasi render bersamaan dari coroutine
    // berbeda (PdfRenderer TIDAK thread-safe untuk akses paralel).
    val pdfDispatcher = remember { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }

    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    // Halaman awal yang SUDAH diresolve (1-based): dari progress terakhir kalau
    // startHalaman == null, atau langsung dari parameter kalau ada nilai eksplisit.
    // Baru terisi setelah proses loading selesai (lihat LaunchedEffect di bawah).
    var resolvedStartPage by remember { mutableStateOf<Int?>(null) }
    // Perintah "lompat ke halaman X" dari klik item bookmark - dikonsumsi oleh
    // PdfPagerContent lalu di-reset ke null lagi.
    var pendingJumpPage by remember { mutableStateOf<Int?>(null) }

    var toolbarVisible by remember { mutableStateOf(true) }
    var currentPageDisplay by remember { mutableStateOf(1) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarkListDialog by remember { mutableStateOf(false) }
    // Task 6 - dipakai untuk resolve bab_id per halaman saat membuka modal Kosakata.
    var daftarIsiTree by remember { mutableStateOf<List<DaftarIsiNode>>(emptyList()) }
    var showKosakataSheet by remember { mutableStateOf(false) }

    fun closeRendererResources() {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        try {
            pfd?.close()
        } catch (_: Exception) {
        }
        renderer = null
        pfd = null
    }

    // Buka (atau download dulu kalau belum ada cache) file PDF, lalu siapkan PdfRenderer.
    LaunchedEffect(reloadTrigger) {
        val url = baseUrl
        if (url == null) {
            errorMsg = "Backend belum diatur. Kembali ke Home dan atur IP dulu."
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMsg = null
        resolvedStartPage = null
        closeRendererResources()
        try {
            val file = ensurePdfDownloaded(context, url, materiId)
            withContext(pdfDispatcher) {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(descriptor)
                pfd = descriptor
                renderer = r
                pageCount = r.pageCount
            }
            // Muat daftar bookmark (gagal dimuat tidak dianggap fatal untuk halaman ini)
            try {
                bookmarks = ApiClient.get(url).listBookmarks(materiId)
            } catch (_: Exception) {
                // biarkan kosong, tetap bisa dipakai untuk menambah bookmark baru
            }
            // Muat daftar isi (Task 6) - dipakai untuk resolve bab_id per halaman
            // saat membuka modal Kosakata. Best-effort: kalau gagal, kosakata tetap
            // bisa ditambahkan tanpa bab_id (bab_id nullable di DB).
            try {
                daftarIsiTree = ApiClient.get(url).getDaftarIsi(materiId)
            } catch (_: Exception) {
                // biarkan kosong
            }
            // Resolve halaman awal: kalau startHalaman dikirim eksplisit (tap ▶ di
            // satu bab), pakai itu. Kalau null (tombol "Baca"), ambil halaman
            // terakhir dari backend supaya baca lanjut dari posisi terakhir -
            // kalau gagal diambil atau belum pernah baca sama sekali, mulai dari 1.
            resolvedStartPage = startHalaman ?: run {
                try {
                    val progress = ApiClient.get(url).getProgress(materiId)
                    if (progress.halaman_terakhir > 0) progress.halaman_terakhir else 1
                } catch (_: Exception) {
                    1
                }
            }
            currentPageDisplay = resolvedStartPage ?: 1
        } catch (e: Exception) {
            errorMsg = "Gagal membuka PDF: ${e.message ?: "tidak diketahui"}"
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            closeRendererResources()
            pdfDispatcher.close()
        }
    }

    // Task 6 - bab (kalau ada) yang mengandung halaman yang sedang dibaca.
    // null artinya halaman ini tidak masuk rentang bab manapun di Daftar Isi -
    // kosakata tetap bisa ditambahkan, hanya tanpa bab_id (nullable di DB).
    val currentBab = remember(daftarIsiTree, currentPageDisplay) {
        findBabForHalaman(daftarIsiTree, currentPageDisplay)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(judulMateri, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (pageCount > 0 && resolvedStartPage != null) {
                            Text(
                                "Halaman ${currentPageDisplay.coerceIn(1, pageCount)} dari $pageCount",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddBookmarkDialog = true }, enabled = pageCount > 0) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Tambah bookmark")
                    }
                    IconButton(onClick = { showBookmarkListDialog = true }) {
                        Icon(Icons.Default.List, contentDescription = "Daftar bookmark")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memuat PDF...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                errorMsg != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { reloadTrigger++ }) { Text("Coba lagi") }
                    }
                }

                renderer != null && pageCount > 0 && resolvedStartPage != null -> {
                    PdfPagerContent(
                        renderer = renderer!!,
                        pageCount = pageCount,
                        startPageIndex = (resolvedStartPage!! - 1).coerceIn(0, pageCount - 1),
                        pdfDispatcher = pdfDispatcher,
                        toolbarVisible = toolbarVisible,
                        onToggleToolbar = { toolbarVisible = !toolbarVisible },
                        onPageSettled = { pageIndex ->
                            currentPageDisplay = pageIndex + 1
                            val url = baseUrl ?: return@PdfPagerContent
                            scope.launch {
                                delay(800)
                                try {
                                    ApiClient.get(url).updateProgress(
                                        materiId,
                                        UpdateProgressRequest(halaman_terakhir = pageIndex + 1),
                                    )
                                } catch (_: Exception) {
                                    // best-effort, tidak memblokir pengalaman baca
                                }
                            }
                        },
                        bookmarkedPages = bookmarks.map { it.halaman - 1 }.toSet(),
                        onOpenKosakata = { showKosakataSheet = true },
                        pendingJumpPage = pendingJumpPage,
                        onJumpConsumed = { pendingJumpPage = null },
                    )
                }
            }
        }
    }

    if (showAddBookmarkDialog) {
        AddBookmarkDialog(
            onDismiss = { showAddBookmarkDialog = false },
            onSave = { halaman, catatan ->
                val url = baseUrl ?: return@AddBookmarkDialog
                scope.launch {
                    try {
                        val created = ApiClient.get(url).createBookmark(
                            materiId,
                            CreateBookmarkRequest(halaman = halaman, catatan = catatan.ifBlank { null }),
                        )
                        bookmarks = bookmarks + created
                        showAddBookmarkDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal menyimpan bookmark: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            defaultHalaman = currentPageDisplay,
        )
    }

    if (showBookmarkListDialog) {
        BookmarkListDialog(
            bookmarks = bookmarks,
            onDismiss = { showBookmarkListDialog = false },
            onJumpTo = { bookmark ->
                pendingJumpPage = (bookmark.halaman - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                showBookmarkListDialog = false
            },
            onDelete = { bookmark ->
                val url = baseUrl ?: return@BookmarkListDialog
                scope.launch {
                    try {
                        ApiClient.get(url).deleteBookmark(bookmark.id)
                        bookmarks = bookmarks.filter { it.id != bookmark.id }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal menghapus bookmark: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    if (showKosakataSheet) {
        KosakataSheet(
            materiId = materiId,
            bahasaSumber = bahasaSumber,
            bahasaTarget = bahasaTarget,
            halaman = currentPageDisplay,
            babId = currentBab?.id,
            judulBab = currentBab?.judul_sumber,
            onDismiss = { showKosakataSheet = false },
        )
    }
}

/**
 * Task 6 - cari node daftar isi (termasuk sub-bab) yang rentang halamannya
 * mengandung halaman tertentu. Kalau ada beberapa node yang cocok (bab &
 * sub-babnya sama-sama mengandung halaman ini), pilih yang rentangnya PALING
 * SEMPIT (biasanya sub-bab, karena levelnya lebih dalam dari bab induknya).
 */
private fun findBabForHalaman(nodes: List<DaftarIsiNode>, halaman: Int): DaftarIsiNode? {
    fun flattenAll(list: List<DaftarIsiNode>): List<DaftarIsiNode> =
        list.flatMap { listOf(it) + flattenAll(it.sub_bab) }

    return flattenAll(nodes)
        .filter { halaman in it.halaman_awal..it.halaman_akhir }
        .minByOrNull { it.halaman_akhir - it.halaman_awal }
}

/** Download file PDF ke cache lokal kalau belum ada, lalu kembalikan File-nya. */
private suspend fun ensurePdfDownloaded(
    context: Context,
    baseUrl: String,
    materiId: String,
): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
    val file = File(dir, "$materiId.pdf")
    if (file.exists() && file.length() > 0) {
        return@withContext file
    }
    val body = ApiClient.get(baseUrl).downloadMateriFile(materiId)
    file.outputStream().use { output ->
        body.byteStream().use { input -> input.copyTo(output) }
    }
    file
}

@Composable
private fun PdfPagerContent(
    renderer: PdfRenderer,
    pageCount: Int,
    startPageIndex: Int,
    pdfDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    toolbarVisible: Boolean,
    onToggleToolbar: () -> Unit,
    onPageSettled: (Int) -> Unit,
    bookmarkedPages: Set<Int>,
    onOpenKosakata: () -> Unit,
    pendingJumpPage: Int?,
    onJumpConsumed: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startPageIndex) { pageCount }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onPageSettled(pagerState.currentPage)
    }

    // Fix bug 3: eksekusi permintaan "lompat ke halaman X" dari klik item bookmark.
    LaunchedEffect(pendingJumpPage) {
        val target = pendingJumpPage
        if (target != null) {
            pagerState.scrollToPage(target.coerceIn(0, pageCount - 1))
            onJumpConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (toolbarVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onOpenKosakata) {
                    Text("📝 Kosakata")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (pagerState.currentPage in bookmarkedPages) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Halaman ini dibookmark",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider()
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                PdfPage(
                    renderer = renderer,
                    pageIndex = pageIndex,
                    pdfDispatcher = pdfDispatcher,
                    onSingleTap = onToggleToolbar,
                )
            }
        }

        if (toolbarVisible) {
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                    ) { Text("◀ Prev") }

                    Text(
                        "Halaman ${pagerState.currentPage + 1} / $pageCount",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )

                    TextButton(
                        onClick = {
                            scope.launch {
                                if (pagerState.currentPage < pageCount - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < pageCount - 1,
                    ) { Text("Next ▶") }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1).toFloat() / pageCount.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Satu halaman PDF: render sekali ke Bitmap resolusi tetap (2x lebar layar
 * supaya tetap tajam saat di-zoom), lalu zoom/pan dilakukan lewat
 * graphicsLayer (scale & offset) - BUKAN dengan render ulang bitmap - supaya
 * gesture zoom terasa responsif tanpa membebani PdfRenderer berulang kali.
 */
@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    pdfDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    onSingleTap: () -> Unit,
) {
    val density = LocalDensity.current
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(pageIndex) { mutableStateOf(false) }

    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    LaunchedEffect(pageIndex) {
        loadError = false
        val targetWidthPx = with(density) { 1080.dp.roundToPx() } // resolusi render tetap, cukup tajam untuk zoom hingga ~3x
        try {
            bitmap = withContext(pdfDispatcher) {
                val page = renderer.openPage(pageIndex)
                try {
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val width = targetWidthPx
                    val height = max(1, (width * ratio).roundToInt())
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            }
        } catch (_: Exception) {
            loadError = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            }
            .pointerInput(pageIndex) {
                // PENTING (fix bug swipe tidak jalan): detectTransformGestures bawaan
                // Compose langsung consume setiap gerakan 1 jari sebagai "pan", sehingga
                // HorizontalPager di atasnya tidak pernah kebagian gesture drag untuk
                // pindah halaman. Di sini kita hanya consume gesture kalau:
                // (a) ada 2 jari atau lebih (pinch-zoom), ATAU
                // (b) gambar sedang dalam kondisi zoom (scale > 1, jadi geser 1 jari
                //     dianggap "pan" di dalam gambar yang sudah membesar).
                // Kalau 1 jari & belum zoom sama sekali, event TIDAK di-consume di sini
                // supaya bisa diteruskan/ditangani HorizontalPager sebagai swipe ganti halaman.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val shouldHandleAsZoomPan = event.changes.size > 1 || scale > 1f
                        if (shouldHandleAsZoomPan) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadError -> Text(
                "Gagal merender halaman ini",
                color = MaterialTheme.colorScheme.error,
            )
            bitmap == null -> CircularProgressIndicator()
            else -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Halaman ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }
    }
}

@Composable
private fun AddBookmarkDialog(
    defaultHalaman: Int,
    onDismiss: () -> Unit,
    onSave: (halaman: Int, catatan: String) -> Unit,
) {
    var halamanText by remember { mutableStateOf(defaultHalaman.toString()) }
    var catatan by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Bookmark") },
        text = {
            Column {
                OutlinedTextField(
                    value = halamanText,
                    onValueChange = { halamanText = it; formError = null },
                    label = { Text("Halaman") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                formError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val halaman = halamanText.toIntOrNull()
                if (halaman == null || halaman < 1) {
                    formError = "Halaman harus berupa angka >= 1"
                } else {
                    onSave(halaman, catatan)
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun BookmarkListDialog(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onJumpTo: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmark") },
        text = {
            if (bookmarks.isEmpty()) {
                Text("Belum ada bookmark di materi ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    Text(
                        "Tap salah satu untuk lompat ke halaman itu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onJumpTo(bookmark) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Halaman ${bookmark.halaman}", fontWeight = FontWeight.Medium)
                                    bookmark.catatan?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { onDelete(bookmark) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus bookmark",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}
