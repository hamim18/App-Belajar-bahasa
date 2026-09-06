# Progress - Aplikasi Belajar Bahasa dari Ebook PDF

> File ini adalah riwayat pengerjaan. Selalu upload file ini di awal chat baru
> supaya Claude tahu progress terakhir dan next step yang harus dikerjakan.

---

## Status Terkini

**Task aktif:** Task 6 - Kamus & Kosakata (Backend + Android)
**Status:** ✅ Task 5 (PDF Viewer - Backend + Android) CONFIRMED berhasil oleh user — `cargo build`, `cargo run`, `./gradlew assembleDebug`, `installDebug` semua sukses, dan pengujian manual di HP fisik (buka PDF dari Daftar Isi, swipe halaman, pinch/double-tap zoom, tambah/hapus/lompat ke bookmark, resume dari halaman terakhir) semua lolos setelah 1 ronde perbaikan bug kecil.

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

### Task 5 — PDF Viewer, Progress & Bookmark (Backend + Android) (2026-09-06)

**Yang dikerjakan:**

1. **File baru ditambahkan (backend):**
   - `backend/src/handlers/progress.rs`:
     - `GET /api/materi/:id/progress` — ambil progress baca user untuk materi ini. Kalau belum pernah ada row di DB, kembalikan default (halaman 0, 0%) TANPA membuat row baru.
     - `PUT /api/materi/:id/progress` — upsert progress (`INSERT ... ON CONFLICT (materi_id, user_id) DO UPDATE`). `progress_persen` dihitung di backend dari `total_halaman` materi (bukan dikirim dari Android), supaya rumusnya konsisten di satu tempat.
   - `backend/src/handlers/bookmarks.rs`:
     - `GET /api/materi/:materi_id/bookmarks` — list bookmark milik user untuk materi ini, urut per halaman.
     - `POST /api/materi/:materi_id/bookmarks` — tambah bookmark (halaman + catatan opsional).
     - `DELETE /api/bookmarks/:id` — hapus bookmark (query join ke `materi`+`folders` untuk pastikan bookmark itu milik user yang benar).
   - `android/.../ui/PdfViewerScreen.kt` — layar utama PDF Viewer:
     - Download file PDF sekali dari `GET /api/materi/:id/file`, di-cache di `cacheDir/pdf_cache/<materiId>.pdf` (tidak didownload ulang kalau cache sudah ada).
     - Render tiap halaman jadi `Bitmap` pakai `android.graphics.pdf.PdfRenderer` bawaan Android di dispatcher single-thread khusus (PdfRenderer tidak thread-safe untuk akses paralel).
     - Navigasi halaman pakai `HorizontalPager` (Compose Foundation) + tombol Prev/Next + progress bar linear.
     - Zoom & pan: pinch-zoom dan double-tap-zoom lewat `graphicsLayer` (scale/offset), TANPA render ulang bitmap di resolusi berbeda (bitmap dirender sekali di resolusi tetap 1080px lebar, cukup tajam untuk zoom hingga ~3.5x).
     - Tap di tengah layar toggle toolbar atas/bawah.
     - Progress baca disimpan ke backend dengan debounce 800ms setelah pindah halaman (best-effort, gagal simpan tidak memblokir baca — sesuai filosofi offline-first).
     - Bookmark: tombol tambah (dialog input halaman+catatan) dan tombol lihat daftar (dialog list, klik item untuk lompat ke halaman itu, tombol hapus terpisah).
     - Tombol "📝 Kosakata" masih placeholder (Toast "Fitur Kosakata akan hadir di Task 6").

2. **File diubah (backend):**
   - `backend/src/models.rs` — tambah struct `ProgressBaca`, `UpdateProgressRequest`, `Bookmark`, `CreateBookmarkRequest`.
   - `backend/src/handlers/mod.rs` — tambah `pub mod progress;` dan `pub mod bookmarks;`.
   - `backend/src/handlers/materi.rs` — tambah handler `get_materi_file` untuk `GET /api/materi/:id/file` (baca file PDF penuh ke memory lalu kirim sebagai response `application/pdf` — cukup aman karena upload sudah dibatasi 50MB).
   - `backend/src/main.rs` — tambah routing untuk file PDF, progress, dan bookmark; tambah `delete` ke import `axum::routing`.

