package com.belajarbahasa.app.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Membangun ulang instance Retrofit setiap kali baseUrl (IP backend) berubah,
 * dan meng-cache-nya selama IP tidak berubah supaya tidak bikin instance baru
 * di setiap recomposition.
 *
 * writeTimeout dibuat lebih panjang (60 detik) karena upload PDF + hitung
 * halaman di server (lopdf) butuh waktu lebih dari request JSON biasa.
 */
object ApiClient {
    private var cachedBaseUrl: String? = null
    private var cachedService: ApiService? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun get(baseUrl: String): ApiService {
        val existing = cachedService
        if (existing != null && cachedBaseUrl == baseUrl) {
            return existing
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        val service = retrofit.create(ApiService::class.java)
        cachedBaseUrl = baseUrl
        cachedService = service
        return service
    }
}
