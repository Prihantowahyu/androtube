package com.whytube.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.whytube.app.api.VideoItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan bookmark lokal yang sangat ringan tanpa library database berat.
 */
class FavoritesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("whytube_favorites", Context.MODE_PRIVATE)

    fun isFavorite(videoId: String): Boolean {
        return prefs.contains(videoId)
    }

    fun toggleFavorite(video: VideoItem): Boolean {
        return if (isFavorite(video.id)) {
            removeFavorite(video.id)
            false
        } else {
            addFavorite(video)
            true
        }
    }

    fun addFavorite(video: VideoItem) {
        val json = JSONObject().apply {
            put("id", video.id)
            put("title", video.title)
            put("uploaderName", video.uploaderName)
            put("duration", video.durationFormatted)
            put("views", video.viewsFormatted)
            put("uploaded", video.uploadedFormatted)
            put("thumbnail", video.thumbnailUrl)
        }
        prefs.edit().putString(video.id, json.toString()).apply()
    }

    fun removeFavorite(videoId: String) {
        prefs.edit().remove(videoId).apply()
    }

    fun getAllFavorites(): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val allEntries = prefs.all
        for ((_, value) in allEntries) {
            try {
                val json = JSONObject(value as String)
                list.add(
                    VideoItem(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        uploaderName = json.getString("uploaderName"),
                        durationFormatted = json.optString("duration"),
                        viewsFormatted = json.optString("views"),
                        uploadedFormatted = json.optString("uploaded"),
                        thumbnailUrl = json.getString("thumbnail")
                    )
                )
            } catch (e: Exception) {
                // Ignore corrupted item
            }
        }
        return list
    }
}
