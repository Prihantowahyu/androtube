# 📺 WhyTube Lite (Ultra-Low RAM Android Client)

**WhyTube Lite** adalah aplikasi alternatif pemutar YouTube native Android yang dirancang khusus untuk perangkat berspesifikasi rendah (**RAM 1GB - 2GB / Android Go Edition**). 

Aplikasi ini menggunakan API terbuka (*Invidious & Piped*), bebas iklan, tidak membutuhkan Google Play Services yang berat, dan dilengkapi fitur otomatisasi **GitHub Actions** untuk langsung mengubah kode menjadi file **APK siap pakai** di cloud.

---

## ⚡ Fitur Khusus untuk HP RAM Kecil (1GB - 2GB)

1. **Buffer Memori Terbatas (Low Buffer Cap)**:
   - Media3 / ExoPlayer standar mengalokasikan buffer video hingga 50 MB di RAM.
   - Di WhyTube Lite, buffer dikunci maksimal **4 MB** (`MemoryOptimizer.kt`), mencegah HP *freeze* atau aplikasi *force close*.
2. **Pengolahan Gambar RGB_565**:
   - Thumbnail video di-load dengan format warna `RGB_565` (menghemat memori hingga 50% dibanding ARGB_8888 biasa).
   - Cache memori dibatasi ketat maksimal 15% dari total sisa heap memori.
3. **Mode Audio Saja (Hemat 80% RAM & Baterai)**:
   - Satu tombol untuk mematikan rendering video dan beralih ke stream audio ringan.
   - Menghemat baterai, kuota internet, dan membebaskan RAM ponsel.
4. **Pilihan Kualitas Hemat (144p / 360p)**:
   - Resolusi default disetel ke 360p yang ramah untuk prosesor dan RAM lawas.
5. **Penyimpanan Lokal Super Ringan**:
   - Fitur bookmark / simpan video favorit menggunakan JSON ringan tanpa database SQLite/Room yang memakan RAM di background.

---

## 🚀 Cara Mengupload ke GitHub & Mendapatkan File APK

Anda **TIDAK PERLU** menginstall Android Studio atau Java di komputer Anda! GitHub Actions yang akan meng-compile dan mengubah source code ini menjadi file `.apk` secara otomatis.

### Langkah 1: Inisialisasi Git dan Push ke Repository GitHub Anda

Buka Terminal / PowerShell di folder proyek ini (`c:\xampp\htdocs\whytube`):

```bash
git init
git add .
git commit -m "Initial commit: WhyTube Lite Android App"
git branch -M main
git remote add origin https://github.com/USERNAME-ANDA/whytube.git
git push -u origin main
```
*(Ganti `USERNAME-ANDA` dengan username akun GitHub Anda).*

---

### Langkah 2: Download File APK dari GitHub

1. Buka repository GitHub Anda di browser: `https://github.com/USERNAME-ANDA/whytube`
2. Klik tab **Actions** di bagian atas repository.
3. Anda akan melihat proses build sedang berjalan dengan nama **"Build WhyTube Android APK"**.
4. Tunggu sekitar 2 - 3 menit sampai statusnya berwarna centang hijau (✔ **Success**).
5. Klik pada hasil build tersebut.
6. Scroll ke bagian bawah ke bagian **Artifacts**.
7. Klik **WhyTube-Lite-APK** untuk mendownload file `.zip` yang berisi file `WhyTube-Lite-Debug.apk`.
8. Ekstrak dan kirim file `.apk` tersebut ke HP Android Anda, lalu install dan nikmati!

---

## 🛠️ Struktur Proyek

```
whytube/
├── .github/workflows/build-apk.yml  # Otomatisasi konversi ke APK di GitHub
├── app/
│   ├── build.gradle.kts             # Dependensi minimal & ringan
│   ├── proguard-rules.pro           # Aturan optimasi & kompresi ukuran APK
│   └── src/main/
│       ├── AndroidManifest.xml      # Izin jaringan & orientasi
│       ├── java/com/whytube/app/
│       │   ├── WhyTubeApp.kt        # Konfigurasi memori Coil (RGB_565)
│       │   ├── api/
│       │   │   ├── InvidiousApi.kt  # Client API YouTube bebas iklan & tracking
│       │   │   └── Models.kt        # Data model video & stream
│       │   ├── ui/
│       │   │   ├── MainActivity.kt  # Halaman pencarian & trending
│       │   │   ├── PlayerActivity.kt# Pemutar video + Mode Audio Saja
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
Dilisensikan di bawah [MIT License](LICENSE). Bebas dimodifikasi dan dikembangkan.