3. **File diubah (Android):**
   - `android/.../network/ApiModels.kt` — tambah `ProgressBaca`, `UpdateProgressRequest`, `Bookmark`, `CreateBookmarkRequest`.
   - `android/.../network/ApiService.kt` — tambah `downloadMateriFile` (dengan `@Streaming`), `getProgress`, `updateProgress`, `listBookmarks`, `createBookmark`, `deleteBookmark`.
   - `android/.../network/ApiClient.kt` — `readTimeout` dinaikkan dari 30 ke 120 detik (download PDF besar butuh waktu lebih lama dari request JSON biasa).
   - `android/.../ui/DaftarIsiScreen.kt` — tombol "📖 Baca" (buka dari progress terakhir) jadi `FloatingActionButton`; tiap baris bab dapat ikon ▶ untuk buka PDF langsung dari halaman awal bab itu.
   - `android/.../MainActivity.kt` — tambah `AppScreen.PdfViewer(materi, startHalaman: Int?)`; `startHalaman = null` artinya "lanjutkan dari progress terakhir", angka eksplisit artinya "lompat ke halaman itu" (dipakai tombol ▶ per bab).
   - `android/app/build.gradle.kts` — tambah `-opt-in=androidx.compose.foundation.ExperimentalFoundationApi` di `freeCompilerArgs` (dibutuhkan untuk `HorizontalPager`/`rememberPagerState`); versionCode → 3, versionName → `0.3.0-task5`.

4. **Tidak ada migrasi SQL baru** — tabel `progress_baca` & `bookmarks` sudah ada dari `migrations/0001_init.sql` Task 1, langsung dipakai. **Tidak ada dependency Gradle baru** — PDF Viewer sengaja pakai `android.graphics.pdf.PdfRenderer` bawaan Android (API 21+, minSdk project 26), bukan library pihak ketiga (AndroidPdfViewer/PDF.js yang disebut di `Konsep-program-learn.md`), supaya tidak perlu tambah repository JitPack di device dev CLI-only.

**Keputusan desain penting (asumsi, dicatat supaya tidak lupa):**
- **PdfRenderer bawaan Android, bukan library pihak ketiga.** Konsekuensinya: swipe halaman, zoom, dan toggle toolbar semua diimplementasi manual dengan Compose (`HorizontalPager` + gesture custom), bukan otomatis dari library. Trade-off ini didiskusikan di awal task dan disetujui.
- **File PDF didownload sekali & di-cache lokal** di `cacheDir/pdf_cache/<materiId>.pdf`, bukan streaming langsung dari network setiap render halaman. Kalau file materi di server pernah diganti (belum ada fitur ganti file di Task 2/3), cache lokal HP tidak otomatis ter-update — perlu clear cache app secara manual kalau ini terjadi nanti.
- **`GET /api/materi/:id/file` membaca file penuh ke memory** (`tokio::fs::read`), bukan streaming chunk-by-chunk. Cukup aman untuk sekarang karena upload dibatasi `MAX_UPLOAD_SIZE` 50MB, tapi kalau nanti ada kebutuhan materi jauh lebih besar, pertimbangkan ganti ke `tokio_util::io::ReaderStream`.
- **`startHalaman: Int?` sebagai sinyal niat**, bukan sekadar nomor halaman: `null` = "tombol Baca, lanjutkan dari progress terakhir" (fetch `GET .../progress` sebelum buka pager), angka eksplisit = "tombol ▶ per bab, langsung ke halaman itu" (tidak fetch progress). Penting diingat kalau nanti ada entry point baru ke PdfViewerScreen — harus eksplisit pilih salah satu dari 2 sinyal ini, jangan asumsikan default 1.
- **Progress & bookmark schema tidak berubah dari Task 1** — endpoint baru murni CRUD di atas tabel yang sudah ada, tidak ada migrasi baru.

**Bug yang ditemukan & diperbaiki (2 ronde, semua dikonfirmasi user):**

*Ronde 1 — gagal compile (`./gradlew assembleDebug`):*
1. `This foundation API is experimental and is likely to change or be removed in the future.` di banyak baris `PdfViewerScreen.kt` (pemakaian `HorizontalPager`/`rememberPagerState`) → **Penyebab:** sama persis dengan kasus `TopAppBar` di Task 3 — API foundation ini masih ditandai `@ExperimentalFoundationApi`, tanpa opt-in Kotlin compiler menjadikannya compile error. → **Fix:** tambah `-opt-in=androidx.compose.foundation.ExperimentalFoundationApi` di `freeCompilerArgs` pada `app/build.gradle.kts` (pola yang sama seperti fix Material3 di Task 3).

