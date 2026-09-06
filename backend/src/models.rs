use chrono::NaiveDateTime;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

// ============================================================
// FOLDER
// ============================================================

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct Folder {
    pub id: Uuid,
    pub user_id: Uuid,
    pub nama_folder: String,
    pub bahasa_sumber: Option<String>,
    pub bahasa_target: Option<String>,
    pub parent_id: Option<Uuid>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

/// Dipakai untuk list folder (halaman utama) - sudah termasuk jumlah isi
/// supaya UI bisa langsung tampilkan "3 materi" seperti di mockup.
#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct FolderListItem {
    pub id: Uuid,
    pub user_id: Uuid,
    pub nama_folder: String,
    pub bahasa_sumber: Option<String>,
    pub bahasa_target: Option<String>,
    pub parent_id: Option<Uuid>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub jumlah_materi: i64,
    pub jumlah_subfolder: i64,
}

#[derive(Debug, Deserialize)]
pub struct CreateFolderRequest {
    pub nama_folder: String,
    pub bahasa_sumber: Option<String>,
    pub bahasa_target: Option<String>,
    pub parent_id: Option<Uuid>,
}

/// Semua field opsional. Field yang tidak dikirim (None) tidak akan diubah.
/// Catatan: karena pakai COALESCE, untuk saat ini parent_id tidak bisa
/// di-set eksplisit jadi NULL (pindah ke root) lewat update biasa -
/// kalau perlu fitur itu nanti bisa ditambah endpoint khusus.
#[derive(Debug, Deserialize)]
pub struct UpdateFolderRequest {
    pub nama_folder: Option<String>,
    pub bahasa_sumber: Option<String>,
    pub bahasa_target: Option<String>,
    pub parent_id: Option<Uuid>,
}

// ============================================================
// MATERI
// ============================================================

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct Materi {
    pub id: Uuid,
    pub folder_id: Uuid,
    pub judul: String,
    pub bahasa_sumber: String,
    pub bahasa_target: String,
    pub file_pdf: Option<String>,
    pub total_halaman: i32,
    pub cover_image: Option<String>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

#[derive(Debug, Deserialize)]
pub struct UpdateMateriRequest {
    pub judul: Option<String>,
    pub folder_id: Option<Uuid>,
    pub bahasa_sumber: Option<String>,
    pub bahasa_target: Option<String>,
}

// ============================================================
// DAFTAR ISI
// ============================================================

fn default_level() -> i32 {
    1
}

/// Bentuk node hasil GET daftar isi (nested tree, sudah bilingual).
/// judul_sumber/judul_target selalu mengikuti bahasa_sumber/bahasa_target
/// milik materi induknya (bukan bahasa bebas), supaya UI Android tidak perlu
/// tahu kode bahasa mana yang dipakai - tinggal pakai judul_sumber/judul_target.
#[derive(Debug, Serialize)]
pub struct DaftarIsiNode {
    pub id: Uuid,
    pub halaman_awal: i32,
    pub halaman_akhir: i32,
    pub level: i32,
    pub urutan: i32,
    pub parent_id: Option<Uuid>,
    pub judul_sumber: Option<String>,
    pub judul_target: Option<String>,
    pub sub_bab: Vec<DaftarIsiNode>,
}

/// Baris flat hasil query (sebelum disusun jadi tree oleh kode Rust).
#[derive(Debug, sqlx::FromRow)]
pub struct DaftarIsiFlatRow {
    pub id: Uuid,
    pub halaman_awal: i32,
    pub halaman_akhir: i32,
    pub level: i32,
    pub urutan: i32,
    pub parent_id: Option<Uuid>,
    pub judul_sumber: Option<String>,
    pub judul_target: Option<String>,
}

/// Body POST /api/materi/:materi_id/daftar-isi (tambah bab manual).
#[derive(Debug, Deserialize)]
pub struct CreateBabRequest {
    pub judul_sumber: String,
    pub judul_target: String,
    pub halaman_awal: i32,
    pub halaman_akhir: i32,
    #[serde(default = "default_level")]
    pub level: i32,
    pub parent_id: Option<Uuid>,
    #[serde(default)]
    pub urutan: i32,
}

/// Body PUT /api/daftar-isi/:id. Field yang tidak dikirim (None) tidak diubah.
/// Catatan: sama seperti UpdateFolderRequest, parent_id tidak bisa di-set
/// eksplisit jadi NULL (pindah ke level root) lewat endpoint ini karena
/// pakai COALESCE - kalau nanti dibutuhkan, perlu endpoint khusus.
#[derive(Debug, Deserialize)]
pub struct UpdateBabRequest {
    pub judul_sumber: Option<String>,
    pub judul_target: Option<String>,
    pub halaman_awal: Option<i32>,
    pub halaman_akhir: Option<i32>,
    pub level: Option<i32>,
    pub urutan: Option<i32>,
    pub parent_id: Option<Uuid>,
}

/// Body POST /api/materi/:materi_id/daftar-isi/import.
/// bahasa_sumber/bahasa_target di sini WAJIB sama dengan bahasa_sumber/bahasa_target
/// milik materi (divalidasi di handler) - supaya judul yang tersimpan konsisten
/// dengan bahasa yang dipakai endpoint GET & export.
#[derive(Debug, Deserialize)]
pub struct ImportDaftarIsiRequest {
    pub bahasa_sumber: String,
    pub bahasa_target: String,
    pub daftar_isi: Vec<ImportBabItem>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct ImportBabItem {
    pub judul_sumber: String,
    pub judul_target: String,
    pub halaman_awal: i32,
    pub halaman_akhir: i32,
    #[serde(default = "default_level")]
    pub level: i32,
    #[serde(default)]
    pub urutan: i32,
    #[serde(default)]
    pub sub_bab: Vec<ImportBabItem>,
}

/// Bentuk response GET export - sengaja dibuat identik dengan bentuk
/// ImportDaftarIsiRequest supaya hasil export bisa langsung dipakai lagi
/// sebagai import (round-trip / backup-restore).
#[derive(Debug, Serialize)]
pub struct ExportDaftarIsiResponse {
    pub bahasa_sumber: String,
    pub bahasa_target: String,
    pub daftar_isi: Vec<ImportBabItem>,
}
