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
/// sebagai import (round-trip backup-restore).
#[derive(Debug, Serialize)]
pub struct ExportDaftarIsiResponse {
    pub bahasa_sumber: String,
    pub bahasa_target: String,
    pub daftar_isi: Vec<ImportBabItem>,
}

// ============================================================
// PROGRESS BACA (Task 5)
// ============================================================

/// Satu baris progress baca untuk (materi, user). Kalau user belum pernah
/// baca materi ini, handler get_progress mengembalikan nilai default
/// (halaman 0, 0%) tanpa membuat row baru di DB - row baru baru dibuat
/// saat PUT pertama kali (lihat handlers/progress.rs).
#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct ProgressBaca {
    pub materi_id: Uuid,
    pub halaman_terakhir: i32,
    pub progress_persen: i32,
    pub last_read_at: NaiveDateTime,
}

/// Body PUT /api/materi/:id/progress.
/// progress_persen SENGAJA tidak diminta dari Android - dihitung otomatis
/// di backend dari total_halaman materi, supaya perhitungan konsisten
/// di satu tempat saja (backend), bukan didup di kode Android juga.
#[derive(Debug, Deserialize)]
pub struct UpdateProgressRequest {
    pub halaman_terakhir: i32,
}

// ============================================================
// BOOKMARK (Task 5)
// ============================================================

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct Bookmark {
    pub id: Uuid,
    pub materi_id: Uuid,
    pub halaman: i32,
    pub catatan: Option<String>,
    pub created_at: NaiveDateTime,
}

/// Body POST /api/materi/:materi_id/bookmarks.
#[derive(Debug, Deserialize)]
pub struct CreateBookmarkRequest {
    pub halaman: i32,
    pub catatan: Option<String>,
}

// ============================================================
// KAMUS & KOSAKATA (Task 6)
// ============================================================

/// Satu baris hasil pencarian kamus (GET /api/kamus). jumlah_materi/jumlah_muncul
/// dihitung dari kosakata_konteks - dipakai untuk chip "3 materi", "12 muncul"
/// di mockup-learn.html bagian 6.
/// bahasa_target disertakan di setiap baris (bukan cuma di response wrapper)
/// supaya Android tahu bahasa_target efektif yang dipakai (berguna kalau nanti
/// bahasa_target tidak dikirim dari client dan di-default oleh backend).
#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct KamusListItem {
    pub id: Uuid,
    pub kata_asli: String,
    pub bahasa_sumber: String,
    pub is_custom: bool,
    pub bahasa_target: String,
    pub terjemahan: Option<String>,
    pub reading: Option<String>,
    pub tipe_kata: Option<String>,
    pub contoh_kalimat: Option<String>,
    pub jumlah_materi: i64,
    pub jumlah_muncul: i64,
}

#[derive(Debug, Serialize)]
pub struct KamusSearchResponse {
    pub items: Vec<KamusListItem>,
    pub total: i64,
}

