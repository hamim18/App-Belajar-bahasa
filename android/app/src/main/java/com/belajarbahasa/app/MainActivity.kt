package com.belajarbahasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.belajarbahasa.app.ui.HomeScreen
import com.belajarbahasa.app.ui.theme.BelajarBahasaTheme

/**
 * MainActivity - Task 3: Halaman Home / File Manager.
 *
 * Skeleton test-koneksi dari Task 1 sudah digantikan HomeScreen yang
 * menampilkan folder & materi asli dari backend Rust, dengan navigasi
 * drill-down per folder serta form Tambah Folder & Tambah Materi
 * (upload PDF via multipart).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BelajarBahasaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}
