use axum::{
    extract::{Path, Query, State},
    Json,
};
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{
        CreateKamusRequest, KamusBaseRow, KamusDetail, KamusDetailQuery, KamusListItem,
        KamusSearchResponse, KemunculanMateri, KemunculanRow, SearchKamusQuery,
        UpdateKamusTerjemahanRequest,
    },
    state::AppState,
};

/// Ambil bahasa default user (dipakai kalau query param bahasa_sumber/
/// bahasa_target tidak dikirim client) - sinkron dengan kolom
/// bahasa_sumber_default/bahasa_target_default di tabel users
/// (migrations/0001_init.sql).
async fn default_bahasa(state: &AppState) -> Result<(String, String), AppError> {
    let row = sqlx::query_as::<_, (String, String)>(
        "SELECT bahasa_sumber_default, bahasa_target_default FROM users WHERE id = $1",
    )
    .bind(state.default_user_id)
    .fetch_one(&state.db)
    .await?;
    Ok(row)
}

/// GET /api/kamus?q=&bahasa_sumber=&bahasa_target=&tipe_kata=&limit=&offset=
pub async fn search_kamus(
    State(state): State<AppState>,
    Query(params): Query<SearchKamusQuery>,
) -> Result<Json<KamusSearchResponse>, AppError> {
    let (default_sumber, default_target) = default_bahasa(&state).await?;
    let bahasa_sumber = params.bahasa_sumber.unwrap_or(default_sumber);
    let bahasa_target = params.bahasa_target.unwrap_or(default_target);
    let limit = params.limit.unwrap_or(50).clamp(1, 200);
    let offset = params.offset.unwrap_or(0).max(0);
    let q_like = params
        .q
        .as_ref()
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .map(|s| format!("%{}%", s));

    let items = sqlx::query_as::<_, KamusListItem>(
        r#"
        SELECT
            k.id, k.kata_asli, k.bahasa_sumber, k.is_custom,
            kt.terjemahan, kt.reading, kt.tipe_kata, kt.contoh_kalimat,
            $1::text AS bahasa_target,
            COALESCE((SELECT COUNT(DISTINCT kc.materi_id) FROM kosakata_konteks kc WHERE kc.kata_id = k.id), 0) AS jumlah_materi,
            COALESCE((SELECT COUNT(*) FROM kosakata_konteks kc WHERE kc.kata_id = k.id), 0) AS jumlah_muncul
        FROM kamus k
        LEFT JOIN kamus_terjemahan kt ON kt.kata_id = k.id AND kt.bahasa_target = $1
        WHERE k.bahasa_sumber = $2
          AND (
            $3::text IS NULL
            OR k.kata_asli ILIKE $3
            OR kt.reading ILIKE $3
            OR kt.terjemahan ILIKE $3
          )
          AND ($4::text IS NULL OR kt.tipe_kata = $4)
        ORDER BY k.kata_asli ASC
        LIMIT $5 OFFSET $6
        "#,
    )
    .bind(&bahasa_target)
    .bind(&bahasa_sumber)
    .bind(&q_like)
    .bind(&params.tipe_kata)
    .bind(limit)
    .bind(offset)
    .fetch_all(&state.db)
    .await?;

    let total = sqlx::query_scalar::<_, i64>(
        r#"
        SELECT COUNT(*)
        FROM kamus k
        LEFT JOIN kamus_terjemahan kt ON kt.kata_id = k.id AND kt.bahasa_target = $1
        WHERE k.bahasa_sumber = $2
          AND (
            $3::text IS NULL
            OR k.kata_asli ILIKE $3
            OR kt.reading ILIKE $3
            OR kt.terjemahan ILIKE $3
          )
          AND ($4::text IS NULL OR kt.tipe_kata = $4)
        "#,
    )
    .bind(&bahasa_target)
    .bind(&bahasa_sumber)
    .bind(&q_like)
    .bind(&params.tipe_kata)
    .fetch_one(&state.db)
    .await?;

    Ok(Json(KamusSearchResponse { items, total }))
}

