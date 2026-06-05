# 播放缓存重构：6+3 双队列 + 9 元组预加载

## 目标
把现在零散、容易漏的播放前预加载，改成"即将播放 6 首 + 已播放 3 首"双队列，**所有 9 个元素（歌名/歌手/音质/cover URL/真实 URL/歌词/bg/pl/nl）在切歌时就已就绪**。随机播放用 seed 算完整序列。歌词搜索加第 5 渠道 + 统一 3 次重试。无封面时用 EvoHermes 图兜底。

## 当前现状
- `SongCache.Entry` 只有 4 字段：`playUrl, pic, lyrics, coverBitmap`
- 用 `Map<String, Entry>` 按 song.id 索引；`evictOutsideWindow` 滑动窗口默认 size=3
- 歌词 4 渠道（酷我→网易云→QQ→lrclib），重试 2-3 次**不一致**
- 封面增强在 `MainViewModel.playSong` 内联（iTunes→网易云），不进缓存
- 颜色推理在 `triggerCoverPrediction` 独立协程，不进缓存
- 随机播放用 `indices.random()` — **无 seed、无序列**

## 关键设计

### 1. Entry 9 元组
```kotlin
data class FullEntry(
    val songId: String,
    val name: String,
    val artist: String,
    val quality: String,
    val picUrl: String?,           // 真实 URL（iTunes/网易云增强后），无则用 default_cover
    val playUrl: String?,          // 真实播放地址
    val lyrics: List<LrcLine>,     // 可能为空
    val coverColors: CoverColors,  // bg/pl/nl 三角
    val coverBitmap: Bitmap?       // 预解码的封面
)
data class CoverColors(val bg: Color, val pl: Color, val nl: Color)
```

### 2. 双队列（替代 Map 缓存）
```kotlin
class PlaybackCache {
    // 即将播放：当前播放的 = queue[0]，next = queue[1] … cap=6
    private val upcoming = ArrayDeque<FullEntry>(6)
    // 已播放：最近 3 首，给"上一首"用 cap=3
    private val history = ArrayDeque<FullEntry>(3)

    fun current(): FullEntry? = upcoming.firstOrNull()
    fun next(): FullEntry? = upcoming.getOrNull(1)
    fun takeUpcoming(): FullEntry  // 取出 queue[0]，移入 history 头
    fun pushToHistory(e: FullEntry)
    fun seed(entries: List<FullEntry>)  // playSong 时填充队列
}
```

### 3. 9 元组装配器（负责把现有 9 步获取合并）
```kotlin
suspend fun assembleEntry(song: Song): FullEntry = coroutineScope {
    val picDeferred = async { getPicWithRetry(song) }   // iTunes→网易云→默认封面
    val playUrlDeferred = async { getPlayUrlWithRetry(song) }
    val lyricsDeferred = async { getLyricsWithRetry(song) }
    val (finalSong, picUrl) = picDeferred.await()       // 返回增强后的 song + pic
    val bitmapDeferred = async { downloadBitmap(picUrl) }
    val playUrl = playUrlDeferred.await()
    val lyrics = lyricsDeferred.await()
    val bitmap = bitmapDeferred.await()
    val coverColors = predictColors(bitmap)             // 用预训练 int8 tflite
    FullEntry(finalSong.id, finalSong.name, finalSong.artist, finalSong.quality,
              picUrl, playUrl, lyrics, coverColors, bitmap)
}
```

**重试统一 3 次**：封装 `retry(3, delay=500ms) { api.call() }` 通用函数。
**无封面兜底**：`getPicWithRetry` 失败 → `picUrl = null` → 装配器用 `default_cover` drawable 转 Bitmap → `predictColors` 用默认封面做推理（不会 crash）。

