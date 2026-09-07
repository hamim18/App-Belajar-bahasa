# Progress - Aplikasi Belajar Bahasa dari Ebook PDF

> File ini adalah riwayat pengerjaan. Selalu upload file ini di awal chat baru
> supaya Claude tahu progress terakhir dan next step yang harus dikerjakan.

---

## Status Terkini

**Task aktif:** Belum ada - Task 6 (Kamus & Kosakata) sudah SELESAI dan dikonfirmasi. Menunggu keputusan task berikutnya (lihat "Next Step").
**Status:** ✅ Task 6 (Kamus & Kosakata - Backend + Android) CONFIRMED berhasil oleh user — `cargo build`, `./gradlew assembleDebug`, `installDebug` semua sukses, dan pengujian manual di HP fisik (tambah/edit/hapus kosakata lewat ModalBottomSheet, sugesti kamus otomatis, halaman Kamus dengan search & filter tipe kata, halaman Detail Kata dengan daftar kemunculan) semua lolos setelah 2 ronde perbaikan (1x bug compile Kotlin, 1x bug pencarian tidak mencakup furigana/terjemahan).

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
     - Tombol "📝 Kosakata" masih placeholder (Toast "Fitur Kosakata akan hadir di Task 6") — **sudah diganti fungsional penuh di Task 6.**

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

### Task 6 — Kamus & Kosakata (Backend + Android) (2026-09-07)

**Yang dikerjakan:**

1. **File baru ditambahkan (backend):**
   - `backend/src/handlers/kamus.rs`:
     - `GET /api/kamus?q=&bahasa_sumber=&bahasa_target=&tipe_kata=&limit=&offset=` — cari kata di kamus. `q` di-ILIKE ke **kata_asli (kanji), reading (furigana), DAN terjemahan sekaligus** (lihat catatan bugfix di bawah). `bahasa_sumber`/`bahasa_target` opsional — kalau tidak dikirim, backend pakai `bahasa_sumber_default`/`bahasa_target_default` milik user (tabel `users`). Response termasuk `total` (untuk pagination) dan `jumlah_materi`/`jumlah_muncul` per kata (dihitung dari `kosakata_konteks`).
     - `GET /api/kamus/:id?bahasa_target=` — detail satu kata + daftar `kemunculan` (dikelompokkan per materi+bab, tiap grup punya `halaman_list`).
     - `POST /api/kamus` — tambah kata custom baru, atau kalau `kata_asli`+`bahasa_sumber` sudah ada, upsert terjemahan untuk `bahasa_target` yang dikirim.
     - `PUT /api/kamus/:id` — upsert terjemahan (reading/tipe_kata/contoh_kalimat/terjemahan) untuk satu `bahasa_target`, tanpa mengubah `kata_asli`/`bahasa_sumber` (itu identitas baris kamus).
   - `backend/src/handlers/kosakata.rs`:
     - `GET /api/materi/:materi_id/halaman/:halaman/kosakata?bab_id=` — list kosakata di satu halaman (bab_id opsional; kalau tidak dikirim, ambil lintas bab).
     - `GET /api/materi/:materi_id/bab/:bab_id/kosakata` — list kosakata dedup per bab (gabungan semua halaman dalam bab itu, `halaman_list`).
     - `POST /api/kosakata` — tambah kata ke konteks (materi+bab+halaman). Kalau `kata_asli` belum ada di kamus untuk `bahasa_sumber` materi ini, otomatis dibuat sebagai kata custom (field `terjemahan` WAJIB diisi kalau kata baru). Kalau kombinasi (kata, materi, bab, halaman) sudah ada, `catatan_pribadi`/`folder_kustom` di-UPDATE, bukan duplikat.
     - `PUT /api/kosakata/:id` — update `catatan_pribadi`/`folder_kustom`.
     - `DELETE /api/kosakata/:id` — hapus dari konteks (tidak menghapus kata dari kamus master).
   - `android/.../ui/KosakataSheet.kt` — **ModalBottomSheet** (bukan Dialog fullscreen atau layar terpisah — keputusan disepakati di awal task) untuk kosakata per halaman:
     - List kosakata di halaman aktif + pencarian lokal (kanji/furigana/terjemahan — lihat bugfix di bawah)
     - Dialog Tambah Kosakata: sugesti otomatis dari `GET /api/kamus` saat mengetik (debounce 300ms), auto-isi terjemahan/reading/tipe kalau klik sugesti kata yang sudah ada
     - Dialog Edit (hanya `catatan_pribadi` — ubah terjemahan/reading dilakukan lewat Kamus Detail, karena itu perubahan global ke kata, bukan ke satu baris konteks)
     - Konfirmasi hapus
   - `android/.../ui/KamusScreen.kt` — halaman Kamus (search + filter tipe kata N/V/Adj/Adv, list dengan jumlah materi/muncul)
   - `android/.../ui/KamusDetailScreen.kt` — halaman Detail Kata (terjemahan, contoh kalimat, daftar "Muncul di Materi" per bab)

