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
