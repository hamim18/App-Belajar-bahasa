# Progress - Aplikasi Belajar Bahasa dari Ebook PDF

> File ini adalah riwayat pengerjaan. Selalu upload file ini di awal chat baru
> supaya Claude tahu progress terakhir dan next step yang harus dikerjakan.

---

## Status Terkini

**Task aktif:** Task 4 - Daftar Isi (Backend + Android)
**Status:** ✅ Task 3 (Halaman Home / File Manager - Android) CONFIRMED berhasil oleh user — build sukses (`assembleDebug`), berhasil install ke HP fisik via wireless debugging (`installDebug`), app jalan menampilkan folder & materi dari backend Rust.

---

## Riwayat Pengerjaan

### Task 1 — Setup Skeleton Backend + Android (2026-09-05)

**Hasil:** ✅ TASK 1 SELESAI TOTAL (dikonfirmasi user, tanpa error)

Lihat detail lengkap di riwayat versi sebelumnya (struktur project, kriteria berhasil, dll — tidak diulang di sini supaya file tidak kepanjangan).

---

### Task 1.1 — Setup Git & Push ke GitHub (2026-09-05)

**Hasil:** ✅ SELESAI — `git push` sukses, repo di GitHub sudah menampilkan struktur `backend/` dan `android/`.

- **Remote GitHub:** `git@github.com:hamim18/App-Belajar-bahasa.git`
- **Branch utama:** `main`

---

### Task 2 — CRUD Folder & Materi (Backend) (2026-09-05)

**Yang dikerjakan:**
1. Modularisasi backend jadi beberapa file:
   - `src/state.rs` — `AppState` (db pool + `default_user_id`)
   - `src/error.rs` — `AppError` terpadu (NotFound/BadRequest/Internal) jadi response JSON rapi
   - `src/models.rs` — struct `Folder`, `FolderListItem`, `Materi`, request DTO
   - `src/storage.rs` — helper simpan file PDF & hitung halaman
   - `src/handlers/folders.rs` — CRUD folder
   - `src/handlers/materi.rs` — CRUD materi + upload PDF
   - `src/main.rs` — daftar route baru + setup default user + storage dir

2. **Endpoint baru:**
   - `GET /api/folders?parent_id=` — list folder (root kalau parent_id kosong), sudah termasuk `jumlah_materi` & `jumlah_subfolder`
   - `POST /api/folders` — buat folder
   - `GET /api/folders/:id` — detail folder
   - `PUT /api/folders/:id` — update folder (partial update pakai COALESCE)
   - `DELETE /api/folders/:id` — hapus folder + subfolder + materi + file PDF fisik (rekursif)
   - `GET /api/folders/:folder_id/materi` — list materi dalam folder
   - `POST /api/materi` — upload materi baru (multipart: judul, folder_id, bahasa_sumber?, bahasa_target?, file). Otomatis hitung total_halaman pakai crate `lopdf`. Bahasa default diambil dari folder kalau tidak dikirim.
   - `GET /api/materi/:id` — detail materi
   - `PUT /api/materi/:id` — update metadata materi (judul/folder/bahasa, TIDAK termasuk ganti file)
   - `DELETE /api/materi/:id` — hapus materi + file PDF fisik

3. **Dependency baru:** `lopdf = "0.32"` (hitung halaman PDF), axum feature `multipart` diaktifkan.

