use axum::{routing::get, Json, Router};
use serde_json::json;
use sqlx::postgres::PgPoolOptions;
use std::net::SocketAddr;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;

#[derive(Clone)]
struct AppState {
    db: sqlx::PgPool,
}

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

    let state = AppState { db: pool };

    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    let app = Router::new()
        .route("/api/health", get(health_check))
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
        "version": "0.1.0"
    }))
}
