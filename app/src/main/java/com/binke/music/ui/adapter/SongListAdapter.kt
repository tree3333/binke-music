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
import com.binke.music.data.model.Song

/**
 * 歌曲行适配器（ListAdapter + DiffUtil）。
 *
 * item 布局 [R.layout.item_song_row]：60dp 高度，左 56dp×56dp 圆角 ImageView，右
 * 上下两行 TextView（歌名 + 歌手）。用于 MineFragment 的"我的收藏"和"播放历史"区块。
 *
 * 点击回调 [onClick] 由调用方注入，参数是点击的 [Song] 对象（Fragment 自行
 * 在回调中根据列表来源 + 索引调用 viewModel.playSongAt(index)）。
 */
class SongListAdapter(
    private val onClick: (Song) -> Unit
) : ListAdapter<Song, SongListAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_row, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onClick: (Song) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val cover: ImageView = view.findViewById(R.id.cover)
        private val name: TextView = view.findViewById(R.id.song_name)
        private val artist: TextView = view.findViewById(R.id.song_artist)

        private var current: Song? = null

        init {
            view.setOnClickListener {
                current?.let { onClick(it) }
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
