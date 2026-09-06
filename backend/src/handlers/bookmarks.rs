use axum::{
    extract::{Path, State},
    Json,
};
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{Bookmark, CreateBookmarkRequest},
    state::AppState,
};

/// GET /api/materi/:materi_id/bookmarks
pub async fn list_bookmarks(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
) -> Result<Json<Vec<Bookmark>>, AppError> {
    let exists = sqlx::query_scalar::<_, Uuid>(
        r#"
        SELECT m.id
        FROM materi m
        JOIN folders f ON f.id = m.folder_id
        WHERE m.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(materi_id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?;

    if exists.is_none() {
        return Err(AppError::NotFound("Materi tidak ditemukan".to_string()));
    }

    let rows = sqlx::query_as::<_, Bookmark>(
        r#"
        SELECT id, materi_id, halaman, catatan, created_at
        FROM bookmarks
        WHERE materi_id = $1 AND user_id = $2
        ORDER BY halaman ASC
        "#,
    )
    .bind(materi_id)
    .bind(state.default_user_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(rows))
}

/// POST /api/materi/:materi_id/bookmarks
pub async fn create_bookmark(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
    Json(body): Json<CreateBookmarkRequest>,
) -> Result<Json<Bookmark>, AppError> {
    if body.halaman < 1 {
        return Err(AppError::BadRequest("halaman harus >= 1".to_string()));
    }

    let materi_exists = sqlx::query_scalar::<_, Uuid>(
        r#"
        SELECT m.id
        FROM materi m
        JOIN folders f ON f.id = m.folder_id
        WHERE m.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(materi_id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Materi tidak ditemukan".to_string()))?;

    let row = sqlx::query_as::<_, Bookmark>(
        r#"
        INSERT INTO bookmarks (materi_id, user_id, halaman, catatan)
        VALUES ($1, $2, $3, $4)
        RETURNING id, materi_id, halaman, catatan, created_at
        "#,
    )
    .bind(materi_exists)
    .bind(state.default_user_id)
    .bind(body.halaman)
    .bind(body.catatan)
    .fetch_one(&state.db)
    .await?;

    Ok(Json(row))
}

/// DELETE /api/bookmarks/:id
pub async fn delete_bookmark(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, AppError> {
    let result = sqlx::query(
        r#"
        DELETE FROM bookmarks b
        USING materi m, folders f
        WHERE b.id = $1
          AND b.materi_id = m.id
          AND m.folder_id = f.id
          AND f.user_id = $2
          AND b.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .execute(&state.db)
    .await?;

    if result.rows_affected() == 0 {
        return Err(AppError::NotFound("Bookmark tidak ditemukan".to_string()));
    }

    Ok(Json(serde_json::json!({ "deleted": true })))
}
