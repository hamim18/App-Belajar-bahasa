use std::collections::HashMap;

use axum::{
    extract::{Path, State},
    Json,
};
use serde_json::json;
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{
        CreateBabRequest, DaftarIsiFlatRow, DaftarIsiNode, ExportDaftarIsiResponse,
        ImportBabItem, ImportDaftarIsiRequest, UpdateBabRequest,
    },
    state::AppState,
};

// ============================================================
// HELPER INTERNAL
// ============================================================

/// Pastikan materi ada & milik user, sekaligus ambil bahasa_sumber/bahasa_target
/// miliknya - dipakai untuk tahu bahasa mana yang harus dibaca dari
/// daftar_isi_judul (kolom `bahasa`).
async fn fetch_materi_bahasa(
    state: &AppState,
    materi_id: Uuid,
) -> Result<(String, String), AppError> {
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

/// Ambil seluruh daftar_isi milik satu materi dalam bentuk nested tree.
async fn fetch_tree(
    state: &AppState,
    materi_id: Uuid,
    bahasa_sumber: &str,
    bahasa_target: &str,
) -> Result<Vec<DaftarIsiNode>, AppError> {
    let rows = sqlx::query_as::<_, DaftarIsiFlatRow>(
        r#"
        SELECT
            d.id, d.halaman_awal, d.halaman_akhir, d.level, d.urutan, d.parent_id,
            js.judul AS judul_sumber,
            jt.judul AS judul_target
        FROM daftar_isi d
        LEFT JOIN daftar_isi_judul js ON js.daftar_isi_id = d.id AND js.bahasa = $2
        LEFT JOIN daftar_isi_judul jt ON jt.daftar_isi_id = d.id AND jt.bahasa = $3
        WHERE d.materi_id = $1
        ORDER BY d.level ASC, d.urutan ASC
        "#,
    )
    .bind(materi_id)
    .bind(bahasa_sumber)
    .bind(bahasa_target)
    .fetch_all(&state.db)
    .await?;

    Ok(build_tree(rows))
}

/// Susun baris flat (sudah terurut level+urutan) jadi tree bersarang
/// berdasarkan parent_id. Anak-anak sebuah bab akan tampil sesuai urutan
/// asal karena Vec mempertahankan insertion order.
fn build_tree(rows: Vec<DaftarIsiFlatRow>) -> Vec<DaftarIsiNode> {
    let mut children_map: HashMap<Option<Uuid>, Vec<DaftarIsiFlatRow>> = HashMap::new();
    for row in rows {
        children_map.entry(row.parent_id).or_default().push(row);
    }

    fn build_level(
        parent_id: Option<Uuid>,
        children_map: &HashMap<Option<Uuid>, Vec<DaftarIsiFlatRow>>,
    ) -> Vec<DaftarIsiNode> {
        let mut nodes = Vec::new();
        if let Some(rows) = children_map.get(&parent_id) {
            for row in rows {
                let sub_bab = build_level(Some(row.id), children_map);
                nodes.push(DaftarIsiNode {
                    id: row.id,
                    halaman_awal: row.halaman_awal,
                    halaman_akhir: row.halaman_akhir,
                    level: row.level,
                    urutan: row.urutan,
                    parent_id: row.parent_id,
                    judul_sumber: row.judul_sumber.clone(),
                    judul_target: row.judul_target.clone(),
                    sub_bab,
                });
            }
        }
        nodes
    }

    build_level(None, &children_map)
}

/// Ubah satu DaftarIsiNode (hasil GET) jadi ImportBabItem (bentuk export/import),
/// dipakai untuk fitur Export supaya hasilnya bisa langsung dipakai lagi sebagai
/// Import.
fn node_to_export_item(node: &DaftarIsiNode) -> ImportBabItem {
    ImportBabItem {
        judul_sumber: node.judul_sumber.clone().unwrap_or_default(),
        judul_target: node.judul_target.clone().unwrap_or_default(),
        halaman_awal: node.halaman_awal,
        halaman_akhir: node.halaman_akhir,
        level: node.level,
        urutan: node.urutan,
        sub_bab: node.sub_bab.iter().map(node_to_export_item).collect(),
    }
}

/// Insert seluruh item (termasuk semua sub_bab bersarang di bawahnya) ke
/// dalam transaksi yang sama. Dipakai oleh import_daftar_isi.
///
/// Sengaja ditulis iteratif (pakai stack), bukan rekursif, karena rekursi
/// pada async fn di Rust butuh Box::pin manual yang berbelit soal lifetime
/// borrow &mut Transaction. Urutan pop dari stack tidak memengaruhi hasil
/// akhir - yang penting parent selalu di-insert (dan id-nya diketahui)
/// sebelum anaknya di-push ke stack, sehingga relasi parent_id selalu benar.
async fn insert_daftar_isi_items(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    materi_id: Uuid,
    bahasa_sumber: &str,
    bahasa_target: &str,
    items: &[ImportBabItem],
) -> Result<(), AppError> {
    let mut stack: Vec<(&ImportBabItem, Option<Uuid>)> =
        items.iter().map(|it| (it, None)).collect();

    while let Some((item, parent_id)) = stack.pop() {
        let id = sqlx::query_scalar::<_, Uuid>(
            r#"
            INSERT INTO daftar_isi (materi_id, halaman_awal, halaman_akhir, level, parent_id, urutan)
            VALUES ($1, $2, $3, $4, $5, $6)
            RETURNING id
            "#,
        )
        .bind(materi_id)
        .bind(item.halaman_awal)
        .bind(item.halaman_akhir)
        .bind(item.level)
        .bind(parent_id)
        .bind(item.urutan)
        .fetch_one(&mut **tx)
        .await?;

        sqlx::query(
            r#"
            INSERT INTO daftar_isi_judul (daftar_isi_id, bahasa, judul)
            VALUES ($1, $2, $3), ($1, $4, $5)
            "#,
        )
        .bind(id)
        .bind(bahasa_sumber)
        .bind(item.judul_sumber.trim())
        .bind(bahasa_target)
        .bind(item.judul_target.trim())
        .execute(&mut **tx)
        .await?;

        for sub in &item.sub_bab {
            stack.push((sub, Some(id)));
        }
    }

    Ok(())
}

async fn upsert_judul(
    state: &AppState,
    daftar_isi_id: Uuid,
    bahasa: &str,
    judul: &str,
) -> Result<(), AppError> {
    sqlx::query(
        r#"
        INSERT INTO daftar_isi_judul (daftar_isi_id, bahasa, judul)
        VALUES ($1, $2, $3)
        ON CONFLICT (daftar_isi_id, bahasa) DO UPDATE SET judul = EXCLUDED.judul
        "#,
    )
    .bind(daftar_isi_id)
    .bind(bahasa)
    .bind(judul.trim())
    .execute(&state.db)
    .await?;
    Ok(())
}

async fn fetch_single_node(
    state: &AppState,
    id: Uuid,
    bahasa_sumber: &str,
    bahasa_target: &str,
) -> Result<DaftarIsiNode, AppError> {
    let row = sqlx::query_as::<_, DaftarIsiFlatRow>(
        r#"
        SELECT
            d.id, d.halaman_awal, d.halaman_akhir, d.level, d.urutan, d.parent_id,
            js.judul AS judul_sumber,
            jt.judul AS judul_target
        FROM daftar_isi d
        LEFT JOIN daftar_isi_judul js ON js.daftar_isi_id = d.id AND js.bahasa = $2
        LEFT JOIN daftar_isi_judul jt ON jt.daftar_isi_id = d.id AND jt.bahasa = $3
        WHERE d.id = $1
        "#,
    )
    .bind(id)
    .bind(bahasa_sumber)
    .bind(bahasa_target)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Bab tidak ditemukan".to_string()))?;

    Ok(DaftarIsiNode {
        id: row.id,
        halaman_awal: row.halaman_awal,
        halaman_akhir: row.halaman_akhir,
        level: row.level,
        urutan: row.urutan,
        parent_id: row.parent_id,
        judul_sumber: row.judul_sumber,
        judul_target: row.judul_target,
        sub_bab: vec![],
    })
}

// ============================================================
// HANDLER (dipanggil dari router di main.rs)
// ============================================================

/// GET /api/materi/:materi_id/daftar-isi
pub async fn get_daftar_isi(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
) -> Result<Json<Vec<DaftarIsiNode>>, AppError> {
    let (bahasa_sumber, bahasa_target) = fetch_materi_bahasa(&state, materi_id).await?;
    let tree = fetch_tree(&state, materi_id, &bahasa_sumber, &bahasa_target).await?;
    Ok(Json(tree))
}

/// POST /api/materi/:materi_id/daftar-isi  (tambah bab/sub-bab manual)
pub async fn create_bab(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
    Json(body): Json<CreateBabRequest>,
) -> Result<Json<DaftarIsiNode>, AppError> {
    let (bahasa_sumber, bahasa_target) = fetch_materi_bahasa(&state, materi_id).await?;

    if body.judul_sumber.trim().is_empty() {
        return Err(AppError::BadRequest("judul_sumber tidak boleh kosong".to_string()));
    }
    if body.halaman_awal > body.halaman_akhir {
        return Err(AppError::BadRequest(
            "halaman_awal tidak boleh lebih besar dari halaman_akhir".to_string(),
        ));
    }

    // Kalau ada parent_id, pastikan bab induk itu benar milik materi yang sama
    // (supaya tidak bisa "menyusupkan" sub-bab ke materi lain lewat parent_id sembarangan).
    if let Some(parent_id) = body.parent_id {
        let parent_ok = sqlx::query_scalar::<_, Uuid>(
            "SELECT id FROM daftar_isi WHERE id = $1 AND materi_id = $2",
        )
        .bind(parent_id)
        .bind(materi_id)
        .fetch_optional(&state.db)
        .await?;
        if parent_ok.is_none() {
            return Err(AppError::BadRequest(
                "parent_id tidak ditemukan di materi ini".to_string(),
            ));
        }
    }

    let id = sqlx::query_scalar::<_, Uuid>(
        r#"
        INSERT INTO daftar_isi (materi_id, halaman_awal, halaman_akhir, level, parent_id, urutan)
        VALUES ($1, $2, $3, $4, $5, $6)
        RETURNING id
        "#,
    )
    .bind(materi_id)
    .bind(body.halaman_awal)
    .bind(body.halaman_akhir)
    .bind(body.level)
    .bind(body.parent_id)
    .bind(body.urutan)
    .fetch_one(&state.db)
    .await?;

    sqlx::query(
        r#"
        INSERT INTO daftar_isi_judul (daftar_isi_id, bahasa, judul)
        VALUES ($1, $2, $3), ($1, $4, $5)
        "#,
    )
    .bind(id)
    .bind(&bahasa_sumber)
    .bind(body.judul_sumber.trim())
    .bind(&bahasa_target)
    .bind(body.judul_target.trim())
    .execute(&state.db)
    .await?;

    Ok(Json(DaftarIsiNode {
        id,
        halaman_awal: body.halaman_awal,
        halaman_akhir: body.halaman_akhir,
        level: body.level,
        urutan: body.urutan,
        parent_id: body.parent_id,
        judul_sumber: Some(body.judul_sumber.trim().to_string()),
        judul_target: Some(body.judul_target.trim().to_string()),
        sub_bab: vec![],
    }))
}

/// PUT /api/daftar-isi/:id
pub async fn update_bab(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(body): Json<UpdateBabRequest>,
) -> Result<Json<DaftarIsiNode>, AppError> {
    // Ambil materi_id & bahasa dulu, sekaligus jadi pengecekan kepemilikan.
    let (materi_id, bahasa_sumber, bahasa_target) = sqlx::query_as::<_, (Uuid, String, String)>(
        r#"
        SELECT d.materi_id, m.bahasa_sumber, m.bahasa_target
        FROM daftar_isi d
        JOIN materi m ON m.id = d.materi_id
        JOIN folders f ON f.id = m.folder_id
        WHERE d.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::NotFound("Bab tidak ditemukan".to_string()))?;

    if let Some(parent_id) = body.parent_id {
        if parent_id == id {
            return Err(AppError::BadRequest(
                "Sebuah bab tidak bisa menjadi induk dirinya sendiri".to_string(),
            ));
        }
        let parent_ok = sqlx::query_scalar::<_, Uuid>(
            "SELECT id FROM daftar_isi WHERE id = $1 AND materi_id = $2",
        )
        .bind(parent_id)
        .bind(materi_id)
        .fetch_optional(&state.db)
        .await?;
        if parent_ok.is_none() {
            return Err(AppError::BadRequest(
                "parent_id tidak ditemukan di materi ini".to_string(),
            ));
        }
    }

    sqlx::query(
        r#"
        UPDATE daftar_isi SET
            halaman_awal = COALESCE($1, halaman_awal),
            halaman_akhir = COALESCE($2, halaman_akhir),
            level = COALESCE($3, level),
            urutan = COALESCE($4, urutan),
            parent_id = COALESCE($5, parent_id),
            updated_at = NOW()
        WHERE id = $6
        "#,
    )
    .bind(body.halaman_awal)
    .bind(body.halaman_akhir)
    .bind(body.level)
    .bind(body.urutan)
    .bind(body.parent_id)
    .bind(id)
    .execute(&state.db)
    .await?;

    if let Some(judul_sumber) = &body.judul_sumber {
        upsert_judul(&state, id, &bahasa_sumber, judul_sumber).await?;
    }
    if let Some(judul_target) = &body.judul_target {
        upsert_judul(&state, id, &bahasa_target, judul_target).await?;
    }

    let node = fetch_single_node(&state, id, &bahasa_sumber, &bahasa_target).await?;
    Ok(Json(node))
}

/// DELETE /api/daftar-isi/:id
/// Sub-bab di bawahnya ikut terhapus (ON DELETE CASCADE parent_id),
/// begitu juga kosakata_konteks yang menunjuk ke bab ini (bab_id -> SET NULL
/// tidak dipakai di migrasi awal, jadi ikut CASCADE - lihat migrations/0001_init.sql).
pub async fn delete_bab(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, AppError> {
    let exists = sqlx::query_scalar::<_, Uuid>(
        r#"
        SELECT d.id
        FROM daftar_isi d
        JOIN materi m ON m.id = d.materi_id
        JOIN folders f ON f.id = m.folder_id
        WHERE d.id = $1 AND f.user_id = $2
        "#,
    )
    .bind(id)
    .bind(state.default_user_id)
    .fetch_optional(&state.db)
    .await?;

    if exists.is_none() {
        return Err(AppError::NotFound("Bab tidak ditemukan".to_string()));
    }

    sqlx::query("DELETE FROM daftar_isi WHERE id = $1")
        .bind(id)
        .execute(&state.db)
        .await?;

    Ok(Json(json!({ "deleted": true })))
}

/// POST /api/materi/:materi_id/daftar-isi/import
/// Mengganti TOTAL seluruh daftar isi materi ini dengan isi JSON yang dikirim
/// (delete semua lalu insert ulang dalam satu transaksi). bahasa_sumber &
/// bahasa_target di body WAJIB sama persis dengan bahasa milik materi.
pub async fn import_daftar_isi(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
    Json(body): Json<ImportDaftarIsiRequest>,
) -> Result<Json<Vec<DaftarIsiNode>>, AppError> {
    let (materi_bahasa_sumber, materi_bahasa_target) =
        fetch_materi_bahasa(&state, materi_id).await?;

    if body.bahasa_sumber != materi_bahasa_sumber || body.bahasa_target != materi_bahasa_target {
        return Err(AppError::BadRequest(format!(
            "bahasa_sumber/bahasa_target pada JSON ({}/{}) harus sama dengan bahasa materi ini ({}/{})",
            body.bahasa_sumber, body.bahasa_target, materi_bahasa_sumber, materi_bahasa_target
        )));
    }

    let mut tx = state.db.begin().await?;

    sqlx::query("DELETE FROM daftar_isi WHERE materi_id = $1")
        .bind(materi_id)
        .execute(&mut *tx)
        .await?;

    insert_daftar_isi_items(
        &mut tx,
        materi_id,
        &materi_bahasa_sumber,
        &materi_bahasa_target,
        &body.daftar_isi,
    )
    .await?;

    tx.commit().await?;

    let tree = fetch_tree(&state, materi_id, &materi_bahasa_sumber, &materi_bahasa_target).await?;
    Ok(Json(tree))
}

/// GET /api/materi/:materi_id/daftar-isi/export
pub async fn export_daftar_isi(
    State(state): State<AppState>,
    Path(materi_id): Path<Uuid>,
) -> Result<Json<ExportDaftarIsiResponse>, AppError> {
    let (bahasa_sumber, bahasa_target) = fetch_materi_bahasa(&state, materi_id).await?;
    let tree = fetch_tree(&state, materi_id, &bahasa_sumber, &bahasa_target).await?;
    let daftar_isi = tree.iter().map(node_to_export_item).collect();

    Ok(Json(ExportDaftarIsiResponse {
        bahasa_sumber,
        bahasa_target,
        daftar_isi,
    }))
}

/// GET /api/daftar-isi/template
/// Template statis (tidak butuh materi_id) supaya Android bisa tampilkan
/// contoh bentuk JSON yang benar sebelum user mulai import.
pub async fn get_template() -> Json<serde_json::Value> {
    Json(json!({
        "bahasa_sumber": "ja",
        "bahasa_target": "id",
        "daftar_isi": [
            {
                "judul_sumber": "第1章 はじめに",
                "judul_target": "Bab 1 Pendahuluan",
                "halaman_awal": 3,
                "halaman_akhir": 6,
                "level": 1,
                "urutan": 1,
                "sub_bab": [
                    {
                        "judul_sumber": "1-1 あいさつ",
                        "judul_target": "Sapaan Dasar",
                        "halaman_awal": 3,
                        "halaman_akhir": 4,
                        "level": 2,
                        "urutan": 1
                    }
                ]
            }
        ]
    }))
}
