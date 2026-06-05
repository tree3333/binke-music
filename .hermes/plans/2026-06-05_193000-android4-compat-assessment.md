# 安卓 4 兼容性评估（minSdk 26 → 14/15/19）

## 当前项目现状
- **minSdk = 26**（Android 8.0）
- **代码量**：22 个 Kotlin 文件，6186 行（UI 几乎全是 Jetpack Compose）
- **核心依赖**：
  - Jetpack Compose（Material3、Navigation、Foundation、Animation）
  - Media3 ExoPlayer 1.2.0 + MediaSession 1.2.0
  - Coil-Compose 2.5.0
  - TensorFlow Lite 2.14.0（封面色预测，7 个 int8 模型）
  - OkHttp 4.12.0
  - DataStore Preferences 1.0.0
  - Kotlin 协程 + StateFlow

## 安卓 4 版本与 API 级别
| 版本 | API | 备注 |
|---|---|---|
| 4.0 Ice Cream Sandwich | 14/15 | 2011，已无活跃设备 |
| 4.1-4.3 Jelly Bean | 16-18 | 2012-2013，< 0.1% 市场份额 |
| 4.4 KitKat | 19 | 2013，唯一"勉强"还可能装上的版本 |

**最现实目标：API 19（4.4 KitKat）**——再低就真没必要了。

## 真实工作量（按"功能等价重写"算）

### 必修：UI 层（**最大头**）
Jetpack Compose 在 API 19 上**完全不能跑**（Compose 最低 API 21，且生产验证只到 23+）。需要重写整个 UI 层：

| 模块 | 当前实现 | 替换 |
|---|---|---|
| MainActivity + MainScreen | Compose 全套 | AppCompatActivity + XML + ViewBinding |
| HomeScreen / MusicScreen / MineScreen / SearchScreen | 4 个 Compose Screen | 4 套 XML layout + Fragment/Activity |
| 歌词 LyricsView | Compose Canvas 绘制 | 自定义 View onDraw |
| 拖拽切页 (`detectHorizontalDragGestures`) | Compose 指针 | GestureDetector / ViewPager2 |
| 进度条 Slider | Material3 Slider | SeekBar 自定义 |
| 氛围色背景 | Compose Box background | View.setBackgroundColor |
| 锁屏/通知栏媒体控件 | Media3 自动处理 | Media3 仍可用 |
| 状态管理 collectAsState | StateFlow + Compose | StateFlow + LiveData/手动 observe |

**预估：2-3 周**全职（取决于对 XML/View 体系的熟悉度）。

### 必修：依赖替换
- `androidx.compose.*` 整套**删掉**（-节省编译时间）
- `androidx.activity:activity-compose` → `androidx.activity:activity-ktx`
- `androidx.lifecycle:lifecycle-viewmodel-compose` → `androidx.lifecycle:lifecycle-viewmodel-ktx`
- `androidx.navigation:navigation-compose` → `androidx.navigation:navigation-fragment-ktx`
- `coil-compose` → `coil`（基础版，无 Compose 依赖）
- `media3-exoplayer 1.2.0` 兼容 API 21+，**可用**（最低要求 21）
- `tensorflow-lite 2.14.0` 兼容 API 21+，**可用**（但 NNAPI delegate 在 API 19 上完全没，需要 CPU 推理，封面色预测要慢一些）
- `okhttp 4.12.0` 兼容 API 21+，**可用**
- `datastore-preferences 1.0.0` **最低要求 API 16**，可用

### 可选（建议删的功能）
- **封面色预测（TensorFlow Lite）**——保留无成本，但 GPU/NNAPI 加速不可用，纯 CPU 推理
- **横屏 1920×1080 适配**——车机目标本身就是 4.x 量产机器罕见，可考虑只保留竖屏

### 已存在的 API 兼容代码
- `Build.VERSION.SDK_INT >= O` 已经在 PlaybackService 里做旧/新 service 启动分支
- `enableEdgeToEdge` / `WindowInsetsController` 在 API 19 上要加 SDK 守卫
- `WindowCompat.setDecorFitsSystemWindows` → API 30+，**已有 polyfill 但要测**

## 总体时间估算
- **完整重写 UI 层（功能等价）**：4-6 周全职
- **"能跑起来"最小版本**（单 Activity + 几个 XML + 核心播放）：1-2 周
- **测试 + 修 bug**：1-2 周

## 我的建议
**不建议做**，理由：

1. **市场**——安卓 4.x 全球 < 0.5%，你车机 1920×1080 也不可能是 4.x 设备
2. **成本**——6 周工作量 vs 0 收益
3. **维护**——以后加任何新功能都要双套实现（Compose + XML）
4. **生态**——连 ExoPlayer 都只测到 21+，TensorFlow Lite 也不支持 19 上的硬件加速
5. **替代方案**：
   - **API 24（Android 7.0）**：覆盖 95%+ 活跃设备，1-2 天工作量
   - **API 21（Android 5.0）**：覆盖 99%+ 设备，3-5 天工作量
   - **真要 4.x**：找台 4.4 真机回归测试，否则只算"理论上能跑"

## 推荐做法
**先把 minSdk 降到 24**，看实际能不能跑通 + 测试。如果是你那个车机环境其实是 7.0/8.0 但 build 配置写错了，那 0 工作量就解决了。