2. **File diubah (backend):**
   - `backend/src/models.rs` — tambah struct `KamusListItem`, `KamusSearchResponse`, `KamusDetail`, `KamusBaseRow`, `KemunculanRow`, `KemunculanMateri`, `CreateKamusRequest`, `UpdateKamusTerjemahanRequest`, `SearchKamusQuery`, `KamusDetailQuery`, `KosakataItem`, `KosakataBabItem`, `CreateKosakataRequest`, `UpdateKosakataRequest`, `KosakataHalamanQuery`
   - `backend/src/handlers/mod.rs` — tambah `pub mod kamus;` dan `pub mod kosakata;`
   - `backend/src/main.rs` — tambah routing kamus & kosakata (lihat daftar endpoint di atas); versi di `health_check` naik ke `0.4.0`

3. **File diubah (Android):**
   - `android/.../network/ApiModels.kt` — tambah semua data class yang sinkron dengan struct backend di atas
   - `android/.../network/ApiService.kt` — tambah endpoint Retrofit: `searchKamus`, `getKamusDetail`, `createKamus`, `updateKamusTerjemahan`, `listKosakataHalaman`, `listKosakataBab`, `createKosakata`, `updateKosakata`, `deleteKosakata`
   - `android/.../ui/HomeScreen.kt` — tambah ikon 📖 di app bar (`onOpenKamus`) untuk membuka `KamusScreen`
   - `android/.../ui/PdfViewerScreen.kt` — tombol "📝 Kosakata" (dulu Toast placeholder Task 5) sekarang membuka `KosakataSheet`; `bab_id` untuk kosakata di-resolve OTOMATIS dari `daftarIsiTree` (di-fetch sekali per materi) berdasarkan halaman yang sedang dibaca lewat helper `findBabForHalaman` (pilih rentang bab/sub-bab TERSEMPIT yang mengandung halaman itu; kalau tidak ada yang cocok, `bab_id = null` — didukung karena kolom nullable di DB)
   - `android/.../MainActivity.kt` — tambah `AppScreen.Kamus` dan `AppScreen.KamusDetail(kataId)`; `PdfViewerScreen` sekarang menerima `bahasaSumber`/`bahasaTarget` dari `materi`

4. **Tidak ada migrasi SQL baru** — tabel `kamus`, `kamus_terjemahan`, `kosakata_konteks` sudah ada dari `migrations/0001_init.sql` Task 1, langsung dipakai.

**Keputusan desain penting (asumsi, dicatat supaya tidak lupa):**
- **ModalBottomSheet untuk Kosakata** (bukan Dialog fullscreen/layar terpisah) — disepakati di awal task supaya konteks halaman PDF yang sedang dibaca tidak hilang, dan bisa di-swipe-dismiss.
- **Dedup kosakata pakai `bab_id IS NOT DISTINCT FROM $x`, BUKAN `ON CONFLICT`** — karena `kosakata_konteks.bab_id` nullable dan UNIQUE constraint di Postgres tidak menganggap dua `NULL` sebagai duplikat. Kalau ini tidak diperhatikan, kata dengan `bab_id = NULL` bisa ke-insert dobel di halaman yang sama. Dedup dicek manual via `SELECT` sebelum `INSERT`/`UPDATE`.
- **`halaman_terkait` di-scope SATU MATERI (lintas bab), bukan per-bab** — berbeda dari sample query Appendix C di `Konsep-program-learn.md` yang men-scope ke `(materi_id, bab_id)` yang sama persis. Diputuskan begini karena itu yang ditunjukkan `mockup-learn.html` (kata "食べる" muncul di p.5 Bab 1, p.12 Bab 2, p.30 Bab 5 — lintas bab).
- **`bab_id` untuk kosakata di-resolve otomatis di Android** dari Daftar Isi materi (bukan diminta manual dari user) — kalau halaman yang sedang dibaca tidak masuk rentang bab manapun (misal daftar isi belum lengkap), `bab_id` dikirim `null` dan kosakata tetap bisa ditambahkan tanpa bab.
- **Fitur ⭐ Favorit di mockup BELUM diimplementasikan** — tidak ada tabel/kolom "favorit" di skema DB. Kalau nanti dibutuhkan, perlu tabel baru dulu (misal `kamus_favorit` per user).
- **`tipe_kata` adalah teks bebas** (konvensi: N=Nomina, V=Verba, Adj=Adjektiva, Adv=Adverbia) — tidak divalidasi di backend sebagai enum, supaya fleksibel untuk bahasa lain di masa depan (fondasi multibahasa).
- **KamusScreen & KamusDetailScreen tidak meminta `bahasaSumber`/`bahasaTarget` dari pemanggil (MainActivity)** — sengaja dikirim `null` ke backend, yang otomatis pakai bahasa default user. Label bahasa di UI diisi belakangan dari hasil response pertama (`item.bahasa_sumber`/`item.bahasa_target`).

