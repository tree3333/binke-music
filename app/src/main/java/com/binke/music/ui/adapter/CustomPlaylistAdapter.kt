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
 * 自定义歌单横向卡片适配器（ListAdapter + DiffUtil）。
 *
 * item 布局 [R.layout.item_custom_playlist]：80dp × 80dp 圆角封面 + 1 行歌单名。
 * 用于 MineFragment "我创建的歌单" 区块的横向 RecyclerView。
 *
 * 点击回调 [onClick] 由调用方注入，一般是 viewModel.playCustomPlaylist(playlist)。
 *
 * 注意：本适配器与 [PlaylistCardAdapter] 的区别——
 *  - PlaylistCardAdapter：120×120 卡片 + 2 行文字，用于首页"每日推荐/热门榜单"
 *  - CustomPlaylistAdapter：80×80 卡片 + 1 行文字，用于 Mine tab "我创建的歌单"
 * 尺寸更小是因为 Mine tab 列表中包含"+"新建按钮，横向空间更紧凑。
 */
class CustomPlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, CustomPlaylistAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_playlist, parent, false)
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
        private val name: TextView = view.findViewById(R.id.playlist_name)

        private var current: Playlist? = null

        init {
            view.setOnClickListener {
                current?.let { onClick(it) }
            }
        }

        fun bind(playlist: Playlist) {
            current = playlist
            name.text = playlist.name
            // 自定义歌单可能没有封面（img 为空），用 placeholder URL 保证宽高稳定
            val url = playlist.img.ifEmpty { PLACEHOLDER_URL }
            cover.load(url) {
                placeholder(R.color.surface)
                error(R.color.surface)
                crossfade(true)
            }
        }

        companion object {
            private const val PLACEHOLDER_URL =
                "https://via.placeholder.com/300/171717/F1F1F1?text=PlayList"
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
