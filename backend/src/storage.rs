use std::path::{Path, PathBuf};
use uuid::Uuid;

pub const PDF_STORAGE_DIR: &str = "storage/pdf";

/// Pastikan folder storage ada sebelum server menerima upload.
pub async fn ensure_storage_dir() -> std::io::Result<()> {
    tokio::fs::create_dir_all(PDF_STORAGE_DIR).await
}

/// Generate nama file unik (UUID) supaya tidak ada bentrok nama file
/// dan supaya nama file asli user tidak jadi path di server.
pub fn new_pdf_filename() -> String {
    format!("{}.pdf", Uuid::new_v4())
}

pub fn pdf_path(filename: &str) -> PathBuf {
    Path::new(PDF_STORAGE_DIR).join(filename)
}

/// Hitung jumlah halaman PDF. Dijalankan lewat spawn_blocking di handler
/// karena lopdf bersifat sinkron/blocking.
pub fn count_pdf_pages(path: &Path) -> Result<i32, String> {
    let doc = lopdf::Document::load(path)
        .map_err(|e| format!("File PDF tidak valid atau rusak: {e}"))?;
    Ok(doc.get_pages().len() as i32)
}

/// Hapus file PDF dari disk. Tidak error kalau file memang sudah tidak ada.
pub async fn delete_pdf_file(filename: &str) {
    let path = pdf_path(filename);
    if let Err(e) = tokio::fs::remove_file(&path).await {
        if e.kind() != std::io::ErrorKind::NotFound {
            tracing::warn!("Gagal menghapus file {:?}: {}", path, e);
        }
    }
}
