use axum::{
    extract::{Path, State},
    Json,
};
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{ProgressBaca, UpdateProgressRequest},
    state::AppState,
};

/// Pastikan materi ada & milik user (default_user_id), kembalikan
/// total_halaman-nya (dipakai untuk hitung progress_persen).
async fn get_materi_total_halaman(state: &AppState, materi_id: Uuid) -> Result<i32, AppError> {
    sqlx::query_scalar::<_, i32>(
        r#"
        SELECT m.total_halaman
        FROM materi m
        JOIN folders f ON f.id = m.folder_id
        WHERE m.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(materi_id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Materi tidak ditemukan".to_string()))
}

/// GET /api/materi/:id/progress
/// Kalau belum pernah ada record progress untuk user ini, kembalikan nilai
/// default (halaman 0, 0%) TANPA membuat row baru di DB - row baru baru
/// dibuat saat PUT pertama kali dipanggil (lihat update_progress di bawah).
pub async fn get_progress(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
) -> Result<Json<ProgressBaca>, AppError> {
    // Validasi materi ada & milik user (dan sekalian error NotFound kalau tidak).
    get_materi_total_halaman(&state, materi_id).await?;

    let existing = sqlx::query_as::<_, ProgressBaca>(
        r#"
        SELECT materi_id, halaman_terakhir, progress_persen, last_read_at
        FROM progress_baca
        WHERE materi_id = $1 AND user_id = $2
        "#,
    )
    .bind(materi_id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?;

    Ok(Json(existing.unwrap_or(ProgressBaca {
        materi_id,
        halaman_terakhir: 0,
        progress_persen: 0,
        last_read_at: chrono::Utc::now().naive_utc(),
    })))
}

/// PUT /api/materi/:id/progress
/// Upsert: kalau user belum pernah punya row progress untuk materi ini,
/// dibuat baru; kalau sudah ada, diupdate. progress_persen dihitung di sini
/// (bukan dikirim dari Android) supaya rumusnya konsisten di satu tempat.
pub async fn update_progress(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
    Json(body): Json<UpdateProgressRequest>,
) -> Result<Json<ProgressBaca>, AppError> {
    let total_halaman = get_materi_total_halaman(&state, materi_id).await?;

    if body.halaman_terakhir < 0 {
        return Err(AppError::BadRequest(
            "halaman_terakhir tidak boleh negatif".to_string(),
        ));
    }

    let persen = if total_halaman > 0 {
        ((body.halaman_terakhir as f64 / total_halaman as f64) * 100.0).clamp(0.0, 100.0) as i32
    } else {
        0
    };

    let row = sqlx::query_as::<_, ProgressBaca>(
        r#"
        INSERT INTO progress_baca (materi_id, user_id, halaman_terakhir, progress_persen, last_read_at)
        VALUES ($1, $2, $3, $4, NOW())
        ON CONFLICT (materi_id, user_id)
        DO UPDATE SET
            halaman_terakhir = EXCLUDED.halaman_terakhir,
            progress_persen = EXCLUDED.progress_persen,
            last_read_at = NOW()
        RETURNING materi_id, halaman_terakhir, progress_persen, last_read_at
        "#,
    )
    .bind(materi_id)
    .bind(state.default_user_id)
    .bind(body.halaman_terakhir)
    .bind(persen)
    .fetch_one(&state.db)
    .await?;

    Ok(Json(row))
}
