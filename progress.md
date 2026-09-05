# Progress - Aplikasi Belajar Bahasa dari Ebook PDF

> File ini adalah riwayat pengerjaan. Selalu upload file ini di awal chat baru
> supaya Claude tahu progress terakhir dan next step yang harus dikerjakan.

---

## Status Terkini

**Task aktif:** Task 3 - Halaman Home / File Manager (Android)
**Status:** ✅ Task 2 (CRUD Folder & Materi Backend) CONFIRMED berhasil oleh user — semua endpoint folder & materi tested via curl, upload PDF + hitung halaman otomatis jalan, delete file fisik dari disk terverifikasi bersih.

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

## Next Step

Kandidat task berikutnya (urutan disarankan, tapi bisa didiskusikan ulang):

1. **Task 3 — Halaman Home / File Manager (Android)** ⬅️ **BERIKUTNYA**
   - UI sesuai mockup `mockup-learn.html` bagian 1 (Home/File Manager)
   - Fetch data folder & materi dari `GET /api/folders` dan `GET /api/folders/:id/materi`
   - Tampilkan list folder + materi, jumlah item, tombol "Tambah Folder" & "Tambah Materi"
   - Tombol "Tambah Materi" perlu form: judul, pilih folder, upload PDF (pakai `POST /api/materi` multipart) — ini kemungkinan bagian paling kompleks di Android (file picker + multipart upload dari Kotlin)
2. **Task 4 — Daftar Isi (Backend + Android)**: endpoint import JSON daftar isi, manual CRUD bab/sub-bab, UI daftar isi bilingual.
3. **Task 5 — PDF Viewer (Android)**: integrasi library PDF viewer, navigasi halaman, progress tracking.
4. **Task 6 — Kamus & Kosakata (Backend + Android)**: search kamus, modal tambah kosakata, deduplikasi.

**PENTING:** Sebelum lanjut ke Task 3, selalu tanyakan ke user file-file project Android terakhir untuk di-upload (terutama `MainActivity.kt`, `build.gradle.kts` app-level, `AndroidManifest.xml`), jangan berasumsi dari `progress.md` saja bahwa kode di atas 100% sama dengan yang ada di device user.

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
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/belajarbahasa/app/MainActivity.kt
│           └── res/values/strings.xml
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
