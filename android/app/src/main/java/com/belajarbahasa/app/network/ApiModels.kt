package com.belajarbahasa.app.network

import kotlinx.serialization.Serializable

/**
 * Bentuk field & tipe di sini HARUS sinkron dengan backend:
 * - backend/src/models.rs -> struct FolderListItem
 * - backend/src/handlers/folders.rs -> query list_folders/get_folder
 *
 * jumlah_materi & jumlah_subfolder dikirim backend sebagai i64 (COUNT(*) Postgres),
 * di Kotlin dipetakan ke Long supaya tidak overflow & tidak ada mismatch tipe saat parsing.
 */
@Serializable
data class FolderListItem(
    val id: String,
    val user_id: String,
    val nama_folder: String,
    val bahasa_sumber: String? = null,
    val bahasa_target: String? = null,
    val parent_id: String? = null,
    val created_at: String,
    val updated_at: String,
    val jumlah_materi: Long,
    val jumlah_subfolder: Long
)

/**
 * Sinkron dengan backend/src/models.rs -> struct Materi.
 * total_halaman di Rust adalah i32 -> Int di Kotlin.
 */
@Serializable
data class Materi(
    val id: String,
    val folder_id: String,
    val judul: String,
    val bahasa_sumber: String,
    val bahasa_target: String,
    val file_pdf: String? = null,
    val total_halaman: Int,
    val cover_image: String? = null,
    val created_at: String,
    val updated_at: String
)

/**
 * Body request POST /api/folders.
 * Sinkron dengan backend/src/models.rs -> struct CreateFolderRequest.
 */
@Serializable
data class CreateFolderRequest(
    val nama_folder: String,
    val bahasa_sumber: String? = null,
    val bahasa_target: String? = null,
    val parent_id: String? = null
)

/** Response DELETE /api/folders/:id dan DELETE /api/materi/:id -> {"deleted": true} */
@Serializable
data class DeletedResponse(
    val deleted: Boolean
)

// ============================================================
// DAFTAR ISI (Task 4)
// ============================================================

/**
 * Bentuk node hasil GET daftar isi (nested tree, sudah bilingual).
 * Sinkron dengan backend/src/models.rs -> struct DaftarIsiNode.
 * judul_sumber/judul_target mengikuti bahasa_sumber/bahasa_target milik
 * materi induknya - jadi UI tidak perlu tahu kode bahasa mana yang dipakai.
 */
@Serializable
data class DaftarIsiNode(
    val id: String,
    val halaman_awal: Int,
    val halaman_akhir: Int,
    val level: Int,
    val urutan: Int,
    val parent_id: String? = null,
    val judul_sumber: String? = null,
    val judul_target: String? = null,
    val sub_bab: List<DaftarIsiNode> = emptyList()
)

/**
 * Satu item bab dalam bentuk import/export JSON (sesuai template di
 * Konsep-program-learn.md lampiran A). Dipakai baik untuk mengirim (import)
 * maupun menerima (export) - bentuknya sengaja dibuat identik.
 */
@Serializable
data class ImportBabItem(
    val judul_sumber: String,
    val judul_target: String,
    val halaman_awal: Int,
    val halaman_akhir: Int,
    val level: Int = 1,
    val urutan: Int = 0,
    val sub_bab: List<ImportBabItem> = emptyList()
)

/** Body POST /api/materi/{materiId}/daftar-isi/import */
@Serializable
data class ImportDaftarIsiRequest(
    val bahasa_sumber: String,
    val bahasa_target: String,
    val daftar_isi: List<ImportBabItem>
)

/** Response GET /api/materi/{materiId}/daftar-isi/export dan GET /api/daftar-isi/template */
@Serializable
data class ExportDaftarIsiResponse(
    val bahasa_sumber: String,
    val bahasa_target: String,
    val daftar_isi: List<ImportBabItem>
)

/** Body POST /api/materi/{materiId}/daftar-isi (tambah bab/sub-bab manual) */
@Serializable
data class CreateBabRequest(
    val judul_sumber: String,
    val judul_target: String,
    val halaman_awal: Int,
    val halaman_akhir: Int,
    val level: Int = 1,
    val parent_id: String? = null,
    val urutan: Int = 0
)

/** Body PUT /api/daftar-isi/{id}. Field null tidak diubah di backend (COALESCE). */
@Serializable
data class UpdateBabRequest(
    val judul_sumber: String? = null,
    val judul_target: String? = null,
    val halaman_awal: Int? = null,
    val halaman_akhir: Int? = null,
    val level: Int? = null,
    val urutan: Int? = null,
    val parent_id: String? = null
)

// ============================================================
// PROGRESS BACA & BOOKMARK (Task 5)
// ============================================================

/**
 * Sinkron dengan backend/src/models.rs -> struct ProgressBaca.
 * progress_persen dihitung di backend (bukan Android) dari total_halaman
 * materi, supaya rumusnya konsisten di satu tempat saja.
 */
@Serializable
data class ProgressBaca(
    val materi_id: String,
    val halaman_terakhir: Int,
    val progress_persen: Int,
    val last_read_at: String
)