**Bug yang ditemukan & diperbaiki (2 ronde, semua dikonfirmasi user):**

*Ronde 1 — gagal compile (`./gradlew assembleDebug`):*
1. `Cannot access 'weight': it is internal in 'androidx.compose.foundation.layout'` di `KamusScreen.kt` dan `KamusDetailScreen.kt` → **Penyebab:** `weight()` itu **member function** dari interface `ColumnScope`/`RowScope` (dideklarasikan sebagai `fun Modifier.weight(...)` DI DALAM interface tsb), BUKAN top-level function di package `androidx.compose.foundation.layout`. Menambahkan `import androidx.compose.foundation.layout.weight` secara eksplisit malah nyantol ke simbol internal lain yang kebetulan sama nama di package itu, bukan ke member function yang dimaksud. → **Fix:** hapus baris `import ...weight` di kedua file — Kotlin otomatis resolve `weight()` dari implicit receiver `ColumnScope` begitu dipanggil di dalam lambda `Column { ... }`, tanpa perlu import apapun.

*Ronde 2 — build & install sukses, tapi 1 bug fungsional ditemukan saat testing manual:*
1. **Pencarian tidak mencakup furigana (reading) & hanya kanji di halaman Kamus** → User melaporkan: pencarian di modal Kosakata per halaman hanya bisa cari kanji+terjemahan (belum furigana), dan pencarian di halaman Kamus hanya bisa cari kanji saja (belum furigana maupun terjemahan). → **Fix:**
   - Backend: query SQL `search_kamus` (baik query list maupun query `COUNT` untuk `total`) diubah dari `k.kata_asli ILIKE $3` saja menjadi `k.kata_asli ILIKE $3 OR kt.reading ILIKE $3 OR kt.terjemahan ILIKE $3` — otomatis memperbaiki DUA tempat sekaligus (halaman Kamus dan sugesti kata di modal Tambah Kosakata, karena keduanya memanggil endpoint `GET /api/kamus` yang sama).
   - Android: filter pencarian lokal di `KosakataSheet.kt` (untuk kosakata per halaman, yang datanya sudah di-fetch penuh dan difilter di client, bukan lewat query param) ditambah pengecekan `reading`, sebelumnya hanya `kata_asli` dan `terjemahan`.

**Kriteria berhasil (dikonfirmasi user, build backend & Android + testing manual di HP, SEMUA SUKSES setelah 2 ronde perbaikan):**
- [x] `cargo build` & `cargo run` sukses tanpa error
- [x] `./gradlew assembleDebug` & `installDebug` sukses tanpa error (setelah fix bug `weight` internal)
- [x] Tap "📝 Kosakata" di PDF Viewer → ModalBottomSheet muncul dari bawah, tidak lagi Toast placeholder
- [x] Tambah kosakata baru (kata belum ada di kamus) dengan terjemahan wajib diisi → berhasil, muncul di list
- [x] Ketik kata yang mirip dengan yang sudah ada di kamus → sugesti muncul otomatis → tap sugesti → form terisi otomatis (terjemahan/reading/tipe)
- [x] Edit catatan pribadi salah satu kosakata → tersimpan
- [x] Hapus salah satu kosakata → hilang dari list
- [x] Tap ikon 📖 di Home → halaman Kamus terbuka, kata yang baru ditambah muncul di list
- [x] Filter tipe kata (N/V/Adj/Adv) di halaman Kamus berfungsi
- [x] Tap salah satu kata di Kamus → halaman Detail Kata terbuka, menampilkan terjemahan + daftar "Muncul di Materi" per bab
- [x] Pencarian pakai furigana di halaman Kamus → kata dengan reading itu ditemukan
- [x] Pencarian pakai terjemahan di halaman Kamus → kata itu ditemukan
- [x] Pencarian pakai furigana di modal Kosakata per halaman → kosakata yang cocok ditemukan
- [x] Pencarian pakai kanji tetap berfungsi seperti biasa di semua tempat di atas

