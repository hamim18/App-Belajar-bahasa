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
import com.belajarbahasa.app.ui.theme.BelajarBahasaTheme

/**
 * Navigasi antar layar aplikasi. Sengaja TIDAK pakai library Navigation
 * Compose (belum jadi dependency proyek ini) - untuk MVP dengan 2 layar,
 * sealed class + state switching sederhana ini sudah cukup dan tidak
 * menambah kompleksitas/dependency baru. Kalau nanti layar bertambah
 * banyak (PDF Viewer, Kamus, dst di Task 5/6), pertimbangkan migrasi ke
 * Navigation Compose.
 */
private sealed class AppScreen {
    data object Home : AppScreen()
    data class DaftarIsi(val materi: Materi) : AppScreen()
}

/**
 * MainActivity.
 *
 * Task 3: skeleton test-koneksi diganti HomeScreen (folder & materi dari
 * backend Rust).
 * Task 4: tap materi di HomeScreen sekarang membuka DaftarIsiScreen
 * (bab & sub-bab bilingual, import/export JSON, CRUD manual).
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
                        )
                        is AppScreen.DaftarIsi -> DaftarIsiScreen(
                            materiId = current.materi.id,
                            judulMateri = current.materi.judul,
                            bahasaSumber = current.materi.bahasa_sumber,
                            bahasaTarget = current.materi.bahasa_target,
                            onBack = { screen = AppScreen.Home },
                        )
                    }
                }
            }
        }
    }
}
