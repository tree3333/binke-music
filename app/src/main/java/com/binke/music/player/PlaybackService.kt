package com.binke.music.player

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 批 1 占位 Service - AndroidManifest 引用了 .player.PlaybackService
 * Batch 2 重写为 ExoPlayer 2.19.1 + MediaSessionCompat
 */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}
