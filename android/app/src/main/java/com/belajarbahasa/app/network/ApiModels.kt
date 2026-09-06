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
