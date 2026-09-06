mod error;
mod handlers;
mod models;
mod state;
mod storage;

use axum::{
    extract::DefaultBodyLimit,
    routing::{delete, get, post, put},
    Json, Router,
};
use serde_json::json;
use sqlx::postgres::PgPoolOptions;
use std::net::SocketAddr;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use uuid::Uuid;

use state::AppState;

const MAX_UPLOAD_SIZE: usize = 50 * 1024 * 1024; // 50 MB

#[tokio::main]
async fn main() {
    // Load .env kalau ada
    dotenvy::dotenv().ok();

    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info".into()),
        )
        .init();

    let database_url = std::env::var("DATABASE_URL")
        .expect("DATABASE_URL harus di-set di file .env, lihat .env.example");

    tracing::info!("Menghubungkan ke database...");
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&database_url)
        .await
        .expect("Gagal konek ke database. Cek DATABASE_URL dan pastikan PostgreSQL jalan.");

    tracing::info!("Menjalankan migrasi database...");
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("Gagal menjalankan migrasi");

    tracing::info!("Menyiapkan folder storage PDF...");
    storage::ensure_storage_dir()
        .await
        .expect("Gagal membuat folder storage/pdf");

    let default_user_id = get_or_create_default_user(&pool).await;
    tracing::info!("Default user ID (sementara, belum ada auth): {}", default_user_id);

    let state = AppState {
        db: pool,
        default_user_id,
    };

    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    let app = Router::new()
        .route("/api/health", get(health_check))
        // Folder
        .route(
            "/api/folders",
            get(handlers::folders::list_folders).post(handlers::folders::create_folder),
        )
        .route(
            "/api/folders/:id",
            get(handlers::folders::get_folder)
                .put(handlers::folders::update_folder)
                .delete(handlers::folders::delete_folder),
        )
        // Materi
        .route(
            "/api/folders/:folder_id/materi",
            get(handlers::materi::list_materi_by_folder),
        )
        .route(
            "/api/materi",
            post(handlers::materi::create_materi)
                .layer(DefaultBodyLimit::max(MAX_UPLOAD_SIZE)),
        )
        .route(
            "/api/materi/:id",
            get(handlers::materi::get_materi)
                .put(handlers::materi::update_materi)
                .delete(handlers::materi::delete_materi),
        )
        // File PDF mentah untuk PDF Viewer Android (Task 5)
        .route(
            "/api/materi/:id/file",
            get(handlers::materi::get_materi_file),
        )
        // Progress baca (Task 5)
        .route(
            "/api/materi/:id/progress",
            get(handlers::progress::get_progress).put(handlers::progress::update_progress),
        )
        // Bookmark (Task 5)
        .route(
            "/api/materi/:materi_id/bookmarks",
            get(handlers::bookmarks::list_bookmarks).post(handlers::bookmarks::create_bookmark),
        )
        .route(
            "/api/bookmarks/:id",
            delete(handlers::bookmarks::delete_bookmark),
        )
        // Daftar Isi
        .route(
            "/api/materi/:materi_id/daftar-isi",
            get(handlers::daftar_isi::get_daftar_isi).post(handlers::daftar_isi::create_bab),
        )
        .route(
            "/api/materi/:materi_id/daftar-isi/import",
            post(handlers::daftar_isi::import_daftar_isi),
        )
        .route(
            "/api/materi/:materi_id/daftar-isi/export",
            get(handlers::daftar_isi::export_daftar_isi),
        )
        .route(
            "/api/daftar-isi/template",
            get(handlers::daftar_isi::get_template),
        )
        .route(
            "/api/daftar-isi/:id",
            put(handlers::daftar_isi::update_bab).delete(handlers::daftar_isi::delete_bab),
        )
        .layer(TraceLayer::new_for_http())
        .layer(cors)
        .with_state(state);

    // Bind ke 0.0.0.0 supaya bisa diakses dari HP di jaringan wifi yang sama
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    tracing::info!("Server jalan di http://{}", addr);

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

async fn health_check(
    axum::extract::State(state): axum::extract::State<AppState>,
) -> Json<serde_json::Value> {
    let db_ok = sqlx::query("SELECT 1").execute(&state.db).await.is_ok();

    Json(json!({
        "status": "ok",
        "db_connected": db_ok,
        "app": "belajar-bahasa-backend",
        "version": "0.3.0"
    }))
}

/// Belum ada sistem login/register. Supaya folder & materi tetap punya
/// pemilik (kolom user_id NOT NULL), server otomatis pakai 1 user "default"
/// yang dibuat sekali saat pertama kali server jalan.
async fn get_or_create_default_user(pool: &sqlx::PgPool) -> Uuid {
    if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM users ORDER BY created_at ASC LIMIT 1",
    )
    .fetch_optional(pool)
    .await
    .expect("Gagal query tabel users")
    {
        return id;
    }

    sqlx::query_scalar::<_, Uuid>(
        r#"
        INSERT INTO users (username, email, bahasa_sumber_default, bahasa_target_default)
        VALUES ('default_user', 'default@local', 'ja', 'id')
        RETURNING id
        "#,
    )
    .fetch_one(pool)
    .await
    .expect("Gagal membuat default user")
}
