use axum::{
    extract::{Path, Query, State},
    Json,
};
use serde::Deserialize;
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{CreateFolderRequest, FolderListItem, UpdateFolderRequest},
    state::AppState,
    storage,
};

#[derive(Debug, Deserialize)]
pub struct ListFolderQuery {
    /// Tidak diisi / null = ambil folder di root (top-level)
    pub parent_id: Option<Uuid>,
}

/// GET /api/folders?parent_id=<uuid>
/// Kalau parent_id tidak dikirim, ambil folder di root.
pub async fn list_folders(
    State(state): State<AppState>,
    Query(q): Query<ListFolderQuery>,
) -> Result<Json<Vec<FolderListItem>>, AppError> {
    let rows = sqlx::query_as::<_, FolderListItem>(
        r#"
        SELECT
            f.id, f.user_id, f.nama_folder, f.bahasa_sumber, f.bahasa_target,
            f.parent_id, f.created_at, f.updated_at,
            (SELECT COUNT(*) FROM materi m WHERE m.folder_id = f.id) AS jumlah_materi,
            (SELECT COUNT(*) FROM folders sf WHERE sf.parent_id = f.id) AS jumlah_subfolder
        FROM folders f
        WHERE f.user_id = $1
          AND f.parent_id IS NOT DISTINCT FROM $2
        ORDER BY f.nama_folder ASC
        "#,
    )
    .bind(state.default_user_id)
    .bind(q.parent_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

/// GET /api/folders/:id
pub async fn get_folder(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<FolderListItem>, AppError> {
    let row = sqlx::query_as::<_, FolderListItem>(
        r#"
        SELECT
            f.id, f.user_id, f.nama_folder, f.bahasa_sumber, f.bahasa_target,
            f.parent_id, f.created_at, f.updated_at,
            (SELECT COUNT(*) FROM materi m WHERE m.folder_id = f.id) AS jumlah_materi,
            (SELECT COUNT(*) FROM folders sf WHERE sf.parent_id = f.id) AS jumlah_subfolder
        FROM folders f
        WHERE f.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Folder tidak ditemukan".to_string()))?;

    Ok(Json(row))
}

/// POST /api/folders
pub async fn create_folder(
    State(state): State<AppState>,
    Json(body): Json<CreateFolderRequest>,
) -> Result<Json<FolderListItem>, AppError> {
    if body.nama_folder.trim().is_empty() {
        return Err(AppError::BadRequest("nama_folder tidak boleh kosong".to_string()));
    }

    let id = sqlx::query_scalar::<_, Uuid>(
        r#"
        INSERT INTO folders (user_id, nama_folder, bahasa_sumber, bahasa_target, parent_id)
        VALUES ($1, $2, $3, $4, $5)
        RETURNING id
        "#,
    )
    .bind(state.default_user_id)
    .bind(body.nama_folder.trim())
    .bind(body.bahasa_sumber)
    .bind(body.bahasa_target)
    .bind(body.parent_id)
    .fetch_one(&state.db)
    .await?;

    get_folder(State(state), Path(id)).await
}

/// PUT /api/folders/:id
pub async fn update_folder(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(body): Json<UpdateFolderRequest>,
) -> Result<Json<FolderListItem>, AppError> {
    let updated = sqlx::query_scalar::<_, Uuid>(
        r#"
        UPDATE folders SET
            nama_folder = COALESCE($1, nama_folder),
            bahasa_sumber = COALESCE($2, bahasa_sumber),
            bahasa_target = COALESCE($3, bahasa_target),
            parent_id = COALESCE($4, parent_id),
            updated_at = NOW()
        WHERE id = $5 AND user_id = $6
        RETURNING id
        "#,
    )
    .bind(body.nama_folder)
    .bind(body.bahasa_sumber)
    .bind(body.bahasa_target)
    .bind(body.parent_id)
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Folder tidak ditemukan".to_string()))?;

    get_folder(State(state), Path(updated)).await
}

/// DELETE /api/folders/:id
/// Menghapus folder beserta semua subfolder & materi di dalamnya (cascade di DB),
/// tapi file PDF fisik di disk harus dihapus manual dulu sebelum row DB dihapus.
pub async fn delete_folder(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, AppError> {
    // Pastikan folder ini milik user & ada
    let exists = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM folders WHERE id = $1 AND user_id = $2",
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?;

    if exists.is_none() {
        return Err(AppError::NotFound("Folder tidak ditemukan".to_string()));
    }

    // Cari semua file PDF di folder ini + semua subfolder (rekursif) sebelum dihapus
    let file_paths: Vec<String> = sqlx::query_scalar(
        r#"
        WITH RECURSIVE sub_folders AS (
            SELECT id FROM folders WHERE id = $1
            UNION ALL
            SELECT f.id FROM folders f INNER JOIN sub_folders s ON f.parent_id = s.id
        )
        SELECT file_pdf FROM materi
        WHERE folder_id IN (SELECT id FROM sub_folders) AND file_pdf IS NOT NULL
        "#,
    )
    .bind(id)
    .fetch_all(&state.db)
    .await?;

    // Hapus DB dulu (cascade akan bereskan subfolder & materi),
    // baru hapus file fisik supaya kalau ada error DB, file tidak kepalang hilang.
    sqlx::query("DELETE FROM folders WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(state.default_user_id)
        .execute(&state.db)
        .await?;

    for filename in file_paths {
        storage::delete_pdf_file(&filename).await;
    }

    Ok(Json(serde_json::json!({ "deleted": true })))
}
