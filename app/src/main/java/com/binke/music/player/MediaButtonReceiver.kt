package com.binke.music.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 拦截系统 MEDIA_BUTTON 广播，转发给 PlaybackService 处理。
 *
 * 必须在 manifest 声明并设高优先级（+999），确保在所有其他 receiver 之前收到事件。
 * 收到后通过 startService 拉起 PlaybackService，由其 onStartCommand 处理按钮事件。
 * 这样即使应用未运行，方向盘按钮也能生效。
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        Log.d(TAG, "MediaButtonReceiver intercepted MEDIA_BUTTON, forwarding to PlaybackService")

        // 拉起 PlaybackService，把原始 intent 原封不动传过去
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = intent.action
            setData(intent.data)
            putExtras(intent)
            // START_FLAG_RETRY: 如果 service 还没创建，确保重试
            // 结合 PendingIntent.FLAG_UPDATE_CURRENT 确保 intent 最新
        }
        context.startService(serviceIntent)

        // 消费事件，阻止后续 receiver 处理（abortBroadcast 对 ordered broadcast 生效）
        abortBroadcast()
    }

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }
}
