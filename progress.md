# Progress - Aplikasi Belajar Bahasa dari Ebook PDF

> File ini adalah riwayat pengerjaan. Selalu upload file ini di awal chat baru
> supaya Claude tahu progress terakhir dan next step yang harus dikerjakan.

---

## Status Terkini

**Task aktif:** Task 1.1 - Setup Git & push ke GitHub
**Status:** ✅ Task 1 (skeleton Backend + Android) CONFIRMED berhasil oleh user — compile Rust sukses, build Android sukses, koneksi HP ke backend sukses. Lanjut setup Git.

---

## Riwayat Pengerjaan

### Task 1 — Setup Skeleton Backend + Android (2026-09-05)

**Yang dikerjakan:**
1. Backend Rust (Axum + SQLx + PostgreSQL)
   - Struktur project di `backend/`
   - Endpoint `GET /api/health` → cek status server & koneksi DB
   - Migrasi database awal (`migrations/0001_init.sql`) berisi seluruh skema tabel dari dokumen konsep: `users`, `folders`, `materi`, `daftar_isi`, `daftar_isi_judul`, `kamus`, `kamus_terjemahan`, `kosakata_konteks`, `progress_baca`, `bookmarks` — lengkap dengan index.
   - Migrasi otomatis jalan saat server start (`sqlx::migrate!`).
   - Server bind ke `0.0.0.0:8080` supaya bisa diakses dari HP di jaringan WiFi yang sama.
   - CORS diaktifkan (allow any) untuk memudahkan testing dari Android.

2. Android Skeleton (Kotlin + Jetpack Compose)
   - Struktur project di `android/` (buildable via `gradle` CLI, tanpa Android Studio)
   - 1 Activity (`MainActivity.kt`) dengan UI Compose sederhana:
     - Input IP backend
     - Tombol "Test Koneksi Backend" → panggil `GET http://{ip}:8080/api/health`
     - Menampilkan hasil response
   - `usesCleartextTraffic="true"` diaktifkan (khusus dev, karena backend masih HTTP biasa di jaringan lokal)
   - minSdk 26, targetSdk 34, compileSdk 34, Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06.00

**Catatan teknis:**
- Backend TIDAK di-compile penuh di sandbox Claude karena sandbox hanya punya Rust 1.75 (dari apt Ubuntu) yang tidak kompatibel dengan crate modern (butuh fitur `edition2024`). Kode sudah direview manual. Rust di Arch Linux (rolling release) seharusnya jauh lebih baru dan tidak bermasalah.
- Android project TIDAK di-build di sandbox karena sandbox tidak punya akses ke Google Maven repository (`dl.google.com`) untuk download Android Gradle Plugin & library AndroX/Compose. Build pertama HARUS dilakukan di device user.
- Belum ada Gradle Wrapper (`gradlew`). User diarahkan pakai `gradle` command langsung dulu (sudah terpasang), atau generate wrapper sendiri kalau mau (`gradle wrapper --gradle-version 8.7`).

**Kriteria berhasil (dikonfirmasi user):**
- [x] `cargo build` di `backend/` sukses tanpa error
- [x] `cargo run` sukses, server jalan, dan `curl http://localhost:8080/api/health` mengembalikan JSON `{"status":"ok","db_connected":true,...}`
- [x] `gradle assembleDebug` di `android/` sukses menghasilkan APK
- [x] APK ter-install ke HP fisik via `adb` (wireless debugging) dan bisa dibuka
- [x] Tombol "Test Koneksi Backend" di app berhasil menampilkan response dari backend (HP dan komputer harus di WiFi yang sama)

**Hasil:** ✅ TASK 1 SELESAI TOTAL (dikonfirmasi user, tanpa error)

---

### Task 1.1 — Setup Git & Push ke GitHub (2026-09-05)

**Yang dikerjakan:**
- Tambah `.gitignore` root (pelengkap `.gitignore` di `backend/` dan `android/` yang sudah ada dari Task 1)
- Panduan step-by-step: `git init` → `git add` → `git commit` → setup SSH key (jika belum) → `git remote add` → `git push`
- Repo GitHub: `git@github.com:hamim18/App-Belajar-bahasa.git`

**Kriteria berhasil (harus dicek oleh user):**
- [ ] `git remote -v` menunjukkan remote `origin` mengarah ke repo yang benar
- [ ] `git push -u origin main` sukses tanpa error
- [ ] Repo di GitHub (web) menampilkan struktur folder `backend/` dan `android/` beserta isinya

---

## Next Step (setelah Task 1.1 / git push dikonfirmasi berhasil)

Kandidat task berikutnya (urutan disarankan, tapi bisa didiskusikan ulang):

1. **Task 2 — CRUD Folder & Materi (Backend)**: endpoint create/read/update/delete folder & materi, upload file PDF ke storage, ekstrak total halaman.
2. **Task 3 — Halaman Home / File Manager (Android)**: UI sesuai mockup `mockup-learn.html` bagian 1, fetch data folder/materi dari backend, tampilkan progress bar.
3. **Task 4 — Daftar Isi (Backend + Android)**: endpoint import JSON daftar isi, manual CRUD bab/sub-bab, UI daftar isi bilingual.
4. **Task 5 — PDF Viewer (Android)**: integrasi library PDF viewer, navigasi halaman, progress tracking.
5. **Task 6 — Kamus & Kosakata (Backend + Android)**: search kamus, modal tambah kosakata, deduplikasi.

**PENTING:** Sebelum lanjut ke task berikutnya, selalu tanyakan ke user file-file project terakhir (backend & android) untuk di-upload, jangan berasumsi dari `progress.md` saja bahwa kode di atas 100% sama dengan yang ada di device user (user mungkin sudah ubah manual).

---

## Struktur Project

```
aplikasi-belajar-bahasa/
├── backend/                  # Rust + Axum + PostgreSQL
│   ├── Cargo.toml
│   ├── .env.example
│   ├── migrations/
│   │   └── 0001_init.sql
│   └── src/
│       └── main.rs
├── android/                  # Kotlin + Jetpack Compose
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/belajarbahasa/app/MainActivity.kt
│           └── res/values/strings.xml
├── .gitignore                # Gitignore root (root-level files/OS junk)
├── progress.md               # File ini
└── README.md                 # Panduan setup & testing
```

---

## Info Repo

- **Remote GitHub:** `git@github.com:hamim18/App-Belajar-bahasa.git`
- **Branch utama:** `main`
- **Akses:** SSH (bukan HTTPS)
