package com.binke.music.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.binke.music.data.model.Song

/**
 * 简单音乐播放器
 */
class MusicPlayer(private val context: Context) {

    private var player: ExoPlayer? = null
    private var isPlaylistSet = false  // true = setPlaylist() 已调用，play() 不应清空列表
    private val handler = Handler(Looper.getMainLooper())

    /** 暴露给 ViewModel 用于恢复时同步状态 */
    fun getCurrentMediaItem(): MediaItem? = player?.currentMediaItem
    fun getCurrentMediaItemIndex(): Int = player?.currentMediaItemIndex ?: 0

    var onPositionChanged: ((Long) -> Unit)? = null
    var onDurationChanged: ((Long) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null
    var onTrackEnded: (() -> Unit)? = null
    /** MediaSession 直接切歌时触发（锁屏上一首/下一首按钮），返回新索引 */
    var onMediaItemTransition: ((Int) -> Unit)? = null

    /**
     * 注入切歌时的 URL 提供器（由 ViewModel 设置）。
     * onMediaItemTransition 触发时调用此函数获取新歌曲的 URL。
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

    fun initialize() {
        if (player != null) return

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                onDurationChanged?.invoke(duration)
                                onPlaybackStateChanged?.invoke(isPlaying)
                            }
                            Player.STATE_ENDED -> {
                                onPlaybackStateChanged?.invoke(false)
                                onTrackEnded?.invoke()
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onPlaybackStateChanged?.invoke(isPlaying)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "onPlayerError code=${error.errorCodeName} msg=${error.message}", error)
                        onPlaybackError?.invoke("[${error.errorCodeName}] ${error.message}")
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        // AUTO（自动播完切下一首）+ SEEK（锁屏/通知栏上一首/下一首按钮）都要处理
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                            player?.let { p ->
                                if (p.mediaItemCount > 1) {
                                    val newIndex = p.currentMediaItemIndex
                                    val newMediaId = mediaItem?.mediaId
                                    // 尝试用缓存 URL 替换（ZongTing 方式：URL 已在预取时缓存）
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
                        }
                    }
                })
            }

        handler.post(progressRunnable)
    }

    fun play(url: String) {
        val p = player ?: run {
            Log.e(TAG, "play() called but player is null — initialize() was not called!")
            return
        }

        if (isPlaylistSet && p.mediaItemCount > 0) {
            // 播放列表已存在，只更新当前项的 URL，保留 metadata（封面等）
            val idx = p.currentMediaItemIndex
            val currentItem = p.getMediaItemAt(idx)
            val updatedItem = MediaItem.Builder()
                .setUri(url)
                .setMimeType("audio/mpeg")
                .setMediaMetadata(currentItem.mediaMetadata)
                .build()
            p.replaceMediaItem(idx, updatedItem)
            p.prepare()
            p.playWhenReady = true
        } else {
            // 首次播放或无播放列表，清空并设置单个媒体项
            p.stop()
            p.clearMediaItems()
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMimeType("audio/mpeg")
                .build()
            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true
            isPlaylistSet = true
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun currentPosition(): Long = player?.currentPosition ?: 0

    fun duration(): Long = player?.duration ?: 0

    /**
     * 接入 MediaSession 系统，供 PlaybackService 共享 ExoPlayer。
     */
    fun attachSessionPlayer(): ExoPlayer {
        return player ?: throw IllegalStateException("call initialize() first")
    }

    fun release() {
        handler.removeCallbacks(progressRunnable)
        player?.release()
        player = null
    }

    fun setRepeatMode(mode: Int) {
        player?.repeatMode = mode
    }

    /**
     * 初始化播放列表（供锁屏上一首/下一首使用）。
     * 所有 URL 必须已预取完毕，不允许空 URI。
     * @param urls rid -> playUrl 的映射，必须包含所有歌曲的 URL
     */
    fun setPlaylist(songs: List<Song>, urls: Map<String, String>, currentIndex: Int) {
        player?.let { p ->
            if (songs.isEmpty()) return
            isPlaylistSet = true
            val mediaItems = songs.mapIndexed { i, song ->
                val url = urls[song.id] ?: ""
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(url)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(song.name)
                            .setArtist(song.artist)
                            .setArtworkUri(if (song.pic.isNotEmpty()) android.net.Uri.parse(song.pic) else null)
                            .build()
                    )
                    .build()
            }
            p.setMediaItems(mediaItems, currentIndex, 0)
            // 不在这里 prepare，由 play() 负责
        }
    }

    /**
     * 更新当前播放索引（用于上一首/下一首逻辑）。
     */
    fun setCurrentIndex(index: Int) {
        player?.seekToDefaultPosition(index)
    }

    companion object {
        private const val TAG = "MusicPlayer"
    }
}
