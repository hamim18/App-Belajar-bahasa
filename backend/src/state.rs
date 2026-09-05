use sqlx::PgPool;
use uuid::Uuid;

/// State global yang dibagikan ke semua handler.
/// `default_user_id` dipakai sementara karena sistem login/auth belum dibuat.
/// Semua folder & materi yang dibuat lewat API akan "dimiliki" oleh user ini.
#[derive(Clone)]
pub struct AppState {
    pub db: PgPool,
    pub default_user_id: Uuid,
}
