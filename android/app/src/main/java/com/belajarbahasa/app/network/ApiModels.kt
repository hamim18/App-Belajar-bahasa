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
