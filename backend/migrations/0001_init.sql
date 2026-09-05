-- Extension untuk gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================
-- 1. TABEL USER
-- ============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE,
    bahasa_sumber_default VARCHAR(5) DEFAULT 'ja',
    bahasa_target_default VARCHAR(5) DEFAULT 'id',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- ============================================
-- 2. TABEL FOLDER
-- ============================================
CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nama_folder VARCHAR(255) NOT NULL,
    bahasa_sumber VARCHAR(5),
    bahasa_target VARCHAR(5),
    parent_id UUID REFERENCES folders(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_folders_user_id ON folders(user_id);
CREATE INDEX idx_folders_parent_id ON folders(parent_id);

-- ============================================
-- 3. TABEL MATERI
-- ============================================
CREATE TABLE materi (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    judul VARCHAR(255) NOT NULL,
    bahasa_sumber VARCHAR(5) NOT NULL,
    bahasa_target VARCHAR(5) NOT NULL,
    file_pdf VARCHAR(500),
    total_halaman INT DEFAULT 0,
    cover_image VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_materi_folder_id ON materi(folder_id);
CREATE INDEX idx_materi_bahasa ON materi(bahasa_sumber, bahasa_target);

-- ============================================
-- 4. TABEL DAFTAR ISI (Structure)
-- ============================================
CREATE TABLE daftar_isi (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    materi_id UUID NOT NULL REFERENCES materi(id) ON DELETE CASCADE,
    halaman_awal INT NOT NULL,
    halaman_akhir INT NOT NULL,
    level INT DEFAULT 1,
    parent_id UUID REFERENCES daftar_isi(id) ON DELETE CASCADE,
    urutan INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_daftar_isi_materi_id ON daftar_isi(materi_id);
CREATE INDEX idx_daftar_isi_parent_id ON daftar_isi(parent_id);
CREATE INDEX idx_daftar_isi_halaman ON daftar_isi(halaman_awal, halaman_akhir);

-- ============================================
-- 5. TABEL DAFTAR ISI JUDUL (Multibahasa)
-- ============================================
CREATE TABLE daftar_isi_judul (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    daftar_isi_id UUID NOT NULL REFERENCES daftar_isi(id) ON DELETE CASCADE,
    bahasa VARCHAR(5) NOT NULL,
    judul TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(daftar_isi_id, bahasa)
);
CREATE INDEX idx_daftar_isi_judul_bahasa ON daftar_isi_judul(bahasa);

-- ============================================
-- 6. TABEL KAMUS (Master)
-- ============================================
CREATE TABLE kamus (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kata_asli VARCHAR(255) NOT NULL,
    bahasa_sumber VARCHAR(5) NOT NULL,
    is_custom BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(kata_asli, bahasa_sumber)
);
CREATE INDEX idx_kamus_kata_asli ON kamus(kata_asli);
CREATE INDEX idx_kamus_bahasa_sumber ON kamus(bahasa_sumber);

-- ============================================
-- 7. TABEL TERJEMAHAN KAMUS
-- ============================================
CREATE TABLE kamus_terjemahan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kata_id UUID NOT NULL REFERENCES kamus(id) ON DELETE CASCADE,
    bahasa_target VARCHAR(5) NOT NULL,
    terjemahan TEXT NOT NULL,
    reading VARCHAR(255),
    tipe_kata VARCHAR(50),
    contoh_kalimat TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(kata_id, bahasa_target)
);
CREATE INDEX idx_kamus_terjemahan_bahasa_target ON kamus_terjemahan(bahasa_target);

-- ============================================
-- 8. TABEL KOSAKATA KONTEKS
-- ============================================
CREATE TABLE kosakata_konteks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kata_id UUID NOT NULL REFERENCES kamus(id) ON DELETE CASCADE,
    materi_id UUID NOT NULL REFERENCES materi(id) ON DELETE CASCADE,
    bab_id UUID REFERENCES daftar_isi(id) ON DELETE CASCADE,
    halaman INT NOT NULL,
    catatan_pribadi TEXT,
    folder_kustom VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(kata_id, materi_id, bab_id, halaman)
);
CREATE INDEX idx_kosakata_kata_id ON kosakata_konteks(kata_id);
CREATE INDEX idx_kosakata_materi_id ON kosakata_konteks(materi_id);
CREATE INDEX idx_kosakata_bab_id ON kosakata_konteks(bab_id);
CREATE INDEX idx_kosakata_halaman ON kosakata_konteks(halaman);
CREATE INDEX idx_kosakata_composite ON kosakata_konteks(materi_id, bab_id, halaman);

-- ============================================
-- 9. TABEL PROGRESS BACA
-- ============================================
CREATE TABLE progress_baca (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    materi_id UUID NOT NULL REFERENCES materi(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    halaman_terakhir INT DEFAULT 0,
    progress_persen INT DEFAULT 0,
    last_read_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(materi_id, user_id)
);
CREATE INDEX idx_progress_user_materi ON progress_baca(user_id, materi_id);

-- ============================================
-- 10. TABEL BOOKMARK
-- ============================================
CREATE TABLE bookmarks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    materi_id UUID NOT NULL REFERENCES materi(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    halaman INT NOT NULL,
    catatan TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_bookmarks_user_materi ON bookmarks(user_id, materi_id);
