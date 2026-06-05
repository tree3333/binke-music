package com.binke.music.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.binke.music.R
import com.binke.music.data.model.PlayMode
import com.binke.music.data.model.Song
import com.binke.music.ui.views.LyricsView
import kotlinx.coroutines.launch

/**
 * 音乐 tab - 核心播放页 (替代 Compose MusicScreen.kt)
 * 批 2: 简化版，先做播放控制 + 进度条 + 歌词 + 封面
 */
class MusicFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as com.binke.music.BinkeMusicApp
        MainViewModelFactory(app, app.apiService, app.musicRepository, app.musicPlayer)
    }

    private var songName: TextView? = null
    private var songArtist: TextView? = null
    private var cover: ImageView? = null
    private var lyrics: LyricsView? = null
    private var progressSeek: SeekBar? = null
    private var timeCurrent: TextView? = null
    private var timeRemaining: TextView? = null
    private var btnPlayPause: ImageButton? = null
    private var btnPrevious: ImageButton? = null
    private var btnNext: ImageButton? = null
    private var btnPlayMode: ImageButton? = null
    private var btnFavorite: ImageButton? = null

    private var isSeekTracking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_music, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        songName = view.findViewById(R.id.song_name)
        songArtist = view.findViewById(R.id.song_artist)
        cover = view.findViewById(R.id.cover)
        lyrics = view.findViewById(R.id.lyrics)
        progressSeek = view.findViewById(R.id.progress_seek)
        timeCurrent = view.findViewById(R.id.time_current)
        timeRemaining = view.findViewById(R.id.time_remaining)
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        btnPrevious = view.findViewById(R.id.btn_previous)
        btnNext = view.findViewById(R.id.btn_next)
        btnPlayMode = view.findViewById(R.id.btn_play_mode)
        btnFavorite = view.findViewById(R.id.btn_favorite)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        btnPlayPause?.setOnClickListener { viewModel.playPause() }
        btnPrevious?.setOnClickListener { viewModel.previous() }
        btnNext?.setOnClickListener { viewModel.next() }
        btnPlayMode?.setOnClickListener { viewModel.togglePlayMode() }
        btnFavorite?.setOnClickListener {
            viewModel.currentSong.value?.let { viewModel.toggleFavorite(it) }
        }

        progressSeek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) { isSeekTracking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isSeekTracking = false
                val dur = viewModel.duration.value
                if (dur > 0) {
                    viewModel.seekTo((seekBar.progress.toLong() * dur / 1000L))
                }
            }
        })

        lyrics?.setOnLineClickListener { line ->
            viewModel.seekTo((line.time * 1000L).toLong())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentSong.collect { song ->
                        song?.let { bindSong(it) }
                    }
                }
                launch {
                    viewModel.isPlaying.collect { playing ->
                        btnPlayPause?.setImageResource(
                            if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
                        )
                    }
                }
                launch {
                    viewModel.currentPosition.collect { pos ->
                        if (!isSeekTracking) {
                            val dur = viewModel.duration.value
                            if (dur > 0) {
                                progressSeek?.progress = ((pos * 1000L) / dur).toInt()
                            }
                            timeCurrent?.text = formatTime(pos)
                            val remain = (dur - pos).coerceAtLeast(0L)
                            timeRemaining?.text = "剩余 ${formatTime(remain)}"
                        }
                        lyrics?.setCurrentPosition(pos)
                    }
                }
                launch {
                    viewModel.duration.collect { dur ->
                        // duration 由 currentPosition collector 推到 progress
                    }
                }
                launch {
                    viewModel.playMode.collect { mode ->
                        btnPlayMode?.setImageResource(
                            when (mode) {
                                PlayMode.LIST_LOOP -> R.drawable.ic_repeat
                                PlayMode.SINGLE_LOOP -> R.drawable.ic_repeat_one
                                PlayMode.SHUFFLE -> R.drawable.ic_shuffle
                            }
                        )
                    }
                }
                launch {
                    viewModel.lyrics.collect { lrc ->
                        lyrics?.setLyrics(lrc)
                    }
                }
                launch {
                    viewModel.coverColors.collect { colors ->
                        lyrics?.setColors(colors.pl, colors.nl)
                    }
                }
                launch {
                    viewModel.currentSong.collect { song ->
                        if (song != null) {
                            btnFavorite?.setImageResource(
                                if (viewModel.isFavorite(song.id)) R.drawable.ic_favorite_filled
                                else R.drawable.ic_favorite_border
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bindSong(song: Song) {
        songName?.text = song.name
        songArtist?.text = song.artist
        cover?.load(song.pic) {
            placeholder(R.color.surface)
            error(R.color.surface)
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        songName = null
        songArtist = null
        cover = null
        lyrics = null
        progressSeek = null
        timeCurrent = null
        timeRemaining = null
        btnPlayPause = null
        btnPrevious = null
        btnNext = null
        btnPlayMode = null
        btnFavorite = null
    }
}
