package com.whytube.app.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloadHelper {

    fun downloadMedia(
        context: Context,
        title: String,
        streamUrl: String,
        isAudioOnly: Boolean
    ) {
        try {
            val extension = if (isAudioOnly) ".m4a" else ".mp4"
            val sanitizedTitle = title
                .replace(Regex("[^a-zA-Z0-9.-]"), "_")
                .take(50) + extension

            val uri = Uri.parse(streamUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle(title)
                setDescription("Mengunduh dengan WhyTube Lite")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitizedTitle)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            manager?.enqueue(request)

            val mediaType = if (isAudioOnly) "Audio" else "Video"
            Toast.makeText(
                context,
                "Mengunduh $mediaType di latar belakang (Cek Notifikasi)",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Gagal mengunduh: ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
