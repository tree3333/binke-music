package com.binke.music.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import com.binke.music.data.model.PlayMode
import com.binke.music.data.model.Song
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes

/**
 * 简单音乐播放器
 *
 * 基于 ExoPlayer 2.19.1（最后支持 minSdk 16 的版本，向下兼容到 API 19）。
 * ExoPlayer 实例在本类内部创建，并可通过 [attachSessionPlayer] 共享给 [PlaybackService]
 * 供 MediaSessionCompat 使用。
 *
 * UI 状态通过一系列 [onPositionChanged] / [onIsPlayingChanged] / [onPlaybackStateChanged]
 * 等 lambda 回调推送给 ViewModel。播放控制接口形状在 Batch 2 重写后保持稳定，
 * ViewModel 端的调用方不受 ExoPlayer 2.x 内部 API 变动影响。
 */
class MusicPlayer(private val context: Context) {

    private var player: ExoPlayer? = null
    private var isPlaylistSet = false
    private val handler = Handler(Looper.getMainLooper())

    /** 缓存 index → Song，用于 getCurrentMediaItem() 时构造 MediaMetadataCompat */
    private val currentSongMap = HashMap<Int, Song>()

    /** 暴露给 ViewModel 用于恢复时同步状态（返回 MediaMetadataCompat 供 MediaSessionCompat 使用） */
    fun getCurrentMediaItem(): MediaMetadataCompat? {
        val p = player ?: return null
        val idx = p.currentMediaItemIndex
        val song = currentSongMap[idx] ?: return null
        return buildMediaMetadata(song)
    }

    fun getCurrentMediaItemIndex(): Int = player?.currentMediaItemIndex ?: 0

    var onPositionChanged: ((Long) -> Unit)? = null
    var onDurationChanged: ((Long) -> Unit)? = null
    var onIsPlayingChanged: ((Boolean) -> Unit)? = null
    var onMediaItemTransition: ((Int) -> Unit)? = null
    var onTrackEnded: (() -> Unit)? = null
    /** 参数是 PlaybackStateCompat state code (e.g. STATE_PLAYING / STATE_PAUSED / STATE_BUFFERING) */
    var onPlaybackStateChanged: ((Int) -> Unit)? = null
    /** 参数是 error message */
    var onPlayerError: ((String) -> Unit)? = null

