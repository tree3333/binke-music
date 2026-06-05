package com.binke.music.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.binke.music.R
import com.binke.music.data.model.Song

/**
 * 搜索结果列表适配器（ListAdapter + DiffUtil）。
 *
 * item 布局 [R.layout.item_search_result]：
 *  - 56dp 高度
 *  - 左侧 48dp×48dp 圆角 ImageView 封面
 *  - 中间 上下两行 TextView（歌名 + 歌手）
 *  - 右侧 ImageButton 播放按钮
 *
 * 点击回调：
 *  - [onClick]      整行点击 (用于跳到播放页 / 设置当前播放列表)
 *  - [onPlayClick]  播放按钮独立点击 (与整行点击行为一致，由调用方决定是否区分)
 *
 * 注：MainViewModel.playSong(song) 在歌曲不在当前播放列表中时会把 _playlist 设为单首，
 * 正好对应"搜索结果点击"场景，因此回调直接传 Song 即可。
 */
class SearchResultAdapter(
    private val onClick: (Song) -> Unit,
    private val onPlayClick: (Song) -> Unit
) : ListAdapter<Song, SearchResultAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view, onClick, onPlayClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onClick: (Song) -> Unit,
        private val onPlayClick: (Song) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val cover: ImageView = view.findViewById(R.id.cover)
        private val name: TextView = view.findViewById(R.id.song_name)
        private val artist: TextView = view.findViewById(R.id.song_artist)
        private val btnPlay: ImageButton = view.findViewById(R.id.btn_play)

        private var current: Song? = null

        init {
            // 整行点击 → 交给调用方
            view.setOnClickListener {
                current?.let { onClick(it) }
            }
            // 播放按钮独立点击 → 单独回调 (避免与整行 click 事件冒泡冲突)
            btnPlay.setOnClickListener {
                current?.let { onPlayClick(it) }
            }
        }

        fun bind(song: Song) {
            current = song
            name.text = song.name
            artist.text = song.artist
            val url = song.pic.ifEmpty { PLACEHOLDER_URL }
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
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
                oldItem == newItem
        }
    }
}
