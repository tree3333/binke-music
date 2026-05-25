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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 简单音乐播放器
 */
class MusicPlayer(private val context: Context) {

    data class ProbeResult(
        val statusCode: Int,
        val contentType: String?,
        val contentLength: Long,
        val finalUrl: String,
        val errorMessage: String? = null
    )

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    var onPositionChanged: ((Long) -> Unit)? = null
    var onDurationChanged: ((Long) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null
    var onTrackEnded: (() -> Unit)? = null

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
                })
            }

        handler.post(progressRunnable)
    }

    fun probe(url: String): ProbeResult {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()
        return try {
            probeClient.newCall(request).execute().use { response ->
                ProbeResult(
                    statusCode = response.code,
                    contentType = response.header("Content-Type"),
                    contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L,
                    finalUrl = response.request.url.toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "probe() failed for url=$url", e)
            ProbeResult(
                statusCode = -1,
                contentType = null,
                contentLength = -1L,
                finalUrl = url,
                errorMessage = e.message
            )
        }
    }

    fun play(song: Song) {
        val p = player ?: run {
            Log.e(TAG, "play() called but player is null — initialize() was not called!")
            return
        }

        // 尝试在现有播放列表中找这首歌（通过 musicRid 匹配）
        var targetIndex = -1
        for (i in 0 until p.mediaItemCount) {
            val existing = p.getMediaItemAt(i)
            // 用 localConfiguration?.uri 的 fragment 或 query 参数中的 rid 来匹配
            val uri = existing.localConfiguration?.uri?.toString() ?: ""
            if (song.playUrl?.isNotEmpty() == true && uri == song.playUrl) {
                targetIndex = i
                break
            }
        }

        val newItem = MediaItem.Builder()
            .setUri(song.playUrl ?: "")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.artist)
                    .setArtworkUri(if (song.pic.isNotEmpty()) android.net.Uri.parse(song.pic) else null)
                    .build()
            )
            .build()

        if (targetIndex >= 0) {
            // 歌已在播放列表中：原地更新 URL + 元数据，seek 到该位置播放
            p.replaceMediaItem(targetIndex, newItem)
            p.seekTo(targetIndex, 0)
            p.playWhenReady = true
        } else {
            // 歌不在列表中（单曲播放场景）：替换整个列表
            p.stop()
            p.clearMediaItems()
            p.setMediaItem(newItem)
            p.prepare()
            p.playWhenReady = true
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

    fun setMetadata(name: String, artist: String, artUri: String) {
        player?.let { p ->
            if (p.mediaItemCount > 0) {
                val currentItem = p.getMediaItemAt(p.currentMediaItemIndex)
                val uri = currentItem.localConfiguration?.uri
                if (uri != null) {
                    val updatedItem = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(name)
                                .setArtist(artist)
                                .setArtworkUri(if (artUri.isNotEmpty()) android.net.Uri.parse(artUri) else null)
                                .build()
                        )
                        .build()
                    p.replaceMediaItem(p.currentMediaItemIndex, updatedItem)
                }
            }
        }
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
     * 同步播放列表到 ExoPlayer（供 MediaSession 显示上一首/下一首按钮）。
     * 第一个参数为 Song 对象列表，第二个参数为当前播放索引。
     */
    fun setPlaylist(songs: List<Song>, currentIndex: Int) {
        player?.let { p ->
            if (songs.isEmpty()) return
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setUri(song.playUrl ?: "")
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
            // 不在这里 prepare 和 play，由 play() 方法负责
        }
    }

    /**
     * 更新当前播放位置（用于上一首/下一首逻辑）。
     */
    fun setCurrentIndex(index: Int) {
        player?.seekToDefaultPosition(index)
    }

    companion object {
        private const val TAG = "MusicPlayer"
    }
}
