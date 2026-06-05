package com.binke.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.binke.music.BinkeMusicApp
import com.binke.music.MainActivity
import com.binke.music.R
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes

/**
 * 媒体播放服务（minSdk 19 兼容 — ExoPlayer 2.19.1 + MediaSessionCompat）。
 *
 * 与 Media3 不同，这里不继承 MediaSessionService（那是 androidx.media3.session.MediaSessionService，
 * minSdk 21+），而是继承 android.app.Service，自行持有 MediaSessionCompat：
 *
 * - 启动时优先复用 Application 内的 [MusicPlayer] 实例（通过 [MusicPlayer.attachSessionPlayer]），
 *   保证前台 UI 和锁屏 / 通知栏看到的是同一 ExoPlayer；
 * - 借不到时则自己 new 一个独立的 ExoPlayer，并在 onDestroy 中 release；
 * - 接收系统的 MEDIA_BUTTON intent（来自耳机按键 / 蓝牙控制器），
 *   转给 [mediaSessionCallback]，由 callback 把事件路由到 [BinkeMediaCallbacks]
 *   → MainActivity → ViewModel 控制 MusicPlayer。
 */
class PlaybackService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    /** true = 用的是 service 自己 new 的 ExoPlayer，onDestroy 必须 release；false = 借的 MusicPlayer 的，不能 release */
    private var serviceOwnsPlayer = false
    private var player: ExoPlayer? = null

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            val cb = BinkeMediaCallbacks.callback
            if (cb == null) {
                Log.w(TAG, "onPlay but callback not registered")
                return
            }
            cb.onMediaPlay()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }

        override fun onPause() {
            val cb = BinkeMediaCallbacks.callback
            if (cb == null) return
            cb.onMediaPause()
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        }

        override fun onSkipToNext() {
            val cb = BinkeMediaCallbacks.callback
            if (cb == null) return
            cb.onMediaNext()
            updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
        }

        override fun onSkipToPrevious() {
            val cb = BinkeMediaCallbacks.callback
            if (cb == null) return
            cb.onMediaPrevious()
            updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS)
        }

        override fun onStop() {
            val cb = BinkeMediaCallbacks.callback
            if (cb == null) return
            cb.onMediaStop()
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            if (BinkeMediaCallbacks.callback == null) {
                Log.w(TAG, "MediaButtonEvent but callback not registered, consuming")
                return true  // 消费事件，不让汽水音乐接收到
            }
            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return false
            // 只处理 DOWN 事件，避免重复触发
            if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                return true
            }
            return handleMediaKey(keyEvent)
        }
    }

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
            if (sharedPlayer != null) {
                player = sharedPlayer
                serviceOwnsPlayer = false
            } else {
                player = createLocalPlayer()
                serviceOwnsPlayer = true
            }

            mediaSession = MediaSessionCompat(this, SESSION_TAG).apply {
                setCallback(mediaSessionCallback)
                isActive = true
            }
            updateMetadataFromPlayer()

            // 启动前台服务（Android 8.0+ startForegroundService 要求 5 秒内 startForeground）
            startForeground(NOTIFICATION_ID, createPlaceholderNotification())

            Log.d(TAG, "MediaSession created, player=${if (sharedPlayer != null) "shared" else "local"}, owns=$serviceOwnsPlayer")
        } catch (e: Exception) {
            Log.e(TAG, "PlaybackService.onCreate failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 当系统通过 MEDIA_BUTTON action 直接拉起服务时，
        // 事件走 MediaSession 之前先在这里处理一下。
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null) handleMediaKey(keyEvent)
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w(TAG, "mediaSession release failed", e)
        }
        mediaSession = null
        // 只 release 自己创建的 player；借来的 MusicPlayer 的 ExoPlayer 绝对不能 release
        if (serviceOwnsPlayer) {
            try {
                player?.release()
                Log.d(TAG, "released service-owned player")
            } catch (e: Exception) {
                Log.w(TAG, "local player release failed", e)
            }
        } else {
            Log.d(TAG, "shared player, not releasing from service")
        }
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createLocalPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    private fun updateMetadataFromPlayer() {
        val app = application as? BinkeMusicApp
        val meta = app?.musicPlayer?.getCurrentMediaItem()
        if (meta != null) {
            mediaSession?.setMetadata(meta)
        }
    }

    private fun updatePlaybackState(stateCode: Int) {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(stateCode, 0L, 1.0f)
            .build()
        mediaSession?.setPlaybackState(state)
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
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // 批 4: API 23+ 用 FLAG_IMMUTABLE；19-22 用 FLAG_UPDATE_CURRENT（无 IMMUTABLE 标记）
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            flags
        )
    }

    private fun createPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("彬可音乐")
            .setContentText("媒体控制已就绪")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(createPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 把媒体按键分发到 BinkeMediaCallbacks。
     * 返回值：
     *   true  = 已处理（消费事件，阻止 ExoPlayer / 其他 App 响应）
     *   false = 未识别 keyCode（不消费，让 ExoPlayer 处理）
     *
     * 同时被 onStartCommand（MEDIA_BUTTON intent 路径）和
     * mediaSessionCallback.onMediaButtonEvent（MediaSession 路径）调用，
     * 避免两处重复维护按键映射。
     */
    private fun handleMediaKey(keyEvent: KeyEvent): Boolean {
        val cb = BinkeMediaCallbacks.callback
        if (cb == null) {
            Log.w(TAG, "MEDIA_BUTTON received but callback not registered (activity not running)")
            return true
        }
        return when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                cb.onMediaPlay(); true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                cb.onMediaNext(); true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                cb.onMediaPrevious(); true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                cb.onMediaStop(); true
            }
            else -> {
                Log.d(TAG, "Unhandled media button keyCode=${keyEvent.keyCode}")
                false
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "binke_music_playback"
        private const val NOTIFICATION_ID = 1
        private const val SESSION_TAG = "BinkeMusicSession"
    }
}
