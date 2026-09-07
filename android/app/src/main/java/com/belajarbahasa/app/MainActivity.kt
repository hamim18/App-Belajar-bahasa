package com.belajarbahasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.belajarbahasa.app.network.Materi
import com.belajarbahasa.app.ui.DaftarIsiScreen
import com.belajarbahasa.app.ui.HomeScreen
import com.belajarbahasa.app.ui.KamusDetailScreen
import com.belajarbahasa.app.ui.KamusScreen
import com.belajarbahasa.app.ui.PdfViewerScreen
import com.belajarbahasa.app.ui.theme.BelajarBahasaTheme

/**
 * Navigasi antar layar aplikasi. Sengaja TIDAK pakai library Navigation
 * Compose (belum jadi dependency proyek ini) - sealed class + state
 * switching sederhana ini masih cukup untuk jumlah layar saat ini. Kalau
 * nanti layar terus bertambah, pertimbangkan migrasi ke Navigation Compose.
 */
private sealed class AppScreen {
    data object Home : AppScreen()
    data class DaftarIsi(val materi: Materi) : AppScreen()
    data class PdfViewer(val materi: Materi, val startHalaman: Int?) : AppScreen()
    data object Kamus : AppScreen()
    data class KamusDetail(val kataId: String) : AppScreen()
}

/**
 * MainActivity.
 *
 * Task 3: skeleton test-koneksi diganti HomeScreen (folder & materi dari
 * backend Rust).
 * Task 4: tap materi di HomeScreen sekarang membuka DaftarIsiScreen
 * (bab & sub-bab bilingual, import/export JSON, CRUD manual).
 * Task 5: tombol "📖 Baca" atau ikon ▶ per bab di DaftarIsiScreen sekarang
 * membuka PdfViewerScreen (render PDF, swipe/zoom halaman, progress baca,
 * bookmark).
 * Task 6: ikon 📖 di app bar HomeScreen membuka KamusScreen (search kamus),
 * tap satu kata membuka KamusDetailScreen. Tombol "📝 Kosakata" di
 * PdfViewerScreen sekarang membuka ModalBottomSheet KosakataSheet (bukan
 * lagi navigasi layar penuh - lihat PdfViewerScreen.kt).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BelajarBahasaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

                    when (val current = screen) {
                        is AppScreen.Home -> HomeScreen(
                            onOpenMateri = { materi -> screen = AppScreen.DaftarIsi(materi) },
                            onOpenKamus = { screen = AppScreen.Kamus },
                        )
                        is AppScreen.DaftarIsi -> DaftarIsiScreen(
                            materiId = current.materi.id,
                            judulMateri = current.materi.judul,
                            bahasaSumber = current.materi.bahasa_sumber,
                            bahasaTarget = current.materi.bahasa_target,
                            totalHalaman = current.materi.total_halaman,
                            onBack = { screen = AppScreen.Home },
                            onOpenPdf = { halaman ->
                                screen = AppScreen.PdfViewer(current.materi, halaman)
                            },
                        )
                        is AppScreen.PdfViewer -> PdfViewerScreen(
                            materiId = current.materi.id,
                            judulMateri = current.materi.judul,
                            totalHalamanAwal = current.materi.total_halaman,
                            bahasaSumber = current.materi.bahasa_sumber,
                            bahasaTarget = current.materi.bahasa_target,
                            startHalaman = current.startHalaman,
                            onBack = { screen = AppScreen.DaftarIsi(current.materi) },
                        )
                        is AppScreen.Kamus -> KamusScreen(
                            onBack = { screen = AppScreen.Home },
                            onOpenDetail = { kataId -> screen = AppScreen.KamusDetail(kataId) },
                        )
                        is AppScreen.KamusDetail -> KamusDetailScreen(
                            kataId = current.kataId,
                            onBack = { screen = AppScreen.Kamus },
                        )
                    }
                }
            }
        }
    }
}