### 4. 随机播放 seed 化
```kotlin
class ShuffleSequence(private val seed: Long, playlistSize: Int) {
    private val rng = Random(seed)
    private val remaining = (0 until playlistSize).shuffled(rng).toMutableList()
    fun nextIdx(): Int? = if (remaining.isEmpty()) null else remaining.removeAt(0)
    fun peekNext(): Int? = remaining.firstOrNull()
    fun reset() { /* ... */ }
}
```
- 切歌时从 sequence 取下一个索引 — 整个列表是固定的有序序列
- playSong 启动时根据 sequence 算出接下来 6 首的 Song，调 `assembleEntry` 预填 upcoming 队列
- 列表循环到尾时调 `reset()`（保留 seed），重新洗牌并跳过当前 idx

### 5. 歌词第 5 渠道
在 `SongCache.loadLyrics` 末尾加：
```kotlin
// 渠道 5：去掉歌手名只搜歌曲名（最后的兜底）
if (lyrics.isEmpty()) {
    repeat(3) { retry ->
        val r = apiService.searchLyricsLrclib(cleanName, "")
        if (r.getOrNull()?.isNotEmpty() == true) {
            lyrics = r.getOrNull()!!; return@repeat
        }
        delay(500)
    }
}
```
`searchLyricsLrclib` 需要支持空 artist（lrclib.net 允许空参数；网易云/QQ 也兼容）。

### 6. playSong 改造
```kotlin
fun playSong(song: Song) {
    val snap = snapshotPlaylist(song)
    val idx = snap.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

    viewModelScope.launch {
        // 1. 装当前首
        val currentEntry = playbackCache.assembleEntry(snap[idx])
        playbackCache.setCurrent(currentEntry)

        // 2. 装接下来 5 首（并发，每首 3 步并发）
        val nextSongs = computeUpcomingSongs(snap, idx, count = 5)  // 列表循环 / 随机用 sequence
        val nextEntries = coroutineScope { nextSongs.map { s -> async { playbackCache.assembleEntry(s) } }.awaitAll() }
        playbackCache.setUpcoming(nextEntries)

        // 3. 推 UI（用 entry 而非裸 Song）
        _currentSong.value = currentEntry.toSong()
        _coverColors.value = currentEntry.coverColors
        _lyrics.value = currentEntry.lyrics
        musicPlayer.play(currentEntry.playUrl)
        // ... 维持原有 _isLoading/_playbackError 流程
    }
}
```
**切歌/上一首**路径从 `_currentSong + 9 个 StateFlow` 改为 **读 `playbackCache.current()/history.first()`**。这样切歌瞬间数据已就绪，不依赖网络。

### 7. 切歌路径
```kotlin
// 下一首
fun onNext() {
    val next = playbackCache.takeUpcoming()  // 弹队首
    if (next == null) return
    // 历史 + 1
    playbackCache.pushToHistory(next)
    // 装进新队首
    val newSong = computeUpcomingSongs(...).first()
    viewModelScope.launch { playbackCache.assembleAndPush(newSong) }
    playFromCache(next)
}
```

## 文件改动

| 文件 | 改动 |
|------|------|
| `res/drawable/default_cover.jpg` | **新增**（已就位 691KB） |
| `player/SongCache.kt` | **重写** 为 `PlaybackCache`，9 元组 + 双队列 |
| `data/api/KuwoApiService.kt` | `searchLyricsLrclib` 兼容空 artist；新增 `getDefaultCoverBitmap()` |
| `ui/MainViewModel.kt` | playSong 改造读 `PlaybackCache.current()`；SHUFFLE 用 `ShuffleSequence`；删 `triggerCoverPrediction` 独立路径 |
| `player/MusicPlayer.kt` | 无需改 |
| `player/PlaybackService.kt` | 无需改 |
| `ui/theme/CoverColorPredictor.kt` | 新增 `predictFromBitmap(Bitmap)` 同步接口（当前是异步） |

## 验证步骤

