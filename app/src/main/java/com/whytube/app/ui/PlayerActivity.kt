package com.whytube.app.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.whytube.app.R
import com.whytube.app.api.InvidiousApi
import com.whytube.app.api.VideoDetails
import com.whytube.app.api.VideoItem
import com.whytube.app.api.VideoStream
import com.whytube.app.databinding.ActivityPlayerBinding
import com.whytube.app.utils.FavoritesManager
import com.whytube.app.utils.MemoryOptimizer
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var relatedAdapter: VideoAdapter

    private var currentVideo: VideoItem? = null
    private var videoDetails: VideoDetails? = null
    private var isAudioOnlyMode = false
    private var currentStream: VideoStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoritesManager = FavoritesManager(this)

        @Suppress("DEPRECATION")
        currentVideo = intent.getSerializableExtra("VIDEO_ITEM") as? VideoItem

        if (currentVideo == null) {
            Toast.makeText(this, "Video tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupInitialUi()
        setupPlayer()
        setupRelatedList()
        setupButtons()

        loadDetails(currentVideo!!.id)
    }

    private fun setupInitialUi() {
        val video = currentVideo ?: return
        binding.tvPlayerTitle.text = video.title
        binding.tvPlayerStats.text = "${video.uploaderName} • ${video.viewsFormatted}"
        binding.tvPlayerChannelName.text = video.uploaderName
        updateBookmarkIcon(favoritesManager.isFavorite(video.id))
    }

    private fun setupPlayer() {
        // Gunakan player teroptimasi RAM kecil
        exoPlayer = MemoryOptimizer.createLowRamExoPlayer(this).apply {
            binding.playerView.player = this
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> binding.playerLoading.visibility = View.VISIBLE
                        Player.STATE_READY -> binding.playerLoading.visibility = View.GONE
                        Player.STATE_ENDED -> binding.playerLoading.visibility = View.GONE
                        Player.STATE_IDLE -> binding.playerLoading.visibility = View.GONE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    binding.playerLoading.visibility = View.GONE
                    Toast.makeText(
                        this@PlayerActivity,
                        "Error memutar stream: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun setupRelatedList() {
        relatedAdapter = VideoAdapter(onVideoClick = { selectedVideo ->
            currentVideo = selectedVideo
            setupInitialUi()
            loadDetails(selectedVideo.id)
        })

        binding.rvRelatedVideos.layoutManager = LinearLayoutManager(this)
        binding.rvRelatedVideos.adapter = relatedAdapter
    }

    private fun setupButtons() {
        // Toggle Audio-Only Mode (Menghemat 80% RAM & Baterai)
        binding.btnToggleAudioMode.setOnClickListener {
            toggleAudioMode()
        }

        // Quality Selector
        binding.btnQuality.setOnClickListener {
            showQualityDialog()
        }

        // Bookmark
        binding.btnPlayerBookmark.setOnClickListener {
            currentVideo?.let { video ->
                val newState = favoritesManager.toggleFavorite(video)
                updateBookmarkIcon(newState)
                val msg = if (newState) "Tersimpan ke favorit" else "Dihapus dari favorit"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // PiP (Picture-in-Picture / Layar Mengambang)
        binding.btnPiP.setOnClickListener {
            enterPipMode()
        }
    }

    private fun loadDetails(videoId: String) {
        binding.playerLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = InvidiousApi.getVideoDetails(videoId)
            binding.playerLoading.visibility = View.GONE

            result.onSuccess { details ->
                videoDetails = details
                binding.tvPlayerTitle.text = details.title
                binding.tvPlayerStats.text = "${details.viewsFormatted} • ${details.publishedTime}"
                binding.tvPlayerChannelName.text = details.uploaderName
                binding.tvPlayerSubscribers.text = details.subscriberCount
                binding.tvDescription.text = if (details.description.isNotEmpty()) {
                    details.description
                } else {
                    "Tidak ada deskripsi tersedia."
                }

                relatedAdapter.submitList(details.relatedVideos)

                // Pilih stream awal yang paling hemat (misal 360p)
                val bestStream = details.streams.find { it.quality.contains("360p") }
                    ?: details.streams.firstOrNull { !it.isAudioOnly }
                    ?: details.streams.firstOrNull()

                bestStream?.let { playStream(it) }
            }.onFailure { err ->
                Toast.makeText(
                    this@PlayerActivity,
                    "Gagal memuat info video: ${err.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun playStream(stream: VideoStream) {
        currentStream = stream
        binding.btnQuality.text = stream.quality

        val currentPosition = exoPlayer?.currentPosition ?: 0L
        val mediaItem = MediaItem.fromUri(stream.url)
        exoPlayer?.setMediaItem(mediaItem, currentPosition)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun toggleAudioMode() {
        val details = videoDetails ?: return
        isAudioOnlyMode = !isAudioOnlyMode

        if (isAudioOnlyMode) {
            // Aktifkan audio-only: sembunyikan rendering video untuk hemat RAM & GPU
            binding.layoutAudioOnly.visibility = View.VISIBLE
            binding.btnToggleAudioMode.text = "Mode Video"

            val audioStream = details.streams.find { it.isAudioOnly }
            if (audioStream != null) {
                playStream(audioStream)
            }
            Toast.makeText(this, "Mode Audio Aktif - Sangat hemat RAM & data", Toast.LENGTH_SHORT).show()
        } else {
            binding.layoutAudioOnly.visibility = View.GONE
            binding.btnToggleAudioMode.text = "Mode Audio"

            val videoStream = details.streams.find { it.quality.contains("360p") }
                ?: details.streams.firstOrNull { !it.isAudioOnly }

            videoStream?.let { playStream(it) }
        }
    }

    private fun showQualityDialog() {
        val streams = videoDetails?.streams ?: return
        if (streams.isEmpty()) return

        val qualityLabels = streams.map { it.quality }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih Resolusi (Hemat RAM)")
            .setItems(qualityLabels) { _, which ->
                val selected = streams[which]
                playStream(selected)
            }
            .show()
    }

    private fun updateBookmarkIcon(isFav: Boolean) {
        binding.btnPlayerBookmark.setImageResource(
            if (isFav) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
        )
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal masuk mode PiP: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Fitur PiP membutuhkan Android 8.0 ke atas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Otomatis masuk mode PiP saat tombol Home ditekan jika video sedang diputar
        if (!isAudioOnlyMode && exoPlayer?.isPlaying == true) {
            enterPipMode()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Sembunyikan elemen UI lainnya untuk menghemat RAM dan menjaga layar floating bersih
            binding.scrollDetails.visibility = View.GONE
            binding.playerView.useController = false
            binding.playerContainer.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            // Kembalikan ke tampilan semula saat floating player diperbesar
            binding.scrollDetails.visibility = View.VISIBLE
            binding.playerView.useController = true
            val density = resources.displayMetrics.density
            binding.playerContainer.layoutParams.height = (220 * density).toInt()
        }
    }

    override fun onPause() {
        super.onPause()
        val isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isAudioOnlyMode && !isInPip) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Sangat penting untuk low RAM: langsung rilis decoder & memori ExoPlayer
        exoPlayer?.release()
        exoPlayer = null
    }
}
