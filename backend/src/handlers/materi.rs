use axum::{
    extract::{Multipart, Path, State},
    Json,
};
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{Materi, UpdateMateriRequest},
    state::AppState,
    storage,
};

/// GET /api/folders/:folder_id/materi
pub async fn list_materi_by_folder(
    State(state): State<AppState>,
    Path(folder_id): Path<Uuid>,
) -> Result<Json<Vec<Materi>>, AppError> {
    // Pastikan folder ada & milik user (biar tidak bocor data folder orang lain)
    let folder_exists = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM folders WHERE id = $1 AND user_id = $2",
    )
    .bind(folder_id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?;

    if folder_exists.is_none() {
        return Err(AppError::NotFound("Folder tidak ditemukan".to_string()));
    }

    let rows = sqlx::query_as::<_, Materi>(
        r#"
        SELECT id, folder_id, judul, bahasa_sumber, bahasa_target,
               file_pdf, total_halaman, cover_image, created_at, updated_at
        FROM materi
        WHERE folder_id = $1
        ORDER BY judul ASC
        "#,
    )
    .bind(folder_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

/// GET /api/materi/:id
pub async fn get_materi(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<Materi>, AppError> {
    let row = sqlx::query_as::<_, Materi>(
        r#"
        SELECT m.id, m.folder_id, m.judul, m.bahasa_sumber, m.bahasa_target,
               m.file_pdf, m.total_halaman, m.cover_image, m.created_at, m.updated_at
        FROM materi m
        JOIN folders f ON f.id = m.folder_id
        WHERE m.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Materi tidak ditemukan".to_string()))?;

    Ok(Json(row))
}

/// POST /api/materi  (multipart/form-data)
/// Field yang diterima: judul, folder_id, bahasa_sumber (opsional),
/// bahasa_target (opsional), file (PDF binary, wajib).
/// Kalau bahasa_sumber/target tidak dikirim, dipakai default dari folder.
pub async fn create_materi(
    State(state): State<AppState>,
    mut multipart: Multipart,
) -> Result<Json<Materi>, AppError> {
    let mut judul: Option<String> = None;
    let mut folder_id: Option<Uuid> = None;
    let mut bahasa_sumber: Option<String> = None;
    let mut bahasa_target: Option<String> = None;
    let mut file_bytes: Option<Vec<u8>> = None;
    let mut original_filename: Option<String> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| AppError::BadRequest(format!("Form tidak valid: {e}")))?
    {
        let name = field.name().unwrap_or("").to_string();

        match name.as_str() {
            "judul" => {
                judul = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| AppError::BadRequest(format!("Field judul error: {e}")))?,
                )
            }
            "folder_id" => {
                let val = field
                    .text()
                    .await
                    .map_err(|e| AppError::BadRequest(format!("Field folder_id error: {e}")))?;
                folder_id = Some(
                    Uuid::parse_str(val.trim())
                        .map_err(|_| AppError::BadRequest("folder_id bukan UUID valid".to_string()))?,
                );
            }
            "bahasa_sumber" => {
                bahasa_sumber = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| AppError::BadRequest(format!("Field bahasa_sumber error: {e}")))?,
                )
            }
            "bahasa_target" => {
                bahasa_target = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| AppError::BadRequest(format!("Field bahasa_target error: {e}")))?,
                )
            }
            "file" => {
                original_filename = field.file_name().map(|s| s.to_string());
                let bytes = field
                    .bytes()
                    .await
                    .map_err(|e| AppError::BadRequest(format!("Gagal membaca file: {e}")))?;
                file_bytes = Some(bytes.to_vec());
            }
            _ => {
                // field tidak dikenal, abaikan saja
            }
        }
    }

    let judul = judul.ok_or_else(|| AppError::BadRequest("judul wajib diisi".to_string()))?;
    let folder_id = folder_id.ok_or_else(|| AppError::BadRequest("folder_id wajib diisi".to_string()))?;
    let file_bytes = file_bytes.ok_or_else(|| AppError::BadRequest("file PDF wajib diupload".to_string()))?;

    if judul.trim().is_empty() {
        return Err(AppError::BadRequest("judul tidak boleh kosong".to_string()));
    }

    if let Some(name) = &original_filename {
        if !name.to_lowercase().ends_with(".pdf") {
            return Err(AppError::BadRequest("File harus berformat .pdf".to_string()));
        }
    }

    // Ambil data folder untuk validasi kepemilikan + default bahasa
    let folder = sqlx::query_as::<_, (Option<String>, Option<String>)>(
        "SELECT bahasa_sumber, bahasa_target FROM folders WHERE id = $1 AND user_id = $2",
    )
    .bind(folder_id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::BadRequest("folder_id tidak ditemukan".to_string()))?;

    let bahasa_sumber = bahasa_sumber
        .or(folder.0)
        .ok_or_else(|| AppError::BadRequest("bahasa_sumber wajib diisi (folder tidak punya default)".to_string()))?;
    let bahasa_target = bahasa_target
        .or(folder.1)
        .ok_or_else(|| AppError::BadRequest("bahasa_target wajib diisi (folder tidak punya default)".to_string()))?;

    // Simpan file ke disk dengan nama unik
    let filename = storage::new_pdf_filename();
    let path = storage::pdf_path(&filename);
    tokio::fs::write(&path, &file_bytes).await?;

    // Hitung jumlah halaman (blocking, jalankan di thread terpisah)
    let count_path = path.clone();
    let total_halaman = match tokio::task::spawn_blocking(move || storage::count_pdf_pages(&count_path))
        .await
        .map_err(|e| AppError::Internal(format!("Task gagal: {e}")))?
    {
        Ok(n) => n,
        Err(msg) => {
            // File rusak/bukan PDF valid -> hapus file yang sudah kepalang tersimpan
            storage::delete_pdf_file(&filename).await;
            return Err(AppError::BadRequest(msg));
        }
    };

    let id = sqlx::query_scalar::<_, Uuid>(
        r#"
        INSERT INTO materi (folder_id, judul, bahasa_sumber, bahasa_target, file_pdf, total_halaman)
        VALUES ($1, $2, $3, $4, $5, $6)
        RETURNING id
        "#,
    )
    .bind(folder_id)
    .bind(judul.trim())
    .bind(bahasa_sumber)
    .bind(bahasa_target)
    .bind(&filename)
    .bind(total_halaman)
    .fetch_one(&state.db)
    .await?;

    get_materi(State(state), Path(id)).await
}

