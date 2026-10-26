package com.whytube.app

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class yang mengkonfigurasi image loader & cache
 * agar sangat hemat RAM (RGB_565 & batas memori ketat).
 */
class WhyTubeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Menggunakan RGB_565 menghemat 50% RAM dibandingkan ARGB_8888 standar
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Batasi cache RAM maksimal 15% dari memori aplikasi
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB di disk (bukan di RAM)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