/** Body PUT /api/materi/{id}/progress */
@Serializable
data class UpdateProgressRequest(
    val halaman_terakhir: Int
)

/** Sinkron dengan backend/src/models.rs -> struct Bookmark. */
@Serializable
data class Bookmark(
    val id: String,
    val materi_id: String,
    val halaman: Int,
    val catatan: String? = null,
    val created_at: String
)

/** Body POST /api/materi/{materiId}/bookmarks */
@Serializable
data class CreateBookmarkRequest(
    val halaman: Int,
    val catatan: String? = null
)

// ============================================================
// KAMUS & KOSAKATA (Task 6)
// ============================================================

/**
 * Satu baris hasil pencarian kamus (GET /api/kamus).
 * Sinkron dengan backend/src/models.rs -> struct KamusListItem.
 * bahasa_target ikut dikirim backend (walau berasal dari query param) supaya
 * Android tidak perlu tahu bahasa default user - tinggal baca dari response.
 */
@Serializable
data class KamusListItem(
    val id: String,
    val kata_asli: String,
    val bahasa_sumber: String,
    val is_custom: Boolean,
    val terjemahan: String? = null,
    val reading: String? = null,
    val tipe_kata: String? = null,
    val contoh_kalimat: String? = null,
    val bahasa_target: String,
    val jumlah_materi: Long,
    val jumlah_muncul: Long
)

/** Response GET /api/kamus */
@Serializable
data class KamusSearchResponse(
    val items: List<KamusListItem>,
    val total: Long
)

/** Satu baris kemunculan kata, dikelompokkan per (materi, bab). */
@Serializable
data class KemunculanMateri(
    val materi_id: String,
    val judul_materi: String,
    val bab_id: String? = null,
    val judul_bab: String? = null,
    val halaman_list: List<Int>
)

/** Response GET /api/kamus/{id} dan POST/PUT ke endpoint kamus. */
@Serializable
data class KamusDetail(
    val id: String,
    val kata_asli: String,
    val bahasa_sumber: String,
    val is_custom: Boolean,
    val bahasa_target: String,
    val terjemahan: String? = null,
    val reading: String? = null,
    val tipe_kata: String? = null,
    val contoh_kalimat: String? = null,
    val kemunculan: List<KemunculanMateri>
)

/** Body POST /api/kamus - tambah kata custom baru / terjemahan baru. */
@Serializable
data class CreateKamusRequest(
    val kata_asli: String,
    val bahasa_sumber: String,
    val terjemahan: String,
    val bahasa_target: String,
    val reading: String? = null,
    val tipe_kata: String? = null,
    val contoh_kalimat: String? = null
)

/** Body PUT /api/kamus/{id} - upsert terjemahan untuk kata_id di path. */
@Serializable
data class UpdateKamusTerjemahanRequest(
    val bahasa_target: String,
    val terjemahan: String? = null,
    val reading: String? = null,
    val tipe_kata: String? = null,
    val contoh_kalimat: String? = null
)

/**
 * Satu baris kosakata_konteks + info kamus yang sudah di-join.
 * Sinkron dengan backend/src/models.rs -> struct KosakataItem.
 * halaman_terkait = semua halaman (di materi yang sama) tempat kata ini
 * pernah ditambahkan - dipakai untuk badge "📄 3, 5, 12" di mockup.
 */
@Serializable
data class KosakataItem(
    val id: String,
    val kata_id: String,
    val kata_asli: String,
    val reading: String? = null,
    val terjemahan: String? = null,
    val tipe_kata: String? = null,
    val catatan_pribadi: String? = null,
    val folder_kustom: String? = null,
    val halaman: Int,
    val bab_id: String? = null,
    val halaman_terkait: List<Int> = emptyList()
)

/** Baris agregat kosakata per bab (dedup per kata). */
@Serializable
data class KosakataBabItem(
    val kata_id: String,
    val kata_asli: String,
    val reading: String? = null,
    val terjemahan: String? = null,
    val tipe_kata: String? = null,
    val halaman_list: List<Int>
)

/**
 * Body POST /api/kosakata. terjemahan/reading/tipe_kata/contoh_kalimat HANYA
 * dipakai backend kalau kata_asli belum ada di kamus untuk bahasa_sumber
 * materi ini (kata baru) - kalau kata sudah ada, field itu diabaikan backend.
 */
@Serializable
data class CreateKosakataRequest(
    val kata_asli: String,
    val materi_id: String,
    val bab_id: String? = null,
    val halaman: Int,
    val catatan_pribadi: String? = null,
    val folder_kustom: String? = null,
    val terjemahan: String? = null,
    val reading: String? = null,
    val tipe_kata: String? = null,
    val contoh_kalimat: String? = null
)

/** Body PUT /api/kosakata/{id} - hanya catatan_pribadi & folder_kustom. */
@Serializable
data class UpdateKosakataRequest(
    val catatan_pribadi: String? = null,
    val folder_kustom: String? = null
)