/// PUT /api/materi/:id
/// Catatan: endpoint ini hanya update metadata (judul, folder, bahasa).
/// Mengganti file PDF belum didukung di task ini - kalau perlu ganti file,
/// hapus materi lalu upload ulang.
pub async fn update_materi(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(body): Json<UpdateMateriRequest>,
) -> Result<Json<Materi>, AppError> {
    let updated = sqlx::query_scalar::<_, Uuid>(
        r#"
        UPDATE materi m SET
            judul = COALESCE($1, m.judul),
            folder_id = COALESCE($2, m.folder_id),
            bahasa_sumber = COALESCE($3, m.bahasa_sumber),
            bahasa_target = COALESCE($4, m.bahasa_target),
            updated_at = NOW()
        FROM folders f
        WHERE m.id = $5 AND m.folder_id = f.id AND f.user_id = $6
        RETURNING m.id
        "#,
    )
    .bind(body.judul)
    .bind(body.folder_id)
    .bind(body.bahasa_sumber)
    .bind(body.bahasa_target)
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Materi tidak ditemukan".to_string()))?;

    get_materi(State(state), Path(updated)).await
}

/// DELETE /api/materi/:id
pub async fn delete_materi(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, AppError> {
    let file_pdf = sqlx::query_scalar::<_, Option<String>>(
        r#"
        SELECT m.file_pdf
        FROM materi m
        JOIN folders f ON f.id = m.folder_id
        WHERE m.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Materi tidak ditemukan".to_string()))?;

    sqlx::query("DELETE FROM materi WHERE id = $1")
        .bind(id)
        .execute(&state.db)
        .await?;

    if let Some(filename) = file_pdf {
        storage::delete_pdf_file(&filename).await;
    }

    Ok(Json(serde_json::json!({ "deleted": true })))
}