*Ronde 2 — build sukses, tapi 4 bug perilaku ditemukan saat testing manual di HP:*
1. **Tombol "📖 Baca" tidak kelihatan/ke-klik** (di ujung kiri layar) → **Penyebab:** toolbar Daftar Isi sudah punya 3 tombol (Import JSON, Tambah Bab, Export) dalam satu `Row` tanpa scroll/wrap, tombol ke-4 overflow keluar layar. → **Fix:** tombol "Baca" dipindah jadi `ExtendedFloatingActionButton` terpisah di `Scaffold`, selalu terlihat di pojok kanan bawah terlepas dari lebar layar.
2. **Swipe kiri/kanan tidak berfungsi sama sekali** → **Penyebab:** `detectTransformGestures` bawaan Compose langsung meng-*consume* setiap gerakan 1 jari sebagai "pan" sejak awal gesture, sehingga `HorizontalPager` di ancestor tidak pernah kebagian gesture drag untuk memicu perpindahan halaman. → **Fix:** ganti dengan gesture detector custom pakai `awaitEachGesture` + `calculateZoom()`/`calculatePan()` manual, yang hanya meng-*consume* event kalau (a) ada 2 jari atau lebih (pinch), atau (b) gambar sedang dalam kondisi zoom (`scale > 1f`). Kalau 1 jari & belum zoom, event dibiarkan tidak ter-consume supaya diteruskan ke `HorizontalPager` sebagai swipe normal.
3. **Klik item di daftar bookmark tidak melakukan apa-apa** (cuma bisa hapus) → **Fix:** tambah state `pendingJumpPage` yang di-set saat item bookmark di-tap, dikonsumsi oleh `LaunchedEffect` di dalam `PdfPagerContent` yang memanggil `pagerState.scrollToPage(...)`, lalu direset lagi ke `null`.
4. **Tidak resume dari halaman terakhir dibaca** setelah app ditutup-buka lagi → **Fix:** ubah tipe `startHalaman` dari `Int` jadi `Int?` di sepanjang alur (`DaftarIsiScreen` → `MainActivity` → `PdfViewerScreen`). Tombol "Baca" (FAB) sekarang kirim `null`, yang oleh `PdfViewerScreen` diartikan sebagai "fetch `GET /api/materi/:id/progress` dulu, lalu buka di `halaman_terakhir`nya (atau halaman 1 kalau belum pernah baca / gagal fetch)". Tombol ▶ per bab tetap kirim angka eksplisit (halaman awal bab itu), tidak terpengaruh perubahan ini.

**Kriteria berhasil (dikonfirmasi user, build backend & Android + testing manual di HP, SEMUA SUKSES setelah 2 ronde perbaikan):**
- [x] `cargo build` & `cargo run` sukses tanpa error
- [x] Endpoint `GET /api/materi/:id/file`, `GET`/`PUT /api/materi/:id/progress`, `GET`/`POST /api/materi/:materi_id/bookmarks`, `DELETE /api/bookmarks/:id` semua berfungsi (dicek via curl)
- [x] `./gradlew assembleDebug` & `installDebug` sukses tanpa error
- [x] Tap tombol "📖 Baca" (FAB) di Daftar Isi → PDF Viewer terbuka, tombol terlihat jelas di semua ukuran layar
- [x] Tap ikon ▶ di salah satu bab → PDF Viewer terbuka langsung ke halaman awal bab itu
- [x] Swipe kiri/kanan → halaman PDF berpindah dengan lancar
- [x] Pinch zoom in/out & double-tap zoom berfungsi; tap tengah layar toggle toolbar
- [x] Tambah bookmark berhasil, ikon bookmark di toolbar berubah warna saat berada di halaman yang di-bookmark
- [x] Tap item di daftar bookmark → PDF lompat ke halaman itu; tombol hapus bookmark berfungsi terpisah
- [x] Tombol "📝 Kosakata" menampilkan Toast placeholder sesuai rencana (fungsional penuh di Task 6)
- [x] Tutup app, buka lagi, masuk ke materi yang sama, tap "📖 Baca" → langsung ke halaman terakhir yang dibaca sebelumnya (bukan selalu halaman 1)

