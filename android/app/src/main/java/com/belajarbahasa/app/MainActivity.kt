package com.belajarbahasa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * MainActivity - Skeleton awal.
 * Tujuan: memastikan pipeline build (gradle -> APK -> install via adb wireless) berjalan,
 * dan bisa melakukan panggilan HTTP sederhana ke backend Rust.
 *
 * UI penuh (Home, Daftar Isi, PDF Viewer, dll) akan dibangun di task-task berikutnya
 * mengikuti referensi mockup-learn.html.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SkeletonScreen()
                }
            }
        }
    }
}

@Composable
fun SkeletonScreen() {
    var backendIp by remember { mutableStateOf("192.168.1.X") }
    var statusText by remember { mutableStateOf("Belum dicek") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📚 Belajar Bahasa",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Skeleton Project - Task 1",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = backendIp,
            onValueChange = { backendIp = it },
            label = { Text("IP Backend (contoh: 192.168.1.10)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                statusText = "Menghubungi backend..."
                scope.launch {
                    val result = checkBackendHealth(backendIp)
                    statusText = result
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Mengecek..." else "Test Koneksi Backend")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = statusText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Memanggil GET http://{ip}:8080/api/health dari backend Rust.
 * Ditulis tanpa dependency HTTP client eksternal supaya skeleton tetap ringan.
 */
suspend fun checkBackendHealth(ip: String): String = withContext(Dispatchers.IO) {
    try {
        val url = URL("http://$ip:8080/api/health")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            "✅ Terhubung!\n$response"
        } else {
            "⚠️ Response code: $responseCode"
        }
    } catch (e: Exception) {
        "❌ Gagal konek: ${e.message}"
    }
}