#[derive(Debug, Deserialize)]
pub struct SearchKamusQuery {
    pub q: Option<String>,
    pub bahasa_sumber: Option<String>,
    pub bahasa_target: Option<String>,
    pub tipe_kata: Option<String>,
    pub limit: Option<i64>,
    pub offset: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct KamusDetailQuery {
    pub bahasa_target: Option<String>,
}

/// Baris dasar kamus (tanpa daftar kemunculan) - dipakai internal oleh
/// handler get_kamus_detail sebelum digabung dengan KemunculanMateri.
#[derive(Debug, sqlx::FromRow)]
pub struct KamusBaseRow {
    pub id: Uuid,
    pub kata_asli: String,
    pub bahasa_sumber: String,
    pub is_custom: bool,
    pub terjemahan: Option<String>,
    pub reading: Option<String>,
    pub tipe_kata: Option<String>,
    pub contoh_kalimat: Option<String>,
}

/// Baris flat kemunculan kata (sebelum dikelompokkan per materi+bab oleh
/// kode Rust jadi KemunculanMateri).
#[derive(Debug, sqlx::FromRow)]
pub struct KemunculanRow {
    pub materi_id: Uuid,
    pub judul_materi: String,
    pub bab_id: Option<Uuid>,
    pub judul_bab: Option<String>,
    pub halaman: i32,
}

#[derive(Debug, Serialize)]
pub struct KemunculanMateri {
    pub materi_id: Uuid,
    pub judul_materi: String,
    pub bab_id: Option<Uuid>,
    pub judul_bab: Option<String>,
    pub halaman_list: Vec<i32>,
}

#[derive(Debug, Serialize)]
pub struct KamusDetail {
    pub id: Uuid,
    pub kata_asli: String,
    pub bahasa_sumber: String,
    pub is_custom: bool,
    pub bahasa_target: String,
    pub terjemahan: Option<String>,
    pub reading: Option<String>,
    pub tipe_kata: Option<String>,
    pub contoh_kalimat: Option<String>,
    pub kemunculan: Vec<KemunculanMateri>,
}

/// Body POST /api/kamus - tambah kata custom baru (atau tambah/timpa
/// terjemahan bahasa_target untuk kata yang kata_asli+bahasa_sumber-nya
/// sudah ada).
#[derive(Debug, Deserialize)]
pub struct CreateKamusRequest {
    pub kata_asli: String,
    pub bahasa_sumber: String,
    pub terjemahan: String,
    pub bahasa_target: String,
    pub reading: Option<String>,
    pub tipe_kata: Option<String>,
    pub contoh_kalimat: Option<String>,
}

/// Body PUT /api/kamus/:id - upsert baris kamus_terjemahan untuk satu
/// bahasa_target (dipakai fitur "✏️ Edit" di Kamus Detail).
#[derive(Debug, Deserialize)]
pub struct UpdateKamusTerjemahanRequest {
    pub bahasa_target: String,
    pub terjemahan: Option<String>,
    pub reading: Option<String>,
    pub tipe_kata: Option<String>,
    pub contoh_kalimat: Option<String>,
}

/// Satu baris kosakata dalam konteks materi (kosakata_konteks join kamus +
/// kamus_terjemahan). halaman_terkait = semua halaman LAIN di materi yang
/// sama tempat kata ini juga muncul (dipakai untuk info "📄 3, 5, 12, 30"
/// dan warning box "kata sudah ada di halaman lain" di mockup bagian 4 & 5).
///
/// CATATAN: berbeda dari sample query Appendix C di Konsep-program-learn.md
/// yang men-scope halaman_terkait ke (materi_id, bab_id) yang sama persis -
/// di sini di-scope ke seluruh materi (lintas bab), karena itu yang
/// ditunjukkan oleh mockup-learn.html (kata "食べる" muncul di p.5 Bab 1,
/// p.12 Bab 2, p.30 Bab 5 - lintas bab).
#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct KosakataItem {
    pub id: Uuid,
    pub kata_id: Uuid,
    pub kata_asli: String,
    pub reading: Option<String>,
    pub terjemahan: Option<String>,
    pub tipe_kata: Option<String>,
    pub catatan_pribadi: Option<String>,
    pub folder_kustom: Option<String>,
    pub halaman: i32,
    pub bab_id: Option<Uuid>,
    pub halaman_terkait: Vec<i32>,
}

/// Satu baris kosakata untuk tampilan "per bab" (dedup per kata, gabungan
/// semua halaman dalam bab itu) - dipakai GET .../bab/:bab_id/kosakata.
#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct KosakataBabItem {
    pub kata_id: Uuid,
    pub kata_asli: String,
    pub reading: Option<String>,
    pub terjemahan: Option<String>,
    pub tipe_kata: Option<String>,
    pub halaman_list: Vec<i32>,
}

#[derive(Debug, Deserialize)]
pub struct KosakataHalamanQuery {
    pub bab_id: Option<Uuid>,
}

/// Body POST /api/kosakata. terjemahan/reading/tipe_kata/contoh_kalimat
/// HANYA dipakai kalau kata_asli belum ada di kamus untuk bahasa_sumber
/// materi ini (kata baru) - kalau kata sudah ada, field-field itu dipakai
/// untuk melengkapi kamus_terjemahan HANYA kalau baris untuk bahasa_target
/// materi ini belum ada (tidak menimpa terjemahan yang sudah ada).
#[derive(Debug, Deserialize)]
pub struct CreateKosakataRequest {
    pub kata_asli: String,
    pub materi_id: Uuid,
    pub bab_id: Option<Uuid>,
    pub halaman: i32,
    pub catatan_pribadi: Option<String>,
    pub folder_kustom: Option<String>,
    pub terjemahan: Option<String>,
    pub reading: Option<String>,
    pub tipe_kata: Option<String>,
    pub contoh_kalimat: Option<String>,
}

/// Body PUT /api/kosakata/:id - hanya catatan_pribadi & folder_kustom yang
/// bisa diubah lewat sini (ganti kata/terjemahan berarti kosakata beda,
/// bukan update - hapus lalu tambah baru).
#[derive(Debug, Deserialize)]
pub struct UpdateKosakataRequest {
    pub catatan_pribadi: Option<String>,
    pub folder_kustom: Option<String>,
}