**Hasil:** ✅ TASK 6 SELESAI TOTAL (dikonfirmasi user — build backend & Android sukses, seluruh fungsi Kamus & Kosakata diuji manual di HP fisik, semua lolos setelah 2 ronde perbaikan: 1x fix compile error `weight` internal, 1x fix pencarian supaya mencakup kanji+furigana+terjemahan di semua tempat)

---

## Next Step

Semua fitur inti MVP (V1.0) di `Konsep-program-learn.md` — upload & render PDF, daftar isi manual/import JSON, kamus, kosakata per halaman, navigasi PDF, progress tracking — sudah selesai diimplementasikan (Task 1-6). Belum ada keputusan task berikutnya; beberapa kandidat yang bisa didiskusikan dengan user:

1. **Halaman Pengaturan / Settings** (mockup bagian 8, ditandai V1.1) — ubah bahasa default, dark mode, font size, export/import data JSON, export ke Anki (.apkg). Beberapa dari ini (dark mode, font size) murni UI Android; export/import data & Anki butuh endpoint backend baru.
2. **Sistem Autentikasi (login/register) yang sesungguhnya** — saat ini masih pakai 1 "default user" otomatis (lihat catatan Task 2). Ini utang teknis yang disebutkan dari Task 2 dan belum pernah dikerjakan.
3. **Pencarian materi/folder di Home** — kolom cari di `HomeScreen.kt` saat ini (kalau ada) perlu dicek lagi apakah sudah benar-benar query ke backend atau baru filter lokal.
4. **Flashcard dengan Spaced Repetition** (mockup bagian 9, V1.2) — butuh skema DB baru (belum ada di `migrations/0001_init.sql`) untuk tracking interval SRS per kosakata per user.
5. **Statistik & Text-to-Speech** (mockup bagian 10 & 11, V1.2) — juga butuh desain skema/endpoint baru dari nol.

**PENTING:** Sebelum lanjut ke task berikutnya (apapun yang dipilih), selalu tanyakan ke user file-file project Android & backend terkini untuk diupload — terutama file yang baru diubah di Task 6 (`kamus.rs`, `kosakata.rs`, `models.rs`, `mod.rs`, `main.rs`, `KosakataSheet.kt`, `KamusScreen.kt`, `KamusDetailScreen.kt`, `HomeScreen.kt`, `PdfViewerScreen.kt`, `MainActivity.kt`, `ApiModels.kt`, `ApiService.kt`), jangan berasumsi dari `progress.md` saja bahwa kode di atas 100% sama dengan yang ada di device/repo user — apalagi setelah 2 ronde perbaikan bug di sesi ini (termasuk fix pencarian furigana yang menyentuh SQL di `kamus.rs`).

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
│       ├── main.rs            # + routing kamus/kosakata, versi 0.4.0 (Task 6)
│       ├── state.rs
│       ├── error.rs
│       ├── models.rs          # + struct Kamus*/Kosakata* (Task 6)
│       ├── storage.rs
│       └── handlers/
│           ├── mod.rs         # + mod kamus, mod kosakata (Task 6)
│           ├── folders.rs
│           ├── materi.rs
│           ├── daftar_isi.rs
│           ├── progress.rs
│           ├── bookmarks.rs
│           ├── kamus.rs       # baru (Task 6)
│           └── kosakata.rs    # baru (Task 6)
├── android/                  # Kotlin + Jetpack Compose
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── java/com/belajarbahasa/app/
│               ├── MainActivity.kt        # + AppScreen.Kamus/KamusDetail (Task 6)
│               ├── BackendPrefs.kt
│               ├── network/
│               │   ├── ApiClient.kt
│               │   ├── ApiModels.kt       # + KamusListItem, KosakataItem, dst (Task 6)
│               │   └── ApiService.kt      # + endpoint kamus/kosakata (Task 6)
│               ├── ui/
│               │   ├── HomeScreen.kt      # + ikon Kamus di app bar (Task 6)
│               │   ├── DaftarIsiScreen.kt
│               │   ├── PdfViewerScreen.kt # + buka KosakataSheet, resolve bab_id (Task 6)
│               │   ├── KosakataSheet.kt   # baru (Task 6)
│               │   ├── KamusScreen.kt     # baru (Task 6)
│               │   ├── KamusDetailScreen.kt # baru (Task 6)
│               │   └── theme/
│               │       └── Theme.kt
│               └── util/
│                   ├── DateUtils.kt
│                   └── FileUtils.kt
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
