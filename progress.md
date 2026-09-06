# Progress - Aplikasi Belajar Bahasa dari Ebook PDF

> File ini adalah riwayat pengerjaan. Selalu upload file ini di awal chat baru
> supaya Claude tahu progress terakhir dan next step yang harus dikerjakan.

---

## Status Terkini

**Task aktif:** Task 5 - PDF Viewer (Android)
**Status:** ✅ Task 4 (Daftar Isi - Backend + Android) CONFIRMED berhasil oleh user — `cargo build`, `cargo run`, `./gradlew assembleDebug`, `installDebug` semua sukses, dan pengujian manual di HP fisik (tambah bab, import JSON, export JSON) semua lolos.

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

### Task 4 — Daftar Isi (Backend + Android) (2026-09-06)

**Yang dikerjakan:**

1. **File baru ditambahkan:**
   - `backend/src/handlers/daftar_isi.rs` — semua handler untuk daftar isi:
     - `GET /api/materi/:materi_id/daftar-isi` — ambil daftar isi (nested tree bilingual, langsung berupa `judul_sumber`/`judul_target` sesuai bahasa materi, tidak perlu tahu kode bahasa dari sisi client)
     - `POST /api/materi/:materi_id/daftar-isi` — tambah bab/sub-bab manual
     - `PUT /api/daftar-isi/:id` — edit bab (partial update pakai COALESCE, sama pola dengan folder/materi)
     - `DELETE /api/daftar-isi/:id` — hapus bab (sub-bab di bawahnya ikut terhapus via `ON DELETE CASCADE`)
     - `POST /api/materi/:materi_id/daftar-isi/import` — import JSON, **mengganti TOTAL** daftar isi materi (delete-lalu-insert dalam satu transaksi). Validasi: `bahasa_sumber`/`bahasa_target` di body JSON wajib sama persis dengan bahasa milik materi, supaya judul yang tersimpan konsisten dengan bahasa yang dipakai endpoint GET & export.
     - `GET /api/materi/:materi_id/daftar-isi/export` — export ke bentuk JSON yang identik dengan bentuk import (round-trip backup-restore)
     - `GET /api/daftar-isi/template` — template statis contoh JSON
   - `android/.../ui/DaftarIsiScreen.kt` — UI daftar isi:
     - List bab & sub-bab bertingkat (indentasi sesuai depth), toggle tampilan Bilingual / Sumber saja / Target saja
     - Toolbar: Import JSON, Tambah Bab, Export
     - Menu titik-tiga per bab: Tambah Sub-bab, Edit, Hapus (dengan dialog konfirmasi yang menyebutkan jumlah sub-bab ikut terhapus)
     - Dialog Import JSON: tempel JSON → Preview (validasi + parsing client-side pakai kotlinx.serialization) → Konfirmasi Import. Ada juga tombol "Lihat contoh prompt AI" (prompt siap pakai untuk ChatGPT/Claude, mengikuti bahasa materi) dengan tombol salin ke clipboard.
     - Dialog Export: tampilkan JSON hasil export + tombol Salin ke clipboard.

2. **File diubah:**
   - `backend/src/models.rs` — tambah struct `DaftarIsiNode`, `DaftarIsiFlatRow`, `CreateBabRequest`, `UpdateBabRequest`, `ImportDaftarIsiRequest`, `ImportBabItem`, `ExportDaftarIsiResponse`
   - `backend/src/handlers/mod.rs` — tambah `pub mod daftar_isi;`
   - `backend/src/main.rs` — tambah routing daftar isi, import `put` dari `axum::routing`
   - `android/.../network/ApiModels.kt` — tambah data class `DaftarIsiNode`, `ImportBabItem`, `ImportDaftarIsiRequest`, `ExportDaftarIsiResponse`, `CreateBabRequest`, `UpdateBabRequest`
   - `android/.../network/ApiService.kt` — tambah endpoint Retrofit untuk semua di atas
   - `android/.../ui/HomeScreen.kt` — `MateriRow` sekarang menerima `onClick`, tap materi memanggil `onOpenMateri`
   - `android/.../MainActivity.kt` — navigasi state-based sederhana (`sealed class AppScreen { Home, DaftarIsi }`) tanpa menambah dependency Navigation Compose baru, supaya tap materi di Home bisa membuka `DaftarIsiScreen`

