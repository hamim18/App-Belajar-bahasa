# Belajar Bahasa - Task 1: Skeleton Backend + Android

Panduan ini untuk verifikasi Task 1: memastikan pipeline backend (Rust) dan
Android bisa di-build & jalan di device kamu (Arch Linux CLI + HP Android fisik
via wireless debugging).

---

## A. Setup & Test Backend (Rust)

### 1. Install dependency (kalau belum ada)

```bash
# Rust (kalau belum ada rustup)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"

# PostgreSQL sudah kamu punya & jalan, tinggal buat database-nya
sudo -u postgres psql -c "CREATE DATABASE belajar_bahasa;"
```

Kalau user PostgreSQL kamu bukan `postgres` atau password beda, sesuaikan di step berikutnya.

### 2. Konfigurasi environment

```bash
cd backend
cp .env.example .env
nano .env   # sesuaikan DATABASE_URL dengan username/password/nama DB kamu
```

### 3. Build & jalankan

```bash
cargo build
```

Kalau ada error saat build, **copy full error message dan kirim ke saya** — kita perbaiki sebelum lanjut.

```bash
cargo run
```

Kalau sukses, akan muncul log seperti:
```
INFO belajar_bahasa_backend: Menghubungkan ke database...
INFO belajar_bahasa_backend: Menjalankan migrasi database...
INFO belajar_bahasa_backend: Server jalan di http://0.0.0.0:8080
```

### 4. Test endpoint health check

Buka terminal baru (biarkan `cargo run` tetap jalan):

```bash
curl http://localhost:8080/api/health
```

Harus muncul respons JSON seperti:
```json
{"status":"ok","db_connected":true,"app":"belajar-bahasa-backend","version":"0.1.0"}
```

✅ **Kalau ini berhasil, backend Task 1 dinyatakan sukses.**

### 5. Cari IP komputer kamu (dibutuhkan untuk test dari HP)

```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
```

Catat IP-nya (misal `192.168.1.10`) — nanti diinput di aplikasi Android.

---

## B. Setup & Test Android

### 1. Pastikan tools tersedia

```bash
gradle -v      # pastikan gradle terpasang
sdkmanager --list | head -20   # pastikan Android SDK terpasang
```

Pastikan environment variable berikut sudah di-set (biasanya di `.bashrc`/`.zshrc`):

```bash
export ANDROID_HOME=$HOME/Android/Sdk   # sesuaikan lokasi SDK kamu
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

Kalau `ANDROID_HOME` belum ada / SDK belum lengkap (butuh `platform-tools`, `platforms;android-34`,
`build-tools;34.0.0`), kabari saya — nanti saya bantu urutan instalasinya.

### 2. Build APK debug

```bash
cd android
gradle assembleDebug
```

Kalau ada error saat build, **copy full error message dan kirim ke saya**.

Kalau sukses, APK ada di:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Sambungkan HP via Wireless Debugging

Di HP: **Settings → Developer Options → Wireless debugging** → catat IP:Port yang muncul (misal `192.168.1.20:37251`).

Di komputer:
```bash
adb pair 192.168.1.20:PORT_PAIRING   # ikuti instruksi pairing code dari HP
adb connect 192.168.1.20:PORT_KONEKSI
adb devices   # pastikan HP muncul dengan status "device"
```

### 4. Install APK ke HP

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. Buka aplikasi di HP dan test

1. Buka app **"Belajar Bahasa"** di HP.
2. Pastikan HP dan komputer terhubung ke **WiFi yang sama**.
3. Isi field IP dengan IP komputer dari langkah A.5 (contoh: `192.168.1.10`).
4. Tap tombol **"Test Koneksi Backend"**.
5. Pastikan `cargo run` backend masih jalan di komputer.

✅ **Kalau muncul teks "✅ Terhubung!" beserta JSON response, Task 1 dinyatakan SELESAI TOTAL.**

❌ Kalau muncul error koneksi, cek:
- Backend masih jalan? (`curl http://localhost:8080/api/health` dari komputer harus tetap sukses)
- HP & komputer satu jaringan WiFi yang sama?
- Firewall di Arch Linux memblokir port 8080? Cek dengan: `sudo iptables -L` atau kalau pakai `ufw`: `sudo ufw allow 8080`

---

## Setelah Semua ✅

Balas ke saya konfirmasi hasil test (screenshot/log boleh dilampirkan kalau ada error).
Setelah Task 1 dikonfirmasi berhasil, kita lanjut ke Task 2 sesuai roadmap di `progress.md`.
