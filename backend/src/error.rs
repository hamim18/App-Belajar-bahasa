use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;

/// Error terpadu untuk semua handler.
/// Setiap varian otomatis diubah jadi response JSON dengan status code yang sesuai.
pub enum AppError {
    NotFound(String),
    BadRequest(String),
    Internal(String),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            AppError::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
            AppError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
        };

        tracing::error!("{}", message);

        (status, Json(json!({ "error": message }))).into_response()
    }
}

impl From<sqlx::Error> for AppError {
    fn from(e: sqlx::Error) -> Self {
        match e {
            sqlx::Error::RowNotFound => AppError::NotFound("Data tidak ditemukan".to_string()),
            sqlx::Error::Database(db_err) => {
                // Foreign key violation (misal folder_id tidak ada) -> 400, bukan 500
                if db_err.is_foreign_key_violation() {
                    AppError::BadRequest(
                        "Referensi tidak valid (folder/materi terkait tidak ditemukan)"
                            .to_string(),
                    )
                } else {
                    AppError::Internal(format!("Database error: {db_err}"))
                }
            }
            _ => AppError::Internal(format!("Database error: {e}")),
        }
    }
}

impl From<std::io::Error> for AppError {
    fn from(e: std::io::Error) -> Self {
        AppError::Internal(format!("IO error: {e}"))
    }
}
