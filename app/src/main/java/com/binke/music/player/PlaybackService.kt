package com.binke.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
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

            val app = application as? BinkeMusicApp
            val musicPlayer = app?.musicPlayer
            val sharedPlayer = musicPlayer?.attachSessionPlayer()
            val activePlayer = sharedPlayer ?: player

            mediaSession = MediaSession.Builder(this, activePlayer)
                .setSessionActivity(createPendingIntent())
                .build()
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
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this@PlaybackService,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "binke_music_playback"
    }
}
