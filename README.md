# Kasir Onga V.1 — Android

Proyek Android untuk **Kasir Onga V.1**: aplikasi kasir (POS) berbasis web yang dibungkus
menjadi aplikasi Android (WebView), lengkap dengan tombol **Cetak Struk** yang memakai
sistem cetak Android (bisa memilih printer Bluetooth/WiFi yang terhubung ke HP).

## Isi proyek

- `app/src/main/java/id/onga/kasir/AppConfig.kt` — **satu-satunya file yang wajib diedit**:
  ganti `WEB_APP_URL` dengan alamat aplikasi web Kasir Onga kamu.
- `app/src/main/java/id/onga/kasir/MainActivity.kt` — WebView + tombol cetak + bridge
  `window.OngaAndroid.print()` untuk halaman web.
- `.github/workflows/build-apk.yml` — GitHub Actions yang otomatis membuat APK setiap
  kali kamu push ke branch `main`.

## Langkah 1 — Ganti alamat web

Buka `app/src/main/java/id/onga/kasir/AppConfig.kt`, ganti:

```kotlin
const val WEB_APP_URL = "https://alamat-web-onga-kamu.com/"
```

dengan alamat tempat aplikasi web Kasir Onga di-deploy (mis. Vercel, Netlify, atau server sendiri).

## Langkah 2 — Push ke GitHub dari Termux

Di Termux (folder hasil ekstrak ZIP ini):

```bash
pkg install git gh
git init
git add .
git commit -m "Kasir Onga V.1"
gh repo create kasir-onga --private --source=. --push
```

Atau buat repo kosong dulu di github.com, lalu:

```bash
git init
git add .
git commit -m "Kasir Onga V.1"
git branch -M main
git remote add origin https://github.com/USERNAME/kasir-onga.git
git push -u origin main
```

## Langkah 3 — Ambil APK-nya

Setelah push, buka repo di GitHub → tab **Actions** → run "Build APK Kasir Onga" →
di bagian **Artifacts** unduh `kasir-onga-debug-apk` → di dalamnya ada `app-debug.apk`
yang siap dipasang di HP Android (izinkan "install dari sumber tidak dikenal" bila diminta).

Setiap push berikutnya ke `main` akan membuat APK baru secara otomatis.

## Catatan

- APK ini adalah versi *debug* untuk pemakaian sendiri/toko. Untuk rilis resmi
  (Play Store) perlu signing key sendiri — bisa ditambahkan nanti.
- Cetak struk: tombol printer di kanan atas, atau dari halaman web lewat
  `window.OngaAndroid.print()`. Printer thermal Bluetooth harus sudah terhubung
  (paired) ke HP dan mendukung layanan cetak Android, atau gunakan aplikasi
  layanan cetak dari merek printer kamu.
- Tautan WhatsApp dari dalam aplikasi otomatis dibuka di aplikasi WhatsApp asli.