**Keputusan desain penting (asumsi, dicatat supaya tidak lupa):**
- **Belum ada sistem login/auth.** Server otomatis membuat 1 "default user" saat pertama kali start (disimpan di tabel `users`, diambil ulang tiap restart via query `ORDER BY created_at LIMIT 1`). Semua folder/materi yang dibuat lewat API "milik" user ini untuk sementara. Kalau nanti auth dibuat, bagian ini perlu direvisi (ganti `default_user_id` jadi user dari token/session asli).
- File PDF disimpan di `backend/storage/pdf/<uuid>.pdf` (nama asli tidak dipakai sebagai path, demi keamanan). Path relatif ini dijalankan dari working directory tempat `cargo run` dieksekusi — kalau nanti deploy pastikan working directory konsisten atau ubah jadi path absolut dari env var.
- Update folder/materi pakai `COALESCE` per field → field yang tidak dikirim di JSON tidak berubah. **Keterbatasan:** belum bisa set `parent_id` folder eksplisit jadi `NULL` (pindah ke root) lewat update biasa, karena COALESCE tidak bisa bedakan "tidak dikirim" vs "dikirim null". Kalau nanti dibutuhkan, perlu endpoint terpisah misal `POST /api/folders/:id/move-to-root`.
- Ganti file PDF pada materi yang sudah ada **belum didukung** — kalau user mau ganti file, alurnya: hapus materi lama → upload materi baru.
- Kolom `created_at`/`updated_at` di DB bertipe `TIMESTAMP` (tanpa timezone) sesuai migrasi awal — jadi di Rust dipetakan ke `chrono::NaiveDateTime`, BUKAN `DateTime<Utc>`. Penting diingat untuk task-task berikutnya yang menambah tabel/struct baru, supaya tidak kena error "mismatched types" lagi.
- `MAX_UPLOAD_SIZE` di-set 50MB via `DefaultBodyLimit` khusus di route `POST /api/materi`.

**Bug yang ditemukan & diperbaiki selama testing user:**
1. `mismatched types` saat decode `created_at`/`updated_at` → fix: ganti tipe struct dari `DateTime<Utc>` ke `NaiveDateTime` (lihat catatan di atas).
2. `column reference "bahasa_sumber" is ambiguous` saat update materi → fix: qualify kolom di `COALESCE(...)` dengan alias tabel (`m.bahasa_sumber`, dst) karena query JOIN ke `folders` yang punya nama kolom sama.

**Kriteria berhasil (dikonfirmasi user, semua via curl):**
- [x] `cargo build` sukses tanpa error
- [x] `cargo run` sukses, server jalan normal, default user ke-generate
- [x] POST folder berhasil, GET folder (list & detail) menampilkan data + jumlah materi/subfolder
- [x] PUT folder berhasil update nama folder
- [x] POST materi dengan upload PDF berhasil, `total_halaman` terisi otomatis dan benar (169 halaman terhitung tepat)
- [x] GET materi per folder & per id berhasil
- [x] PUT materi berhasil update judul
- [x] DELETE materi berhasil, dan file PDF fisik di `backend/storage/pdf/` ikut terhapus (dicek manual via `ls`, hasilnya kosong)
- [x] DELETE folder berhasil

**Hasil:** ✅ TASK 2 SELESAI TOTAL (dikonfirmasi user, tanpa error)

---

### Task 3 — Halaman Home / File Manager (Android) (2026-09-06)

**Yang dikerjakan:**

1. **File baru ditambahkan:**
   - `network/ApiModels.kt` — data class `FolderListItem`, `Materi`, `CreateFolderRequest`, `DeletedResponse` (`@Serializable`, field disamakan persis dengan struct JSON dari backend Rust, termasuk tipe `Long` untuk `jumlah_materi`/`jumlah_subfolder` dan `Int` untuk `total_halaman`)
   - `network/ApiService.kt` — interface Retrofit: `listFolders`, `getFolder`, `createFolder`, `deleteFolder`, `listMateriByFolder`, `createMateri` (multipart upload PDF), `deleteMateri`
   - `network/ApiClient.kt` — builder Retrofit + OkHttp (base URL dinamis dari `BackendPrefs`, logging interceptor, timeout upload 60 detik untuk PDF besar)
   - `BackendPrefs.kt` — simpan IP & port backend Rust di `SharedPreferences` supaya user tidak perlu input ulang tiap buka app
   - `util/DateUtils.kt` — format `updated_at` jadi teks relatif ("2 hari lalu", dst) untuk ditampilkan di list
   - `util/FileUtils.kt` — resolve nama file asli dari `Uri` hasil file picker (untuk ditampilkan sebelum upload)
   - `ui/HomeScreen.kt` — layar utama: list folder + materi (drill-down per folder pakai `folderStack`), dialog Tambah Folder, dialog Tambah Materi (pilih file PDF via `ActivityResultContracts.OpenDocument` + upload multipart), dialog pengaturan IP backend, pull-to-reload, empty/error/loading state
   - `ui/theme/Theme.kt` — tema Compose dasar (warna sesuai design system di `Konsep-program-learn.md`: primary `#2C3E7A`, dst)