3. **Tidak ada migrasi SQL baru** — tabel `daftar_isi` & `daftar_isi_judul` sudah ada dari `migrations/0001_init.sql` Task 1, langsung dipakai.

**Keputusan desain penting (asumsi, dicatat supaya tidak lupa):**
- **Tidak ada tracking "selesai/belum" per bab** di skema DB saat ini (`progress_baca` hanya melacak progress per materi, bukan per bab). Chip "✓ Selesai" di `mockup-learn.html` bagian Daftar Isi **belum diimplementasikan**. Kalau nanti dibutuhkan, perlu kolom/tabel baru dulu di backend (misal `daftar_isi_selesai` per user+bab).
- **Level & urutan bab dibuat OTOMATIS oleh Android**, bukan input manual dari user, untuk menyederhanakan form: tambah dari toolbar → level 1, urutan = jumlah bab level-1 + 1. Tambah "Sub-bab" dari menu titik-tiga sebuah bab → level = level induk + 1, `parent_id` = bab itu, urutan = jumlah anak induk itu + 1. Drag & drop reorder **belum ada** (V-next, sesuai roadmap V1.1+ di `Konsep-program-learn.md`).
- **Import JSON mengganti TOTAL** seluruh daftar isi materi (bukan menambah/menggabungkan) — sudah diberi peringatan jelas di dialog import Android, tapi perlu diingat kalau nanti ada fitur "tambah dari JSON tanpa menghapus yang lama", itu endpoint terpisah, bukan modifikasi `import_daftar_isi` yang sudah ada.
- **`parent_id` tidak bisa di-set eksplisit jadi `NULL`** lewat `PUT /api/daftar-isi/:id` (sama keterbatasan dengan folder/materi di Task 2, karena pakai `COALESCE`). Kalau nanti perlu "pindahkan sub-bab jadi bab utama", perlu endpoint terpisah.
- Endpoint import mervalidasi `bahasa_sumber`/`bahasa_target` body harus sama persis dengan bahasa materi (bukan bahasa bebas) — mencegah data judul tersimpan dengan kode bahasa yang tidak match dengan yang dibaca balik oleh GET/export.
- Navigasi Android sengaja tidak pakai library Navigation Compose (biar tidak nambah dependency untuk 2 layar) — kalau di Task 5/6 layar bertambah banyak (PDF Viewer, Kamus, dst), pertimbangkan migrasi ke Navigation Compose supaya state back-stack lebih rapi daripada sealed class manual.

**Bug yang ditemukan & diperbaiki selama testing user:**
1. `error[E0277]: the trait bound &mut ...: Executor<'_> is not satisfied` di beberapa baris `daftar_isi.rs` (fungsi `insert_daftar_isi_items`) → **Penyebab:** `sqlx::Transaction` implement `Deref`/`DerefMut` ke `PoolConnection` (bukan ke dirinya sendiri) — yang implement `Executor` adalah `PoolConnection`, bukan `Transaction` langsung. Jumlah `*` yang dibutuhkan tergantung apakah `tx` itu **owned** `Transaction` (satu `*` sudah otomatis kena `Deref` ke `PoolConnection`, jadi `&mut *tx` sudah benar) atau **referensi** `&mut Transaction` seperti di fungsi helper ini (butuh dua `*`: `*` pertama membuka `&mut`-nya jadi `Transaction`, `*` kedua baru kena `Deref` ke `PoolConnection`) → **fix:** ganti `&mut *tx` jadi `&mut **tx` di dalam `insert_daftar_isi_items` (2 titik: `.fetch_one()` dan `.execute()`), sementara pemakaian `&mut *tx` di `import_daftar_isi` (tx owned) dibiarkan karena sudah benar.

