package com.whytube.app.api

import java.io.Serializable

data class VideoItem(
    val id: String,
    val title: String,
    val uploaderName: String,
    val durationFormatted: String,
    val viewsFormatted: String,
    val uploadedFormatted: String,
    val thumbnailUrl: String
) : Serializable

data class VideoStream(
    val url: String,
    val quality: String,
    val mimeType: String,
    val isAudioOnly: Boolean = false
)

data class VideoDetails(
    val id: String,
    val title: String,
    val description: String,
    val uploaderName: String,
    val subscriberCount: String,
    val viewsFormatted: String,
    val publishedTime: String,
    val streams: List<VideoStream>,
    val relatedVideos: List<VideoItem>
)