/// GET /api/kamus/:id?bahasa_target=
pub async fn get_kamus_detail(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Query(params): Query<KamusDetailQuery>,
) -> Result<Json<KamusDetail>, AppError> {
    let (_, default_target) = default_bahasa(&state).await?;
    let bahasa_target = params.bahasa_target.unwrap_or(default_target);

    let base = sqlx::query_as::<_, KamusBaseRow>(
        r#"
        SELECT k.id, k.kata_asli, k.bahasa_sumber, k.is_custom,
               kt.terjemahan, kt.reading, kt.tipe_kata, kt.contoh_kalimat
        FROM kamus k
        LEFT JOIN kamus_terjemahan kt ON kt.kata_id = k.id AND kt.bahasa_target = $1
        WHERE k.id = $2
        "#,
    )
    .bind(&bahasa_target)
    .bind(id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Kata tidak ditemukan".to_string()))?;

    // Kemunculan dibatasi ke materi milik default_user_id ini (belum ada
    // multi-user sungguhan, tapi query sudah disiapkan filter per user
    // supaya tidak perlu diubah lagi nanti kalau auth sudah ada).
    let rows = sqlx::query_as::<_, KemunculanRow>(
        r#"
        SELECT kc.materi_id, m.judul AS judul_materi, kc.bab_id,
               dij.judul AS judul_bab, kc.halaman
        FROM kosakata_konteks kc
        JOIN materi m ON m.id = kc.materi_id
        JOIN folders f ON f.id = m.folder_id
        LEFT JOIN daftar_isi_judul dij
            ON dij.daftar_isi_id = kc.bab_id AND dij.bahasa = m.bahasa_sumber
        WHERE kc.kata_id = $1 AND f.user_id = $2
        ORDER BY m.judul ASC, kc.halaman ASC
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_all(&state.db)
    .await?;

    // Kelompokkan per (materi_id, bab_id) supaya halaman jadi satu daftar
    // per bab, bukan satu baris per halaman.
    let mut kemunculan: Vec<KemunculanMateri> = Vec::new();
    for row in rows {
        if let Some(existing) = kemunculan
            .iter_mut()
            .find(|k| k.materi_id == row.materi_id && k.bab_id == row.bab_id)
        {
            existing.halaman_list.push(row.halaman);
        } else {
            kemunculan.push(KemunculanMateri {
                materi_id: row.materi_id,
                judul_materi: row.judul_materi,
                bab_id: row.bab_id,
                judul_bab: row.judul_bab,
                halaman_list: vec![row.halaman],
            });
        }
    }

    Ok(Json(KamusDetail {
        id: base.id,
        kata_asli: base.kata_asli,
        bahasa_sumber: base.bahasa_sumber,
        is_custom: base.is_custom,
        bahasa_target,
        terjemahan: base.terjemahan,
        reading: base.reading,
        tipe_kata: base.tipe_kata,
        contoh_kalimat: base.contoh_kalimat,
        kemunculan,
    }))
}

/// POST /api/kamus - tambah kata custom baru, atau tambah/ubah terjemahan
/// untuk kata yang kata_asli+bahasa_sumber-nya sudah ada.
pub async fn create_kamus(
    State(state): State<AppState>,
    Json(body): Json<CreateKamusRequest>,
) -> Result<Json<KamusDetail>, AppError> {
    let kata_asli = body.kata_asli.trim();
    if kata_asli.is_empty() {
        return Err(AppError::BadRequest("kata_asli tidak boleh kosong".to_string()));
    }
    if body.terjemahan.trim().is_empty() {
        return Err(AppError::BadRequest("terjemahan tidak boleh kosong".to_string()));
    }

    let existing_id = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM kamus WHERE kata_asli = $1 AND bahasa_sumber = $2",
    )
    .bind(kata_asli)
    .bind(&body.bahasa_sumber)
    .fetch_optional(&state.db)
    .await?;

    let kata_id = match existing_id {
        Some(id) => id,
        None => {
            sqlx::query_scalar::<_, Uuid>(
                r#"
                INSERT INTO kamus (kata_asli, bahasa_sumber, is_custom)
                VALUES ($1, $2, true)
                RETURNING id
                "#,
            )
            .bind(kata_asli)
            .bind(&body.bahasa_sumber)
            .fetch_one(&state.db)
            .await?
        }
    };

    sqlx::query(
        r#"
        INSERT INTO kamus_terjemahan (kata_id, bahasa_target, terjemahan, reading, tipe_kata, contoh_kalimat)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (kata_id, bahasa_target) DO UPDATE SET
            terjemahan = EXCLUDED.terjemahan,
            reading = EXCLUDED.reading,
            tipe_kata = EXCLUDED.tipe_kata,
            contoh_kalimat = EXCLUDED.contoh_kalimat,
            updated_at = NOW()
        "#,
    )
    .bind(kata_id)
    .bind(&body.bahasa_target)
    .bind(body.terjemahan.trim())
    .bind(&body.reading)
    .bind(&body.tipe_kata)
    .bind(&body.contoh_kalimat)
    .execute(&state.db)
    .await?;

    get_kamus_detail(
        State(state),
        Path(kata_id),
        Query(KamusDetailQuery {
            bahasa_target: Some(body.bahasa_target),
        }),
    )
    .await
}

/// PUT /api/kamus/:id - upsert terjemahan untuk kata_id di path (tidak
/// mengubah kata_asli/bahasa_sumber - itu identitas baris kamus).
pub async fn update_kamus_terjemahan(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(body): Json<UpdateKamusTerjemahanRequest>,
) -> Result<Json<KamusDetail>, AppError> {
    let kata_exists = sqlx::query_scalar::<_, Uuid>("SELECT id FROM kamus WHERE id = $1")
        .bind(id)
        .fetch_optional(&state.db)
        .await?
        .is_some();
    if !kata_exists {
        return Err(AppError::NotFound("Kata tidak ditemukan".to_string()));
    }

    sqlx::query(
        r#"
        INSERT INTO kamus_terjemahan (kata_id, bahasa_target, terjemahan, reading, tipe_kata, contoh_kalimat)
        VALUES ($1, $2, COALESCE($3, ''), $4, $5, $6)
        ON CONFLICT (kata_id, bahasa_target) DO UPDATE SET
            terjemahan = COALESCE($3, kamus_terjemahan.terjemahan),
            reading = COALESCE($4, kamus_terjemahan.reading),
            tipe_kata = COALESCE($5, kamus_terjemahan.tipe_kata),
            contoh_kalimat = COALESCE($6, kamus_terjemahan.contoh_kalimat),
            updated_at = NOW()
        "#,
    )
    .bind(id)
    .bind(&body.bahasa_target)
    .bind(&body.terjemahan)
    .bind(&body.reading)
    .bind(&body.tipe_kata)
    .bind(&body.contoh_kalimat)
    .execute(&state.db)
    .await?;

    get_kamus_detail(
        State(state),
        Path(id),
        Query(KamusDetailQuery {
            bahasa_target: Some(body.bahasa_target),
        }),
    )
    .await
}
