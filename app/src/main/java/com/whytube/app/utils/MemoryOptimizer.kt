package com.whytube.app.utils

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Konfigurasi khusus untuk perangkat Android dengan RAM kecil (1GB - 2GB).
 * Membatasi ukuran buffer ExoPlayer agar tidak menghabiskan RAM perangkat.
 */
object MemoryOptimizer {

    fun createLowRamExoPlayer(context: Context): ExoPlayer {
        // Alokasi memori buffer maksimal 4 MB (Standar ExoPlayer bisa sampai 50MB!)
        val allocator = DefaultAllocator(true, 64 * 1024)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                /* minBufferMs = */ 2500,       // Hanya buffer 2.5 detik
                /* maxBufferMs = */ 6000,       // Maksimal buffer 6 detik
                /* bufferForPlaybackMs = */ 1000, // Mulai putar setelah 1 detik
                /* bufferForPlaybackAfterRebufferMs = */ 2000
            )
            .setTargetBufferBytes(4 * 1024 * 1024) // 4 MB buffer cap
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }
}
