package com.belajarbahasa.app.util

import java.time.Duration
import java.time.LocalDateTime

/**
 * Backend mengirim NaiveDateTime Rust sebagai string ISO tanpa timezone,
 * contoh: "2026-09-05T22:09:23.536795". LocalDateTime.parse(text) Kotlin/Java
 * bisa langsung membaca format ini (ISO_LOCAL_DATE_TIME, jumlah digit
 * pecahan detik fleksibel).
 */
fun formatRelativeTime(iso: String): String {
    return try {
        val dt = LocalDateTime.parse(iso)
        val duration = Duration.between(dt, LocalDateTime.now())
        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()
        when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit lalu"
            hours < 24 -> "$hours jam lalu"
            days < 30 -> "$days hari lalu"
            else -> dt.toLocalDate().toString()
        }
    } catch (e: Exception) {
        iso
    }
}
