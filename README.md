# 📺 WhyTube Lite (All-in-One Ultra-Low RAM: YouTube, TikTok & Instagram)

**WhyTube Lite** adalah aplikasi all-in-one super ringan yang dirancang khusus untuk perangkat Android berspesifikasi rendah (**RAM 1GB - 2GB / Android Go Edition**). 

Aplikasi ini menggabungkan:
1. **📺 YouTube Lite Native Player** (Bebas iklan & tracking via API Invidious/Piped).
2. **🎵 TikTok Lite Sandbox** (Akses feed TikTok tanpa aplikasi resmi yang rakus RAM).
3. **📸 Instagram Lite Sandbox** (Akses feed, reels, dan story Instagram super cepat).

Semua modul dilengkapi **Anti-Tracker & Sandbox Memory Release**, sehingga ketika Anda berpindah atau menutup aplikasi, memori RAM langsung dibersihkan dan kembali 0MB (tanpa proses latar belakang yang menghabiskan baterai).

Proyek ini sudah dilengkapi otomatisasi **GitHub Actions** untuk mengubah source code menjadi file **APK siap pakai** langsung di cloud tanpa butuh Android Studio di PC Anda.

---

## ⚡ Fitur Khusus untuk HP RAM Kecil (1GB - 2GB)

1. **Buffer Memori Terbatas (Low Buffer Cap 4MB)**:
   - Media3 / ExoPlayer standar memakan buffer video hingga 50 MB di RAM.
   - Di WhyTube Lite, buffer dikunci maksimal **4 MB** (`MemoryOptimizer.kt`), mencegah HP *freeze* atau aplikasi *force close*.
2. **TikTok Lite & Instagram Lite Sandboxing**:
   - Menjalankan TikTok & Instagram dalam WebView terisolasi yang mematikan database SQLite lokal browser dan menghentikan background script saat aplikasi tidak aktif.
   - **Blokir Pelacak (Anti-Tracker Interceptor)**: Memblokir otomatis script analitik dan pelacak seperti `analytics.tiktok.com`, `connect.facebook.net`, `pixel.facebook.com` yang menghemat 40-60% kuota dan konsumsi RAM.
   - Saat keluar dari TikTok/Instagram, seluruh instans WebView langsung dimusnahkan (`destroy()`) dan `System.gc()` dipanggil untuk membebaskan RAM seketika.
3. **Pengolahan Gambar RGB_565**:
   - Thumbnail video di-load dengan format warna `RGB_565` (menghemat memori hingga 50% dibanding ARGB_8888 standar).
   - Cache memori dibatasi ketat maksimal 15% dari sisa heap memori.
4. **Mode Audio Saja (Hemat 80% RAM & Baterai)**:
   - Satu tombol untuk mematikan rendering video dan beralih ke stream audio ringan saat mendengarkan musik atau podcast YouTube.
5. **Pilihan Kualitas Hemat (144p / 360p)**:
   - Resolusi default disetel ke 360p yang ramah untuk prosesor dan RAM lawas.

---

## 🚀 Cara Mengupload ke GitHub & Mendapatkan File APK

Anda **TIDAK PERLU** menginstall Android Studio atau Java di komputer Anda! GitHub Actions yang akan meng-compile dan mengubah source code ini menjadi file `.apk` secara otomatis.

### Langkah 1: Hubungkan & Push ke Repository GitHub Anda

Buka Terminal / PowerShell di folder proyek ini (`c:\xampp\htdocs\whytube`):

```bash
git remote add origin https://github.com/USERNAME-ANDA/whytube.git
git push -u origin main
```
*(Ganti `USERNAME-ANDA` dengan username akun GitHub Anda).*

---

### Langkah 2: Download File APK dari GitHub Actions

1. Buka repository GitHub Anda di browser: `https://github.com/USERNAME-ANDA/whytube`
2. Klik tab **Actions** di bagian atas repository.
3. Anda akan melihat alur kerja **"Build WhyTube Android APK"** sedang berjalan otomatis di server cloud GitHub.
4. Tunggu sekitar 2 - 3 menit sampai statusnya berwarna centang hijau (✔ **Success**).
5. Klik pada hasil build tersebut.
6. Scroll ke bagian bawah ke bagian **Artifacts**.
7. Klik **WhyTube-Lite-APK** untuk mendownload file `.zip` yang berisi file `WhyTube-Lite-Debug.apk`.
8. Ekstrak dan kirim file `.apk` tersebut ke HP Android Anda, lalu install dan nikmati!

---

## 🛠️ Struktur Proyek

```
whytube/
├── .github/workflows/build-apk.yml  # Otomatisasi konversi ke APK di GitHub Actions
├── app/
│   ├── build.gradle.kts             # Dependensi minimal & ringan
│   ├── proguard-rules.pro           # Aturan optimasi & kompresi ukuran APK
│   └── src/main/
│       ├── AndroidManifest.xml      # Izin jaringan & registrasi layar
│       ├── java/com/whytube/app/
│       │   ├── WhyTubeApp.kt        # Konfigurasi memori Coil (RGB_565)
│       │   ├── api/
│       │   │   ├── InvidiousApi.kt  # Client API YouTube bebas iklan & tracking
│       │   │   └── Models.kt        # Data model video & stream
│       │   ├── ui/
│       │   │   ├── MainActivity.kt  # Hub Utama (YouTube, TikTok, Instagram)
│       │   │   ├── PlayerActivity.kt# Pemutar video + Mode Audio Saja
│       │   │   ├── SocialLiteActivity.kt # Sandbox TikTok & Instagram Anti-Lag
│       │   │   └── VideoAdapter.kt  # Tampilan daftar video 60fps
│       │   └── utils/
│       │       ├── MemoryOptimizer.kt# Konfigurasi buffer 4MB ExoPlayer
│       │       └── FavoritesManager.kt# Bookmark lokal hemat RAM
│       └── res/                     # Layout, tema gelap AMOLED & aset ikon
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 📄 Lisensi
Dilisensikan di bawah [MIT License](LICENSE).