**Hasil:** ✅ TASK 5 SELESAI TOTAL (dikonfirmasi user — build backend & Android sukses, seluruh fungsi PDF Viewer/progress/bookmark diuji manual di HP fisik, semua lolos setelah 2 ronde perbaikan: 1x fix compile error opt-in, 1x fix 4 bug perilaku)

---

## Next Step

Kandidat task berikutnya (urutan disarankan, tapi bisa didiskusikan ulang):

1. **Task 6 — Kamus & Kosakata (Backend + Android)** ⬅️ **BERIKUTNYA**
   - Backend: endpoint kamus (`GET /api/kamus?q=...`, `GET /api/kamus/:id`, `POST /api/kamus`) dan kosakata konteks (`GET`/`POST`/`PUT`/`DELETE /api/kosakata`, termasuk endpoint per halaman & per bab yang sudah didesain di `Konsep-program-learn.md` bagian 5.3) — tabel `kamus`, `kamus_terjemahan`, `kosakata_konteks` sudah ada dari migrasi Task 1, tinggal dicek lagi skemanya sebelum mulai.
   - Android: modal Kosakata per halaman (list kosakata + tombol tambah), modal Tambah Kosakata dengan sugesti dari kamus & deduplikasi (sesuai mockup bagian 4 & 5), halaman Kamus + Detail Kata (mockup bagian 6 & 7).
   - Tombol "📝 Kosakata" yang sekarang masih placeholder Toast di `PdfViewerScreen.kt` akan diganti jadi navigasi ke modal Kosakata halaman aktif.
   - Perlu didiskusikan di awal task: bagaimana modal Kosakata (yang menurut mockup adalah overlay di atas PDF Viewer) diimplementasikan — apakah sebagai `ModalBottomSheet`/`Dialog` di atas `PdfViewerScreen`, atau layar terpisah yang menutupi PDF sementara.

**PENTING:** Sebelum lanjut ke Task 6, selalu tanyakan ke user file-file project Android & backend terakhir untuk di-upload (terutama file-file yang baru dibuat/diubah di Task 5: `progress.rs`, `bookmarks.rs`, `materi.rs`, `models.rs`, `mod.rs`, `main.rs`, `PdfViewerScreen.kt`, `DaftarIsiScreen.kt`, `MainActivity.kt`, `ApiModels.kt`, `ApiService.kt`, `ApiClient.kt`, `build.gradle.kts`), jangan berasumsi dari `progress.md` saja bahwa kode di atas 100% sama dengan yang ada di device/repo user — apalagi setelah 2 ronde perbaikan bug manual di sesi ini.

Juga perlu dicek: skema tabel `kamus`, `kamus_terjemahan`, `kosakata_konteks` dari `migrations/0001_init.sql` Task 1 — pastikan field-fieldnya (terutama `tipe_kata`, `reading`, `contoh_kalimat`, `folder_kustom`) masih sesuai kebutuhan sebelum mulai desain endpoint Task 6.

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
│       ├── main.rs            # + routing file/progress/bookmark (Task 5)
│       ├── state.rs
│       ├── error.rs
│       ├── models.rs          # + struct ProgressBaca, Bookmark (Task 5)
│       ├── storage.rs
│       └── handlers/
│           ├── mod.rs         # + mod progress, mod bookmarks (Task 5)
│           ├── folders.rs
│           ├── materi.rs      # + get_materi_file (Task 5)
│           ├── daftar_isi.rs
│           ├── progress.rs    # baru (Task 5)
│           └── bookmarks.rs   # baru (Task 5)
├── android/                  # Kotlin + Jetpack Compose
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts   # + opt-in ExperimentalFoundationApi, versionCode 3 (Task 5)
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/belajarbahasa/app/
│               ├── MainActivity.kt        # + AppScreen.PdfViewer (Task 5)
│               ├── BackendPrefs.kt        # (Task 3)
│               ├── network/
│               │   ├── ApiClient.kt       # + readTimeout 120s (Task 5)
│               │   ├── ApiModels.kt       # + ProgressBaca, Bookmark, dst (Task 5)
│               │   └── ApiService.kt      # + endpoint file/progress/bookmark (Task 5)
│               ├── ui/
│               │   ├── HomeScreen.kt      # (Task 4)
│               │   ├── DaftarIsiScreen.kt # + FAB Baca, ikon ▶ per bab (Task 5)
│               │   ├── PdfViewerScreen.kt # baru (Task 5)
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
