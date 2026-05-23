package com.binke.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.binke.music.BinkeMusicApp
import com.binke.music.MainActivity

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val player: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            // 复用 Application 中的 ExoPlayer（保持单一实例，UI 状态一致）
            val app = application as? BinkeMusicApp
            val sharedPlayer = try {
                app?.musicPlayer?.attachSessionPlayer()
            } catch (e: Exception) {
                Log.w(TAG, "attachSessionPlayer failed, using local player", e)
                null
            }
            val activePlayer = sharedPlayer ?: player

            mediaSession = MediaSession.Builder(this, activePlayer)
                .setSessionActivity(createPendingIntent())
                .setCallback(MediaButtonCallback())
                .build()

            Log.d(TAG, "MediaSession created, player=${if (sharedPlayer != null) "shared" else "local"}")
        } catch (e: Exception) {
            Log.e(TAG, "PlaybackService.onCreate failed", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "彬可音乐",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "音乐播放控制"
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "createNotificationChannel failed", e)
            }
        }
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this@PlaybackService, MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this@PlaybackService,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * 拦截媒体按钮事件，转发给 ViewModel 处理。
     * 返回 true 消费事件，阻止默认行为（ExoPlayer 响应 + 启动其他音乐 App）。
     */
    private inner class MediaButtonCallback : MediaSession.Callback {
        override fun onMediaButtonEvent(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaButtonEvent: Intent
        ): Boolean {
            val cb = BinkeMediaCallbacks.callback
            if (cb == null) {
                Log.w(TAG, "MediaButtonEvent but callback not registered, consuming")
                return true  // 消费事件，不让汽水音乐接收到
            }

            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return false

            // 只处理 DOWN 事件，避免重复触发
            if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                return true
            }

            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    cb.onMediaPlay()
                }
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    cb.onMediaNext()
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    cb.onMediaPrevious()
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    cb.onMediaStop()
                }
                else -> {
                    Log.d(TAG, "Unhandled media button keyCode=${keyEvent.keyCode}")
                    return false
                }
            }

            return true  // 消费事件，阻止 ExoPlayer 和其他 App 处理
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "binke_music_playback"
    }
}
