package com.whytube.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InvidiousApi {

    // Daftar instance publik Invidious yang stabil dengan fallback otomatis
    private val INSTANCES = listOf(
        "https://inv.tux.pizza",
        "https://invidious.nerdvpn.de",
        "https://invidious.jing.rocks",
        "https://yt.drgnz.club"
    )

    private var currentInstanceIndex = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getBaseUrl(): String = INSTANCES[currentInstanceIndex % INSTANCES.size]

    private fun switchInstance() {
        currentInstanceIndex = (currentInstanceIndex + 1) % INSTANCES.size
    }

    /**
     * Mengambil daftar video trending
     */
    suspend fun getTrending(type: String = ""): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (i in INSTANCES.indices) {
            try {
                val queryParam = if (type.isNotEmpty()) "?type=$type" else ""
                val url = "${getBaseUrl()}/api/v1/trending$queryParam"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val jsonArray = JSONArray(body)
                    val list = mutableListOf<VideoItem>()

                    for (j in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(j)
                        if (obj.optString("type") == "video" || !obj.has("type")) {
                            list.add(parseVideoItem(obj))
                        }
                    }
                    return@withContext Result.success(list)
                } else {
                    switchInstance()
                }
            } catch (e: Exception) {
                lastException = e
                switchInstance()
            }
        }
        Result.failure(lastException ?: Exception("Gagal memuat video trending"))
    }

    /**
     * Mencari video berdasarkan kata kunci
     */
    suspend fun search(query: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (i in INSTANCES.indices) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "${getBaseUrl()}/api/v1/search?q=$encodedQuery&type=video"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val jsonArray = JSONArray(body)
                    val list = mutableListOf<VideoItem>()

                    for (j in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(j)
                        if (obj.optString("type") == "video") {
                            list.add(parseVideoItem(obj))
                        }
                    }
                    return@withContext Result.success(list)
                } else {
                    switchInstance()
                }
            } catch (e: Exception) {
                lastException = e
                switchInstance()
            }
        }
        Result.failure(lastException ?: Exception("Gagal melakukan pencarian"))
    }

    /**
     * Mengambil detail dan URL streaming video
     */
    suspend fun getVideoDetails(videoId: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (i in INSTANCES.indices) {
            try {
                val url = "${getBaseUrl()}/api/v1/videos/$videoId"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val obj = JSONObject(body)

                    val streams = mutableListOf<VideoStream>()

                    // FormatStreams (Video + Audio tergabung, sangat ringan untuk didecode)
                    val formatStreams = obj.optJSONArray("formatStreams")
                    if (formatStreams != null) {
                        for (j in 0 until formatStreams.length()) {
                            val streamObj = formatStreams.getJSONObject(j)
                            val streamUrl = streamObj.optString("url")
                            val quality = streamObj.optString("qualityLabel", streamObj.optString("resolution"))
                            val mime = streamObj.optString("type")
                            if (streamUrl.isNotEmpty()) {
                                streams.add(VideoStream(streamUrl, quality, mime, isAudioOnly = false))
                            }
                        }
                    }

                    // AdaptiveFormats untuk Audio Only mode (Hemat 80% RAM & Kuota!)
                    val adaptiveFormats = obj.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (j in 0 until adaptiveFormats.length()) {
                            val streamObj = adaptiveFormats.getJSONObject(j)
                            val mime = streamObj.optString("type")
                            if (mime.startsWith("audio/")) {
                                val streamUrl = streamObj.optString("url")
                                val bitrate = "${streamObj.optInt("bitrate") / 1000} kbps"
                                if (streamUrl.isNotEmpty()) {
                                    streams.add(VideoStream(streamUrl, "Audio ($bitrate)", mime, isAudioOnly = true))
                                }
                            }
                        }
                    }

                    // Related videos
                    val relatedList = mutableListOf<VideoItem>()
                    val recommended = obj.optJSONArray("recommendedVideos")
                    if (recommended != null) {
                        for (j in 0 until recommended.length()) {
                            val relObj = recommended.getJSONObject(j)
                            relatedList.add(parseVideoItem(relObj))
                        }
                    }

                    val details = VideoDetails(
                        id = videoId,
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        uploaderName = obj.optString("author"),
                        subscriberCount = obj.optString("subCountText", ""),
                        viewsFormatted = formatViews(obj.optLong("viewCount")),
                        publishedTime = obj.optString("publishedText", ""),
                        streams = streams,
                        relatedVideos = relatedList
                    )
                    return@withContext Result.success(details)
                } else {
                    switchInstance()
                }
            } catch (e: Exception) {
                lastException = e
                switchInstance()
            }
        }
        Result.failure(lastException ?: Exception("Gagal memuat data video"))
    }

    private fun parseVideoItem(obj: JSONObject): VideoItem {
        val id = obj.optString("videoId")
        val title = obj.optString("title")
        val author = obj.optString("author")
        val lengthSeconds = obj.optInt("lengthSeconds", 0)
        val viewCount = obj.optLong("viewCount", 0L)
        val publishedText = obj.optString("publishedText")

        // Cari thumbnail resolusi sedang/rendah (hemat memori)
        val thumbnails = obj.optJSONArray("videoThumbnails")
        var thumbUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        if (thumbnails != null && thumbnails.length() > 0) {
            thumbUrl = thumbnails.getJSONObject(0).optString("url", thumbUrl)
        }

        return VideoItem(
            id = id,
            title = title,
            uploaderName = author,
            durationFormatted = formatDuration(lengthSeconds),
            viewsFormatted = formatViews(viewCount),
            uploadedFormatted = publishedText,
            thumbnailUrl = thumbUrl
        )
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1f jt penonton", views / 1_000_000.0)
            views >= 1_000 -> String.format("%d rb penonton", views / 1_000)
            views > 0 -> "$views penonton"
            else -> ""
        }
    }
}
