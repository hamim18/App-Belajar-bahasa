use axum::{
    extract::{Path, Query, State},
    Json,
};
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{
        CreateKosakataRequest, KosakataBabItem, KosakataHalamanQuery, KosakataItem,
        UpdateKosakataRequest,
    },
    state::AppState,
};

/// Ambil bahasa_sumber/bahasa_target materi + pastikan materi ini milik
/// user (join ke folders, pola yang sama dipakai di handlers lain seperti
/// progress.rs/bookmarks.rs).
async fn get_materi_bahasa(state: &AppState, materi_id: Uuid) -> Result<(String, String), AppError> {
    sqlx::query_as::<_, (String, String)>(
        r#"
        SELECT m.bahasa_sumber, m.bahasa_target
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

/// Ambil satu KosakataItem lengkap (dengan halaman_terkait) berdasarkan id
/// kosakata_konteks - dipakai setelah insert/update supaya bentuk response
/// selalu konsisten dengan bentuk yang dipakai endpoint list.
async fn fetch_kosakata_item(
    state: &AppState,
    kosakata_id: Uuid,
    bahasa_target: &str,
) -> Result<KosakataItem, AppError> {
    sqlx::query_as::<_, KosakataItem>(
        r#"
        SELECT
            kc.id, k.id AS kata_id, k.kata_asli, kt.reading, kt.terjemahan, kt.tipe_kata,
            kc.catatan_pribadi, kc.folder_kustom, kc.halaman, kc.bab_id,
            (
                SELECT ARRAY_AGG(DISTINCT kc2.halaman ORDER BY kc2.halaman)
                FROM kosakata_konteks kc2
                WHERE kc2.kata_id = k.id AND kc2.materi_id = kc.materi_id
            ) AS halaman_terkait
        FROM kosakata_konteks kc
        JOIN kamus k ON k.id = kc.kata_id
        LEFT JOIN kamus_terjemahan kt ON kt.kata_id = k.id AND kt.bahasa_target = $1
        WHERE kc.id = $2
        "#,
    )
    .bind(bahasa_target)
    .bind(kosakata_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Kosakata tidak ditemukan".to_string()))
}

/// GET /api/materi/:materi_id/halaman/:halaman/kosakata?bab_id=
/// bab_id opsional. Kalau dikirim, hanya kosakata dengan bab_id itu persis
/// yang dikembalikan; kalau tidak dikirim, semua kosakata di halaman itu
/// (lintas bab) dikembalikan.
pub async fn list_kosakata_halaman(
    State(state): State<AppState>,
    Path((materi_id, halaman)): Path<(Uuid, i32)>,
    Query(params): Query<KosakataHalamanQuery>,
) -> Result<Json<Vec<KosakataItem>>, AppError> {
    let (_, bahasa_target) = get_materi_bahasa(&state, materi_id).await?;

    let items = sqlx::query_as::<_, KosakataItem>(
        r#"
        SELECT
            kc.id, k.id AS kata_id, k.kata_asli, kt.reading, kt.terjemahan, kt.tipe_kata,
            kc.catatan_pribadi, kc.folder_kustom, kc.halaman, kc.bab_id,
            (
                SELECT ARRAY_AGG(DISTINCT kc2.halaman ORDER BY kc2.halaman)
                FROM kosakata_konteks kc2
                WHERE kc2.kata_id = k.id AND kc2.materi_id = kc.materi_id
            ) AS halaman_terkait
        FROM kosakata_konteks kc
        JOIN kamus k ON k.id = kc.kata_id
        LEFT JOIN kamus_terjemahan kt ON kt.kata_id = k.id AND kt.bahasa_target = $1
        WHERE kc.materi_id = $2 AND kc.halaman = $3
          AND ($4::uuid IS NULL OR kc.bab_id = $4)
        ORDER BY k.kata_asli ASC
        "#,
    )
    .bind(&bahasa_target)
    .bind(materi_id)
    .bind(halaman)
    .bind(params.bab_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(items))
}

/// GET /api/materi/:materi_id/bab/:bab_id/kosakata
/// Dedup per kata, halaman_list = semua halaman di bab ini tempat kata itu muncul.
pub async fn list_kosakata_bab(
    State(state): State<AppState>,
    Path((materi_id, bab_id)): Path<(Uuid, Uuid)>,
) -> Result<Json<Vec<KosakataBabItem>>, AppError> {
    let (_, bahasa_target) = get_materi_bahasa(&state, materi_id).await?;

    let items = sqlx::query_as::<_, KosakataBabItem>(
        r#"
        SELECT
            k.id AS kata_id, k.kata_asli, kt.reading, kt.terjemahan, kt.tipe_kata,
            ARRAY_AGG(DISTINCT kc.halaman ORDER BY kc.halaman) AS halaman_list
        FROM kosakata_konteks kc
        JOIN kamus k ON k.id = kc.kata_id
        LEFT JOIN kamus_terjemahan kt ON kt.kata_id = k.id AND kt.bahasa_target = $1
        WHERE kc.materi_id = $2 AND kc.bab_id = $3
        GROUP BY k.id, k.kata_asli, kt.reading, kt.terjemahan, kt.tipe_kata
        ORDER BY k.kata_asli ASC
        "#,
    )
    .bind(&bahasa_target)
    .bind(materi_id)
    .bind(bab_id)
    .fetch_all(&state.db)
    .await?;

    Ok(Json(items))
}

/// POST /api/kosakata - tambah kata ke konteks (materi+bab+halaman).
///
/// Kalau kata_asli belum ada di kamus untuk bahasa_sumber materi ini,
/// otomatis dibuat sebagai kata custom (field terjemahan WAJIB diisi).
/// Kalau kombinasi (kata, materi, bab, halaman) sudah ada, catatan_pribadi/
/// folder_kustom di baris itu di-UPDATE (bukan bikin duplikat) - sesuai
/// aturan deduplikasi di Konsep-program-learn.md bagian 4.4.
pub async fn create_kosakata(
    State(state): State<AppState>,
    Json(body): Json<CreateKosakataRequest>,
) -> Result<Json<KosakataItem>, AppError> {
    let kata_asli = body.kata_asli.trim();
    if kata_asli.is_empty() {
        return Err(AppError::BadRequest("kata_asli tidak boleh kosong".to_string()));
    }
    if body.halaman < 1 {
        return Err(AppError::BadRequest("halaman harus >= 1".to_string()));
    }

    let (bahasa_sumber, bahasa_target) = get_materi_bahasa(&state, body.materi_id).await?;

    // Kalau bab_id dikirim, pastikan bab itu benar milik materi ini -
    // mencegah client (sengaja/tidak sengaja) mengirim bab_id dari materi lain.
    if let Some(bab_id) = body.bab_id {
        let bab_ok = sqlx::query_scalar::<_, Uuid>(
            "SELECT id FROM daftar_isi WHERE id = $1 AND materi_id = $2",
        )
        .bind(bab_id)
        .bind(body.materi_id)
        .fetch_optional(&state.db)
        .await?;
        if bab_ok.is_none() {
            return Err(AppError::BadRequest(
                "bab_id tidak valid untuk materi ini".to_string(),
            ));
        }
    }

    let existing_kata_id = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM kamus WHERE kata_asli = $1 AND bahasa_sumber = $2",
    )
    .bind(kata_asli)
    .bind(&bahasa_sumber)
    .fetch_optional(&state.db)
    .await?;

    let kata_id = match existing_kata_id {
        Some(id) => {
            // Kata sudah ada di kamus. Kalau terjemahan untuk bahasa_target
            // materi ini belum ada dan user mengirim terjemahan, lengkapi -
            // tapi TIDAK menimpa terjemahan yang sudah ada (itu tugas
            // PUT /api/kamus/:id, bukan endpoint tambah-kosakata ini).
            if let Some(terjemahan) = body.terjemahan.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
                let has_translation = sqlx::query_scalar::<_, Uuid>(
                    "SELECT kata_id FROM kamus_terjemahan WHERE kata_id = $1 AND bahasa_target = $2",
                )
                .bind(id)
                .bind(&bahasa_target)
                .fetch_optional(&state.db)
                .await?;
                if has_translation.is_none() {
                    sqlx::query(
                        r#"
                        INSERT INTO kamus_terjemahan (kata_id, bahasa_target, terjemahan, reading, tipe_kata, contoh_kalimat)
                        VALUES ($1, $2, $3, $4, $5, $6)
                        "#,
                    )
                    .bind(id)
                    .bind(&bahasa_target)
                    .bind(terjemahan)
                    .bind(&body.reading)
                    .bind(&body.tipe_kata)
                    .bind(&body.contoh_kalimat)
                    .execute(&state.db)
                    .await?;
                }
            }
            id
        }
        None => {
            // Kata baru - wajib ada terjemahan supaya kamus_terjemahan bisa diisi.
            let terjemahan = body
                .terjemahan
                .as_deref()
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .ok_or_else(|| {
                    AppError::BadRequest("Kata baru, terjemahan wajib diisi".to_string())
                })?;

            let new_id = sqlx::query_scalar::<_, Uuid>(
                r#"
                INSERT INTO kamus (kata_asli, bahasa_sumber, is_custom)
                VALUES ($1, $2, true)
                RETURNING id
                "#,
            )
            .bind(kata_asli)
            .bind(&bahasa_sumber)
            .fetch_one(&state.db)
            .await?;

            sqlx::query(
                r#"
                INSERT INTO kamus_terjemahan (kata_id, bahasa_target, terjemahan, reading, tipe_kata, contoh_kalimat)
                VALUES ($1, $2, $3, $4, $5, $6)
                "#,
            )
            .bind(new_id)
            .bind(&bahasa_target)
            .bind(terjemahan)
            .bind(&body.reading)
            .bind(&body.tipe_kata)
            .bind(&body.contoh_kalimat)
            .execute(&state.db)
            .await?;

            new_id
        }
    };

    // PENTING: pakai "IS NOT DISTINCT FROM" untuk bab_id, BUKAN "=".
    // bab_id nullable, dan UNIQUE constraint di DB (kata_id, materi_id,
    // bab_id, halaman) tidak menganggap dua NULL sebagai duplikat -
    // jadi kalau bab_id NULL, ON CONFLICT tidak bisa diandalkan untuk
    // dedup. Makanya dedup dicek manual lewat SELECT di sini dulu.
    let existing_konteks_id = sqlx::query_scalar::<_, Uuid>(
        r#"
        SELECT id FROM kosakata_konteks
        WHERE kata_id = $1 AND materi_id = $2 AND halaman = $3
          AND bab_id IS NOT DISTINCT FROM $4
        "#,
    )
    .bind(kata_id)
    .bind(body.materi_id)
    .bind(body.halaman)
    .bind(body.bab_id)
    .fetch_optional(&state.db)
    .await?;

    let kosakata_id = match existing_konteks_id {
        Some(id) => {
            sqlx::query(
                r#"
                UPDATE kosakata_konteks SET
                    catatan_pribadi = COALESCE($1, catatan_pribadi),
                    folder_kustom = COALESCE($2, folder_kustom),
                    updated_at = NOW()
                WHERE id = $3
                "#,
            )
            .bind(&body.catatan_pribadi)
            .bind(&body.folder_kustom)
            .bind(id)
            .execute(&state.db)
            .await?;
            id
        }
        None => {
            sqlx::query_scalar::<_, Uuid>(
                r#"
                INSERT INTO kosakata_konteks (kata_id, materi_id, bab_id, halaman, catatan_pribadi, folder_kustom)
                VALUES ($1, $2, $3, $4, $5, $6)
                RETURNING id
                "#,
            )
            .bind(kata_id)
            .bind(body.materi_id)
            .bind(body.bab_id)
            .bind(body.halaman)
            .bind(&body.catatan_pribadi)
            .bind(&body.folder_kustom)
            .fetch_one(&state.db)
            .await?
        }
    };

    let item = fetch_kosakata_item(&state, kosakata_id, &bahasa_target).await?;
    Ok(Json(item))
}

/// PUT /api/kosakata/:id - update catatan_pribadi/folder_kustom.
pub async fn update_kosakata(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(body): Json<UpdateKosakataRequest>,
) -> Result<Json<KosakataItem>, AppError> {
    let row = sqlx::query_as::<_, (Uuid, String)>(
        r#"
        SELECT kc.materi_id, m.bahasa_target
        FROM kosakata_konteks kc
        JOIN materi m ON m.id = kc.materi_id
        JOIN folders f ON f.id = m.folder_id
        WHERE kc.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Kosakata tidak ditemukan".to_string()))?;

    sqlx::query(
        r#"
        UPDATE kosakata_konteks SET
            catatan_pribadi = COALESCE($1, catatan_pribadi),
            folder_kustom = COALESCE($2, folder_kustom),
            updated_at = NOW()
        WHERE id = $3
        "#,
    )
    .bind(&body.catatan_pribadi)
    .bind(&body.folder_kustom)
    .bind(id)
    .execute(&state.db)
    .await?;

    let item = fetch_kosakata_item(&state, id, &row.1).await?;
    Ok(Json(item))
}

/// DELETE /api/kosakata/:id
pub async fn delete_kosakata(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, AppError> {
    let exists = sqlx::query_scalar::<_, Uuid>(
        r#"
        SELECT kc.id
        FROM kosakata_konteks kc
        JOIN materi m ON m.id = kc.materi_id
        JOIN folders f ON f.id = m.folder_id
        WHERE kc.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Kosakata tidak ditemukan".to_string()))?;

    sqlx::query("DELETE FROM kosakata_konteks WHERE id = $1")
        .bind(exists)
        .execute(&state.db)
        .await?;

    Ok(Json(serde_json::json!({ "deleted": true })))
}
