package com.whytube.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.whytube.app.R
import com.whytube.app.api.VideoItem
import com.whytube.app.databinding.ItemVideoBinding
import com.whytube.app.utils.FavoritesManager

class VideoAdapter(
    private val onVideoClick: (VideoItem) -> Unit,
    private val onBookmarkClick: ((VideoItem, Boolean) -> Unit)? = null
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(DiffCallback) {

    private var favoritesManager: FavoritesManager? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        if (favoritesManager == null) {
            favoritesManager = FavoritesManager(parent.context)
        }
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem) {
            binding.tvTitle.text = item.title

            val metaBuilder = StringBuilder()
            if (item.uploaderName.isNotEmpty()) metaBuilder.append(item.uploaderName)
            if (item.viewsFormatted.isNotEmpty()) {
                if (metaBuilder.isNotEmpty()) metaBuilder.append(" • ")
                metaBuilder.append(item.viewsFormatted)
            }
            if (item.uploadedFormatted.isNotEmpty()) {
                if (metaBuilder.isNotEmpty()) metaBuilder.append(" • ")
                metaBuilder.append(item.uploadedFormatted)
            }
            binding.tvMeta.text = metaBuilder.toString()

            if (item.durationFormatted.isNotEmpty()) {
                binding.tvDuration.visibility = View.VISIBLE
                binding.tvDuration.text = item.durationFormatted
            } else {
                binding.tvDuration.visibility = View.GONE
            }

            // Load thumbnail hemat memori dengan Coil
            binding.ivThumbnail.load(item.thumbnailUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_whytube_logo)
                error(R.drawable.ic_whytube_logo)
            }

            val isFav = favoritesManager?.isFavorite(item.id) == true
            updateBookmarkIcon(isFav)

            binding.btnBookmark.setOnClickListener {
                favoritesManager?.let { favMgr ->
                    val newState = favMgr.toggleFavorite(item)
                    updateBookmarkIcon(newState)
                    onBookmarkClick?.invoke(item, newState)
                }
            }

            binding.root.setOnClickListener {
                onVideoClick(item)
            }
        }

        private fun updateBookmarkIcon(isFav: Boolean) {
            binding.btnBookmark.setImageResource(
                if (isFav) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
            )
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
            return oldItem == newItem
        }
    }
}