2. **File diubah:**
   - `MainActivity.kt` — skeleton test-koneksi Task 1 diganti jadi render `HomeScreen()` langsung
   - `app/build.gradle.kts` — dependency baru: Retrofit 2.11.0, `retrofit2-kotlinx-serialization-converter` 1.0.0, `kotlinx-serialization-json` 1.6.3, OkHttp 4.12.0 + logging-interceptor

3. **Fitur yang jalan (sesuai mockup bagian 1 - Home/File Manager):**
   - Fetch & tampilkan folder + materi dari `GET /api/folders` dan `GET /api/folders/:id/materi`
   - Navigasi masuk/keluar folder (tap folder masuk, tombol back / gesture back keluar)
   - Tambah folder baru (`POST /api/folders`)
   - Tambah materi baru dengan upload PDF asli dari penyimpanan HP (`POST /api/materi`, multipart)
   - Setting IP:port backend tersimpan persisten di HP

**Masalah yang muncul & solusi:**

| # | Error saat `./gradlew assembleDebug` | Penyebab | Solusi |
|---|---|---|---|
| 1 | `Unresolved reference: converter` dan `Unresolved reference: asConverterFactory` di `ApiClient.kt` | Import salah package. Library `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` punya package **`com.jakewharton.retrofit2.converter.kotlinx.serialization`**, bukan `retrofit2.converter.kotlinx.serialization` (yang itu untuk converter resmi Retrofit seperti Gson/Moshi) | Ganti baris import di `ApiClient.kt` jadi `import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory` |
| 2 | `This material API is experimental and is likely to change or to be removed in the future.` di `HomeScreen.kt` (pemakaian `TopAppBar`) | `TopAppBar` Material3 masih ditandai `@ExperimentalMaterial3Api`. Tanpa opt-in, Kotlin compiler menjadikannya **compile error**, bukan sekadar warning | Tambah `freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")` di blok `kotlinOptions` pada `app/build.gradle.kts`, supaya berlaku untuk seluruh modul tanpa perlu anotasi `@OptIn` manual di tiap file |
| 3 | `com.android.builder.testing.api.DeviceException: No connected devices!` saat `./gradlew installDebug` | Bukan bug kode — `adb` belum terhubung ke HP fisik lewat wireless debugging (device baru, belum pernah di-pair) | Pairing sekali via `adb pair IP:PAIRING_PORT` + kode 6 digit dari HP (Settings → Developer options → Wireless debugging → Pair device with pairing code), lalu setiap sesi baru konek ulang via `adb connect IP:PORT` (port koneksi beda dari port pairing, dilihat di layar utama Wireless debugging) |

**Kriteria berhasil — build & install (dikonfirmasi user):**
- [x] `./gradlew assembleDebug` sukses tanpa error setelah fix import & opt-in Material3
- [x] `adb pair` + `adb connect` berhasil, `adb devices` menampilkan HP dengan status `device`
- [x] `./gradlew installDebug` sukses, APK ter-install di HP fisik
- [x] App terbuka tanpa crash, HomeScreen tampil
- [x] Bisa isi IP:port backend lewat dialog pengaturan
- [x] List folder & materi dari backend Rust tampil di HP (HP & laptop di WiFi sama)

**Kriteria berhasil — cek fungsi satu-satu di HP fisik (dikonfirmasi user, SEMUA SUKSES):**
- [x] Halaman root muncul, tidak error, menampilkan folder yang sudah ada (JLPT N5, dst. dari data lama)
- [x] Tap + Folder → isi nama → folder baru muncul di list
- [x] Tap salah satu folder → masuk ke dalamnya (judul app bar berubah jadi nama folder, tombol back muncul)
- [x] Di dalam folder, tombol + Materi aktif → pilih file PDF dari HP → isi judul → Upload → materi muncul di list dengan jumlah halaman terisi otomatis
- [x] Tombol back / gesture back mengembalikan ke folder induk / root
- [x] Ketik sesuatu di kolom cari → list ter-filter
- [x] Matikan backend (Ctrl+C di terminal `cargo run`) → tap "Coba lagi" → muncul pesan error yang jelas, app tidak crash

