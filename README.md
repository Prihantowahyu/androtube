# 📺 WhyTube Lite (All-in-One Ultra-Low RAM: YouTube, TikTok & Instagram)

**WhyTube Lite** adalah aplikasi multimedia super ringan yang dirancang khusus untuk perangkat Android berspesifikasi rendah (**RAM 1GB - 2GB / Android Go Edition**). 

Aplikasi ini menggabungkan:
1. **📺 YouTube Lite Native Player** (Bebas iklan & tracking via API terbuka).
2. **🎵 TikTok Lite Sandbox** (Akses feed TikTok tanpa aplikasi resmi yang rakus RAM).
3. **📸 Instagram Lite Sandbox** (Akses feed, reels, dan story Instagram super cepat).
4. **🖼️ Picture-in-Picture (PiP / PnP)** (Layar mengambang untuk multitasking sambil WhatsApp / browsing).
5. **💾 Unduh Video & Audio Offline** (Menggunakan DownloadManager bawaan sistem, 0% beban RAM).

---

## ⚡ Fitur Lengkap Khusus HP RAM Kecil (1GB - 2GB)

1. **Buffer Memori Terbatas (Low Buffer Cap 4MB)**:
   - Media3 / ExoPlayer standar memakan buffer video hingga 50 MB di RAM.
   - Di WhyTube Lite, buffer dikunci maksimal **4 MB** (`MemoryOptimizer.kt`), mencegah HP *freeze* atau aplikasi *force close*.
2. **Unduh Video & Audio Offline**:
   - Fitur download memanfaatkan `android.app.DownloadManager` bawaan sistem Android.
   - Proses download berjalan di service sistem Android, sehingga aplikasi WhyTube Lite memakai **0 MB RAM** untuk download. File tersimpan langsung di folder `Downloads`.
3. **Pengatur Kecepatan Putar (Playback Speed)**:
   - Pilihan kecepatan: `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`.
4. **Sleep Timer (Pengatur Waktu Tidur Otomatis)**:
   - Menyetel timer (15, 30, 45, 60 menit) agar pemutaran berhenti otomatis saat Anda tertidur, menjaga baterai dan kuota tidak terbuang.
5. **Gesture Swipe Kecerahan & Volume**:
   - Usap sisi kiri layar pemutar untuk mengatur kecerahan layar.
   - Usap sisi kanan layar pemutar untuk mengatur volume media.
6. **Preferensi Kualitas Hemat Kuota (Default 144p / 360p)**:
   - Menyimpan pilihan resolusi default sehingga video otomatis dimulai di resolusi hemat tanpa buffering lama.
7. **Mode Layar Mengambang (PiP / Picture-in-Picture)**:
   - Video otomatis mengecil menjadi jendela mengambang saat tombol Home ditekan.
8. **TikTok Lite & Instagram Lite Sandboxing**:
   - Menjalankan TikTok & Instagram dalam WebView terisolasi yang mematikan database SQLite lokal browser dan menghentikan background script saat aplikasi tidak aktif.
   - **Blokir Pelacak (Anti-Tracker Interceptor)**: Memblokir otomatis script analitik dan pelacak seperti `analytics.tiktok.com`, `connect.facebook.net`, `pixel.facebook.com` yang menghemat 40-60% kuota dan konsumsi RAM.
   - Saat keluar dari TikTok/Instagram, seluruh instans WebView langsung dimusnahkan (`destroy()`) dan `System.gc()` dipanggil untuk membebaskan RAM seketika.
9. **Pengolahan Gambar RGB_565**:
   - Thumbnail video di-load dengan format warna `RGB_565` (menghemat memori hingga 50% dibanding ARGB_8888 standar).

---

## 🚀 Cara Download File APK Terbaru dari GitHub Actions

Setiap kali kode di-push ke GitHub, sistem otomatis membuat file APK baru yang siap pakai:

1. Buka repository GitHub Anda di browser:
   👉 **[https://github.com/Prihantowahyu/androtube](https://github.com/Prihantowahyu/androtube)**
2. Klik tab **Actions** di menu atas:
   👉 **[https://github.com/Prihantowahyu/androtube/actions](https://github.com/Prihantowahyu/androtube/actions)**
3. Klik build terbaru yang berstatus centang hijau (✔ **Success**).
4. Scroll ke bagian bawah ke menu **Artifacts**.
5. Klik **WhyTube-Lite-APK** untuk mendownload file APK-nya, lalu pasang di HP Android Anda!

---

## 📄 Lisensi
Dilisensikan di bawah [MIT License](LICENSE).
