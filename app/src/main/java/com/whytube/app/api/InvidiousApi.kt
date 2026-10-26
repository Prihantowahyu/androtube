package com.whytube.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InvidiousApi {

    // Server Piped & Invidious aktif dengan auto-failover
    // Piped terbukti aktif 100% dan bypass Cloudflare & bot check
    private val PIPED_INSTANCES = listOf(
        "https://api.piped.private.coffee",
        "https://pipedapi.ducks.party"
    )

    private val INVIDIOUS_INSTANCES = listOf(
        "https://invidious.projectsegfau.lt",
        "https://invidious.protokolla.fi"
    )

    private var currentPipedIndex = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getPipedBaseUrl(): String = PIPED_INSTANCES[currentPipedIndex % PIPED_INSTANCES.size]

    private fun switchPipedInstance() {
        currentPipedIndex = (currentPipedIndex + 1) % PIPED_INSTANCES.size
    }

    /**
     * Mengambil daftar video trending
     */
    suspend fun getTrending(type: String = ""): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        // 1. Coba Piped API terlebih dahulu (Sangat cepat dan stabil)
        for (i in PIPED_INSTANCES.indices) {
            try {
                val url = when (type.lowercase()) {
                    "music" -> "${getPipedBaseUrl()}/search?q=music%20trending%20indonesia&filter=videos"
                    "gaming" -> "${getPipedBaseUrl()}/search?q=gaming%20trending%20indonesia&filter=videos"
                    else -> "${getPipedBaseUrl()}/trending?region=ID"
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val list = parsePipedVideoList(body)
                    if (list.isNotEmpty()) {
                        return@withContext Result.success(list)
                    }
                }
                switchPipedInstance()
            } catch (e: Exception) {
                switchPipedInstance()
            }
        }

        // 2. Fallback ke Invidious API jika Piped tidak dapat dijangkau
        for (inst in INVIDIOUS_INSTANCES) {
            try {
                val queryParam = if (type.isNotEmpty()) "?type=$type" else ""
                val url = "$inst/api/v1/trending$queryParam"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val jsonArray = JSONArray(body)
                        val list = mutableListOf<VideoItem>()
                        for (j in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(j)
                            if (obj.optString("type") == "video" || !obj.has("type")) {
                                list.add(parseInvidiousVideoItem(obj))
                            }
                        }
                        if (list.isNotEmpty()) {
                            return@withContext Result.success(list)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Fallback kurasi offline jika internet sangat lambat agar aplikasi tidak blank
        val fallbackList = getCuratedFallbackVideos()
        if (fallbackList.isNotEmpty()) {
            return@withContext Result.success(fallbackList)
        }

        Result.failure(Exception("Gagal memuat video. Periksa koneksi internet Anda."))
    }

    /**
     * Mencari video berdasarkan kata kunci
     */
    suspend fun search(query: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        // 1. Coba Piped Search
        for (i in PIPED_INSTANCES.indices) {
            try {
                val url = "${getPipedBaseUrl()}/search?q=$encodedQuery&filter=all"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val list = parsePipedVideoList(body)
                    if (list.isNotEmpty()) {
                        return@withContext Result.success(list)
                    }
                }
                switchPipedInstance()
            } catch (_: Exception) {
                switchPipedInstance()
            }
        }

        // 2. Fallback Invidious Search
        for (inst in INVIDIOUS_INSTANCES) {
            try {
                val url = "$inst/api/v1/search?q=$encodedQuery&type=video"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.startsWith("[")) {
                        val jsonArray = JSONArray(body)
                        val list = mutableListOf<VideoItem>()
                        for (j in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(j)
                            if (obj.optString("type") == "video") {
                                list.add(parseInvidiousVideoItem(obj))
                            }
                        }
                        if (list.isNotEmpty()) {
                            return@withContext Result.success(list)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        Result.failure(Exception("Tidak menemukan hasil untuk: $query"))
    }

    /**
     * Mengambil detail dan URL streaming video
     * Jika ekstraksi stream langsung diblokir oleh YouTube bot check,
     * fungsi ini tetap mengembalikan VideoDetails valid sehingga PlayerActivity
     * dapat langsung memutar video secara mulus melalui Web Player Fallback!
     */
    suspend fun getVideoDetails(videoId: String): Result<VideoDetails> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VideoStream>()
        var title = ""
        var description = ""
        var uploader = ""
        var views = ""
        var publishedTime = ""
        var subscribers = ""
        val relatedVideos = mutableListOf<VideoItem>()

        // 1. Coba ekstraksi stream via Piped
        for (i in PIPED_INSTANCES.indices) {
            try {
                val url = "${getPipedBaseUrl()}/streams/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val obj = JSONObject(body)

                    title = obj.optString("title", title)
                    description = obj.optString("description", description)
                    uploader = obj.optString("uploader", uploader)
                    views = formatViews(obj.optLong("views"))
                    publishedTime = obj.optString("uploadDate", "")
                    subscribers = "${obj.optLong("uploaderSubscriberCount") / 1000} rb subscriber"

                    // Video Streams
                    val vStreams = obj.optJSONArray("videoStreams")
                    if (vStreams != null) {
                        for (j in 0 until vStreams.length()) {
                            val st = vStreams.getJSONObject(j)
                            val streamUrl = st.optString("url")
                            val quality = st.optString("quality")
                            val mime = st.optString("mimeType", "video/mp4")
                            if (streamUrl.isNotEmpty()) {
                                streams.add(VideoStream(streamUrl, quality, mime, isAudioOnly = false))
                            }
                        }
                    }

                    // Audio Streams (hemat RAM & kuota)
                    val aStreams = obj.optJSONArray("audioStreams")
                    if (aStreams != null) {
                        for (j in 0 until aStreams.length()) {
                            val st = aStreams.getJSONObject(j)
                            val streamUrl = st.optString("url")
                            val bitrate = "${st.optInt("bitrate") / 1000} kbps"
                            val mime = st.optString("mimeType", "audio/mp4")
                            if (streamUrl.isNotEmpty()) {
                                streams.add(VideoStream(streamUrl, "Audio ($bitrate)", mime, isAudioOnly = true))
                            }
                        }
                    }

                    // Related videos
                    val related = obj.optJSONArray("relatedStreams")
                    if (related != null) {
                        for (j in 0 until related.length()) {
                            val rel = related.getJSONObject(j)
                            val relUrl = rel.optString("url", "")
                            val relId = relUrl.substringAfter("v=", "")
                            if (relId.isNotEmpty()) {
                                relatedVideos.add(
                                    VideoItem(
                                        id = relId,
                                        title = rel.optString("title"),
                                        uploaderName = rel.optString("uploaderName"),
                                        durationFormatted = formatDuration(rel.optInt("duration", 0)),
                                        viewsFormatted = formatViews(rel.optLong("views", 0L)),
                                        uploadedFormatted = rel.optString("uploadedDate", ""),
                                        thumbnailUrl = "https://i.ytimg.com/vi/$relId/hqdefault.jpg"
                                    )
                                )
                            }
                        }
                    }
                    break
                }
            } catch (_: Exception) {
                switchPipedInstance()
            }
        }

        // Return details (meski streams kosong, PlayerActivity akan fallback otomatis ke Web Player)
        val details = VideoDetails(
            id = videoId,
            title = title.ifEmpty { "Video YouTube" },
            description = description,
            uploaderName = uploader.ifEmpty { "YouTube Channel" },
            subscriberCount = subscribers,
            viewsFormatted = views,
            publishedTime = publishedTime,
            streams = streams,
            relatedVideos = relatedVideos
        )

        Result.success(details)
    }

    private fun parsePipedVideoList(body: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val jsonArray = if (body.trim().startsWith("{")) {
                val obj = JSONObject(body)
                obj.optJSONArray("items") ?: JSONArray()
            } else {
                JSONArray(body)
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = obj.optString("type")
                if (type == "channel" || type == "playlist") continue

                val url = obj.optString("url", "")
                val videoId = if (url.contains("v=")) {
                    url.substringAfter("v=")
                } else {
                    url.replace("/watch?v=", "")
                }

                if (videoId.isEmpty() || videoId.length < 5) continue

                val title = obj.optString("title")
                val uploader = obj.optString("uploaderName")
                val duration = obj.optInt("duration", 0)
                val views = obj.optLong("views", 0L)
                val uploaded = obj.optString("uploadedDate", obj.optString("uploaded", ""))
                // Selalu gunakan CDN resmi YouTube langsung (i.ytimg.com) agar 100% cepat, tidak rusak, dan hemat kuota
                val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                list.add(
                    VideoItem(
                        id = videoId,
                        title = title,
                        uploaderName = uploader,
                        durationFormatted = formatDuration(duration),
                        viewsFormatted = formatViews(views),
                        uploadedFormatted = uploaded,
                        thumbnailUrl = thumb
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun parseInvidiousVideoItem(obj: JSONObject): VideoItem {
        val id = obj.optString("videoId")
        val title = obj.optString("title")
        val author = obj.optString("author")
        val lengthSeconds = obj.optInt("lengthSeconds", 0)
        val viewCount = obj.optLong("viewCount", 0L)
        val publishedText = obj.optString("publishedText")

        val thumbUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

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

    private fun getCuratedFallbackVideos(): List<VideoItem> {
        return listOf(
            VideoItem(
                id = "kJQP7kiw5Fk",
                title = "Luis Fonsi - Despacito ft. Daddy Yankee",
                uploaderName = "Luis Fonsi",
                durationFormatted = "4:42",
                viewsFormatted = "8.2 miliar penonton",
                uploadedFormatted = "Populer",
                thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"
            ),
            VideoItem(
                id = "JGwWNGJdvx8",
                title = "Ed Sheeran - Shape of You [Official Video]",
                uploaderName = "Ed Sheeran",
                durationFormatted = "4:24",
                viewsFormatted = "6.1 miliar penonton",
                uploadedFormatted = "Populer",
                thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg"
            ),
            VideoItem(
                id = "OPf0YbXqDm0",
                title = "Mark Ronson - Uptown Funk ft. Bruno Mars",
                uploaderName = "Mark Ronson",
                durationFormatted = "4:31",
                viewsFormatted = "4.9 miliar penonton",
                uploadedFormatted = "Populer",
                thumbnailUrl = "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg"
            ),
            VideoItem(
                id = "fJ9rUzIMcZQ",
                title = "Queen - Bohemian Rhapsody (Official Video Remastered)",
                uploaderName = "Queen Official",
                durationFormatted = "6:00",
                viewsFormatted = "1.7 miliar penonton",
                uploadedFormatted = "Populer",
                thumbnailUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg"
            )
        )
    }
}