    /**
     * 注入切歌时的 URL 提供器（由 ViewModel 设置）。
     * 当 ExoPlayer 内部 transition 到下一首（自动播完 / 锁屏上一首下一首按钮）时，
     * 回调此函数用 mediaId 解析出真实播放地址，再 replaceMediaItem 替换。
     */
    var urlProvider: ((String) -> String?)? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.playbackState == Player.STATE_READY) {
                    onPositionChanged?.invoke(p.currentPosition)
                    if (p.duration > 0) {
                        onDurationChanged?.invoke(p.duration)
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            when (state) {
                Player.STATE_READY -> {
                    onDurationChanged?.invoke(p.duration)
                    // 同步 playback state：READY + isPlaying → STATE_PLAYING
                    onPlaybackStateChanged?.invoke(
                        if (p.isPlaying) PlaybackStateCompat.STATE_PLAYING
                        else PlaybackStateCompat.STATE_PAUSED
                    )
                }
                Player.STATE_BUFFERING -> {
                    onPlaybackStateChanged?.invoke(PlaybackStateCompat.STATE_BUFFERING)
                }
                Player.STATE_ENDED -> {
                    onPlaybackStateChanged?.invoke(PlaybackStateCompat.STATE_STOPPED)
                    onTrackEnded?.invoke()
                }
                Player.STATE_IDLE -> {
                    onPlaybackStateChanged?.invoke(PlaybackStateCompat.STATE_NONE)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onIsPlayingChanged?.invoke(isPlaying)
            val p = player ?: return
            if (p.playbackState == Player.STATE_READY) {
                onPlaybackStateChanged?.invoke(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED
                )
            }
        }

        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
            // ExoPlayer 2.19.1: Player.Listener.onPlayerError(PlaybackException) 是 base type
            // 实际传入的多为 ExoPlaybackException，按 error.errorCodeName + message 上报即可
            val codeName = (error as? ExoPlaybackException)?.errorCodeName ?: error.errorCodeName
            Log.e(TAG, "onPlayerError code=$codeName msg=${error.message}", error)
            onPlayerError?.invoke("[$codeName] ${error.message}")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // AUTO（自动播完切下一首）+ SEEK（锁屏/通知栏上一首/下一首按钮）都要处理
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                return
            }
            val p = player ?: return
            if (p.mediaItemCount <= 1) return
            val newIndex = p.currentMediaItemIndex
            val newMediaId = mediaItem?.mediaId
            if (!newMediaId.isNullOrEmpty()) {
                val cachedUrl = urlProvider?.invoke(newMediaId)
                if (!cachedUrl.isNullOrEmpty()) {
                    val updatedItem = p.getMediaItemAt(newIndex)
                        .buildUpon()
                        .setUri(cachedUrl)
                        .build()
                    p.replaceMediaItem(newIndex, updatedItem)
                }
            }
            onMediaItemTransition?.invoke(newIndex)
        }
    }

    /**
     * 初始化 ExoPlayer。
     *
     * @param context 用于创建 ExoPlayer 的 Context
     * @param cachedPlayer 可选：传入已创建好的 ExoPlayer 实例（由 PlaybackService 共享）。
     *   传 null 则内部 new 一个。共享场景下 [release] 不会 release cachedPlayer。
     */
    fun initialize(context: Context, cachedPlayer: ExoPlayer? = null) {
        if (player != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val activePlayer = cachedPlayer ?: ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }

        activePlayer.addListener(playerListener)
        player = activePlayer
        handler.post(progressRunnable)
    }

    /** 播放/暂停切换（MediaButton PLAY/PAUSE 走这里） */
    fun playPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    fun next() {
        player?.next()
    }

    fun previous() {
        player?.previous()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun currentPosition(): Long = player?.currentPosition ?: 0

    fun duration(): Long = player?.duration ?: 0

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun resume() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    /**
     * 初始化播放列表（供锁屏上一首/下一首使用）。
     * 内部通过 [urlProvider] 解析每个 mediaId 对应的 URL。
     * startIndex 歌曲的 URL 必须已预取完毕，否则该 MediaItem 的 URI 为空，
     * ExoPlayer 准备时会失败 — 调用方应保证 urlProvider 对 startIndex 媒体项返回非空 URL。
     *
     * @param songs 完整歌曲列表
     * @param startIndex 开始播放的索引
     * @param startPositionMs 起始位置（毫秒），默认 0
     */
    fun setPlaylist(songs: List<Song>, startIndex: Int, startPositionMs: Long = 0L) {
        val p = player ?: run {
            Log.e(TAG, "setPlaylist() called but player is null — initialize() was not called!")
            return
        }
        if (songs.isEmpty()) return
        isPlaylistSet = true
        currentSongMap.clear()
        songs.forEachIndexed { i, song -> currentSongMap[i] = song }

        val mediaItems = songs.mapIndexed { i, song -> buildMediaItem(song, i) }
        p.setMediaItems(mediaItems, startIndex, startPositionMs)
        p.prepare()
        p.playWhenReady = true
    }

    /** 切换播放模式（单曲循环 / 列表循环 / 随机） */
    fun setPlayMode(mode: PlayMode) {
        player?.repeatMode = when (mode) {
            PlayMode.SINGLE_LOOP -> Player.REPEAT_MODE_ONE
            PlayMode.LIST_LOOP, PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * 接入 MediaSession 系统，供 PlaybackService 共享 ExoPlayer。
     * 必须在 [initialize] 之后调用。
     */
    fun attachSessionPlayer(): ExoPlayer {
        return player ?: throw IllegalStateException("call initialize() first")
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        player?.let { p ->
            p.removeListener(playerListener)
            p.release()
        }
        player = null
        currentSongMap.clear()
    }

    private fun buildMediaItem(song: Song, @Suppress("UNUSED_PARAMETER") index: Int): MediaItem {
        val url = urlProvider?.invoke(song.id)
        val uri = if (!url.isNullOrBlank()) Uri.parse(url) else Uri.EMPTY
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMimeType("audio/mpeg")
            .setMediaMetadata(
                com.google.android.exoplayer2.MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artist)
                    .setArtworkUri(if (song.pic.isNotEmpty()) Uri.parse(song.pic) else null)
                    .build()
            )
            .build()
    }

    private fun buildMediaMetadata(song: Song): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, song.name)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, song.artist)
        if (song.pic.isNotEmpty()) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.pic)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, song.pic)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "MusicPlayer"
    }
}