**Hasil:** ✅ TASK 3 SELESAI TOTAL (dikonfirmasi user — build sukses, install ke HP fisik sukses, dan seluruh fungsi utama Home/File Manager diuji manual satu-satu di HP, semua lolos)

---

## Next Step

Kandidat task berikutnya (urutan disarankan, tapi bisa didiskusikan ulang):

1. **Task 4 — Daftar Isi (Backend + Android)** ⬅️ **BERIKUTNYA**
   - Backend: endpoint import JSON daftar isi (`POST /api/materi/:id/daftar-isi/import`), export (`GET .../export`), manual CRUD bab/sub-bab (`POST`/`PUT`/`DELETE /api/daftar-isi/:id`)
   - Android: UI daftar isi bilingual sesuai mockup bagian 2 (list bab + sub-bab dengan indentasi, toggle Bilingual/JP-only/ID-only, tombol Import JSON & Tambah Manual, progress bar bab selesai)
2. **Task 5 — PDF Viewer (Android)**: integrasi library PDF viewer, navigasi halaman, progress tracking (`PUT /api/materi/:id/progress`).
3. **Task 6 — Kamus & Kosakata (Backend + Android)**: search kamus, modal tambah kosakata, deduplikasi.

**PENTING:** Sebelum lanjut ke Task 4, selalu tanyakan ke user file-file project Android & backend terakhir untuk di-upload (terutama file-file yang baru dibuat/diubah di Task 3: `ApiClient.kt`, `ApiModels.kt`, `ApiService.kt`, `HomeScreen.kt`, `app/build.gradle.kts`, plus struct backend `daftar_isi` & `daftar_isi_judul` di `models.rs`), jangan berasumsi dari `progress.md` saja bahwa kode di atas 100% sama dengan yang ada di device/repo user.

---

## Struktur Project (terkini)

```
aplikasi-belajar-bahasa/
├── backend/                  # Rust + Axum + PostgreSQL
│   ├── Cargo.toml
│   ├── .env.example
│   ├── .gitignore            # + storage/pdf/*.pdf
│   ├── migrations/
│   │   └── 0001_init.sql
│   ├── storage/
│   │   └── pdf/               # File PDF hasil upload (di-gitignore)
│   └── src/
│       ├── main.rs
│       ├── state.rs
│       ├── error.rs
│       ├── models.rs
│       ├── storage.rs
│       └── handlers/
│           ├── mod.rs
│           ├── folders.rs
│           └── materi.rs
├── android/                  # Kotlin + Jetpack Compose
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts   # + dependency Retrofit/OkHttp/Serialization (Task 3)
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/belajarbahasa/app/
│               ├── MainActivity.kt        # render HomeScreen() (Task 3)
│               ├── BackendPrefs.kt        # baru (Task 3)
│               ├── network/
│               │   ├── ApiClient.kt       # baru (Task 3)
│               │   ├── ApiModels.kt       # baru (Task 3)
│               │   └── ApiService.kt      # baru (Task 3)
│               ├── ui/
│               │   ├── HomeScreen.kt      # baru (Task 3)
│               │   └── theme/
│               │       └── Theme.kt       # baru (Task 3)
│               └── util/
│                   ├── DateUtils.kt       # baru (Task 3)
│                   └── FileUtils.kt       # baru (Task 3)
├── Konsep-program-learn.md
├── mockup-learn.html
├── progress.md               # File ini
└── README.md                 # Panduan setup & testing
```

---

## Info Repo

- **Remote GitHub:** `git@github.com:hamim18/App-Belajar-bahasa.git`
- **Branch utama:** `main`
- **Akses:** SSH (bukan HTTPS)
