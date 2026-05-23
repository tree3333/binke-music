package com.binke.music.player

/**
 * 全局媒体控制器回调注册表。
 * 用于在 PlaybackService（系统启动）和 MainActivity（ViewModel 初始化）之间
 * 传递媒体按钮事件。
 *
 * 使用方式：
 *   注册：BinkeMediaCallbacks.callback = viewModel  （在 MainActivity.onCreate）
 *   注销：BinkeMediaCallbacks.callback = null      （在 MainActivity.onDestroy）
 *   读取：PlaybackService 读取此字段将事件路由到 ViewModel
 */
object BinkeMediaCallbacks {
    var callback: MediaControllerCallback? = null
}