**Kriteria berhasil (dikonfirmasi user):**
- [x] `cargo build` sukses tanpa error (setelah fix bug Executor di atas)
- [x] `cargo run` sukses, server jalan normal
- [x] `./gradlew assembleDebug` sukses tanpa error
- [x] `./gradlew installDebug` sukses, APK ter-install di HP fisik
- [x] App terbuka tanpa crash, tap materi di HomeScreen berhasil membuka halaman Daftar Isi
- [x] Tambah bab baru berhasil, muncul di list
- [x] Import JSON berhasil (mengganti daftar isi lama sesuai desain)
- [x] Export berhasil, JSON muncul di dialog dan bisa disalin

**Hasil:** ✅ TASK 4 SELESAI TOTAL (dikonfirmasi user — build backend & Android sukses, seluruh fungsi utama Daftar Isi diuji manual di HP: tambah bab, import JSON, export JSON, semua lolos)

---

## Next Step

Kandidat task berikutnya (urutan disarankan, tapi bisa didiskusikan ulang):

1. **Task 5 — PDF Viewer (Android)** ⬅️ **BERIKUTNYA**
   - Integrasi library PDF viewer (mis. AndroidPdfViewer / PDF.js via WebView) sesuai mockup bagian 3
   - Navigasi halaman (next/prev, swipe, jump to page), zoom (75%/100%/125%/150%)
   - Progress tracking: `PUT /api/materi/:id/progress` (endpoint ini sudah didefinisikan di `Konsep-program-learn.md` bagian 5.4 tapi **belum diimplementasikan** di backend — perlu dicek dulu apakah sudah ada dari task sebelumnya atau perlu dibuat baru di Task 5 ini)
   - Bookmark halaman: `GET/POST /api/materi/:id/bookmarks`, `DELETE /api/bookmarks/:id` (juga belum diimplementasikan, cek `Konsep-program-learn.md` bagian 5.4)
   - Tombol "Kosakata" di toolbar PDF viewer bisa disiapkan sebagai placeholder navigasi ke Task 6 (belum perlu fungsional penuh)
2. **Task 6 — Kamus & Kosakata (Backend + Android)**: search kamus, modal tambah kosakata, deduplikasi.

**PENTING:** Sebelum lanjut ke Task 5, selalu tanyakan ke user file-file project Android & backend terakhir untuk di-upload (terutama file-file yang baru dibuat/diubah di Task 4: `daftar_isi.rs`, `models.rs`, `main.rs`, `handlers/mod.rs`, `DaftarIsiScreen.kt`, `ApiModels.kt`, `ApiService.kt`, `HomeScreen.kt`, `MainActivity.kt`), jangan berasumsi dari `progress.md` saja bahwa kode di atas 100% sama dengan yang ada di device/repo user — apalagi setelah ada fix manual langsung di file `daftar_isi.rs` pada sesi ini.

Juga perlu dicek: apakah tabel `progress_baca` dan `bookmarks` (sudah ada skemanya dari migrasi Task 1) sudah punya endpoint API-nya atau belum, supaya Task 5 tidak duplikat kerjaan.

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
│       ├── main.rs            # + routing daftar isi (Task 4)
│       ├── state.rs
│       ├── error.rs
│       ├── models.rs          # + struct daftar isi (Task 4)
│       ├── storage.rs
│       └── handlers/
│           ├── mod.rs         # + mod daftar_isi (Task 4)
│           ├── folders.rs
│           ├── materi.rs
│           └── daftar_isi.rs  # baru (Task 4)
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
│               ├── MainActivity.kt        # navigasi Home <-> DaftarIsi (Task 4)
│               ├── BackendPrefs.kt        # (Task 3)
│               ├── network/
│               │   ├── ApiClient.kt       # (Task 3)
│               │   ├── ApiModels.kt       # + model daftar isi (Task 4)
│               │   └── ApiService.kt      # + endpoint daftar isi (Task 4)
│               ├── ui/
│               │   ├── HomeScreen.kt      # + onOpenMateri (Task 4)
│               │   ├── DaftarIsiScreen.kt # baru (Task 4)
│               │   └── theme/
│               │       └── Theme.kt       # (Task 3)
│               └── util/
│                   ├── DateUtils.kt       # (Task 3)
│                   └── FileUtils.kt       # (Task 3)
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
