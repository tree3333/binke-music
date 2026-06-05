package com.binke.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.binke.music.R
import com.binke.music.data.model.Playlist

/**
 * 横向歌单卡片适配器（ListAdapter + DiffUtil）。
 *
 * item 布局 [R.layout.item_playlist_card]：120dp × 120dp 圆角封面 +
 * 歌单名（2 行 + ellipsize）+ 播放量/收录数（1 行）。
 *
 * 点击回调 [onClick] 由调用方注入（一般是 `viewModel.playPlaylist`）。
 */
class PlaylistCardAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistCardAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_card, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onClick: (Playlist) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val cover: ImageView = view.findViewById(R.id.cover)
        private val name: TextView = view.findViewById(R.id.name)
        private val meta: TextView = view.findViewById(R.id.meta)

        private var current: Playlist? = null

        init {
            view.setOnClickListener {
                current?.let { onClick(it) }
            }
        }

        fun bind(playlist: Playlist) {
            current = playlist
            name.text = playlist.name
            // 播放量 / 收录数 / 创建者 三选一展示，与 Compose 原版逻辑一致
            meta.text = when {
                playlist.total > 0 -> itemView.context.getString(
                    R.string.playlist_meta_total, playlist.total
                )
                playlist.listenCount.isNotBlank() -> itemView.context.getString(
                    R.string.playlist_meta_listen, playlist.listenCount
                )
                else -> playlist.creator.ifBlank {
                    itemView.context.getString(R.string.playlist_meta_default)
                }
            }
            val url = playlist.img.ifEmpty {
                PLACEHOLDER_URL
            }
            cover.load(url) {
                placeholder(R.color.surface)
                error(R.color.surface)
                crossfade(true)
            }
        }

        companion object {
            private const val PLACEHOLDER_URL =
                "https://via.placeholder.com/300/171717/F1F1F1?text=BinKe"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean =
                oldItem == newItem
        }
    }
}