1. `./gradlew assembleRelease` — 0 编译错误
2. 装 APK，手动测试：
   - 选首歌播放 → 队列头部正确，UI 显示对应 bg/pl/nl
   - 切到下一首 → 队列滑动，UI 无闪烁（数据已就绪）
   - 切到上一首 → 从 history 取
   - 随机播放 → seed 序列可复现（重启 APP 同一首起点的下一首一致）
   - 切到 4-5 首时，队列已补齐到 6
   - 歌词切歌 1s 内显示（无网络等待）
3. 内存：6+3 = 9 个 Bitmap，按 1080×1080 ARGB_8888 ≈ 4.5MB/张 → 9 × 4.5MB = **40MB**。需要 LRU 限制或缩到 720p。**风险点，待确认**。
4. 重试：模拟断网（飞行模式）切歌 → 缓存不阻塞播放，进度条仍能 seek，只是没歌词
5. 默认封面：搜索一首冷门歌（如翻唱）→ 封面显示 EvoHermes 图

## 风险与开放问题

### 高风险
1. **内存**：9 个 Bitmap 同步驻留 ≈ 40MB。Android 单 App 堆预算 256-512MB。可接受但需要监控。
2. **冷门歌曲**：iTunes/网易云都搜不到 → 退到默认封面 → 推理 bg/pl/nl（默认封面色比较深，可能影响其他歌曲视觉协调）。**需要确认**：默认封面推理出来的颜色，是否适合作为所有"无封面歌曲"的统一背景色？
3. **playSong 内联协程的取消**：`assembleEntry` 内部用 coroutineScope { async ... }，如果 playSong 被新的 playSong 取消，需要确保新协程启动前旧协程已让出。**用 `lyricsJob?.cancel()` 同样的方式**。

### 中风险
4. **随机 seed 持久化**：用户重启 APP 后 sequence 能否续上？**方案**：seed = 列表 id 列表 + 当前 idx 的 hash，APP 启动时复算。
5. **搜索结果点单首**：snapshotPlaylist = listOf(song)，没有"下一首"概念 → 切到下一首会播放失败。**现状已是如此**（之前的 `playSong` 也是这样），不是新引入的问题。
6. **history 跨列表失效**：用户搜新歌，搜索结果 hit 后的"上一首"应该指搜索列表的上一首，还是 history 的上一首？**方案**：history 与 playlist source 关联，搜索列表的上一首不写入 history（或者 history 区分 source）。

### 开放问题（需要你确认）
- **A. 默认封面推理色 vs 静态中性色**：无封面歌曲用推理色（每首不同）还是统一中性深灰？影响视觉一致性。
- **B. Bitmap 大小限制**：是否要把预解码 Bitmap 强制缩到 720p（节省 ~75% 内存）？
- **C. 随机 seed 是否持久化**：重启 APP 续上 vs 每次启动重新洗牌。
- **D. 队列深度**：即将 6 / 已播 3 是写死的。**如果你想要可配置**，需要在设置里加两个 slider，工作量 +半天。
- **E. assembleEntry 在切歌时的耗时**：6 首 × (pic + url + lyrics + bitmap + predict) 全部并发 → 1s 内能完成首屏可接受的等待。但网络差的歌会拖累其他歌。**是否在 UI 上加"加载中"骨架屏**？

## 预计工作量
- 文件改动：6 文件 + 1 新资源
- 代码量：+350 / -120（净 +230 行，主要在 PlaybackCache 队列 + 重试封装 + ShuffleSequence）
- 测试：手动 5 个场景
- **建议分 3 批提交**：
  1. 基础设施：PlaybackCache + 9 元组 + 重试 + 默认封面
  2. 整合：playSong 改造 + 双队列驱动 UI
  3. 优化：ShuffleSequence seed + 第 5 歌词渠道

## 等你拍板
- **Q1**: 方案整体方向 OK 吗？还是要调整？
- **Q2**: 上面 5 个开放问题（A-E）你的决定？
- **Q3**: 是否同意分 3 批提交？每批 build + commit？
- **Q4**: 是否要先 `git checkout -b feature/playback-cache-v2` 隔离开发？
