# HanimeAndroid

开源的第三方 Hanime Android 客户端，基于 Jetpack Compose 构建原生界面，采用 MVVM + Repository 架构与多模块化设计。

## 功能

- 多区块首页浏览
- 多维度搜索与筛选（关键词 / 分类 / 排序，支持任意组合）
- 多画质在线播放（HLS 流媒体，支持全屏、倍速、画中画、后台播放）
- 后台下载与离线缓存（前台服务通知、断点续传、并发控制）
- 批量下载管理（一键批量选择、多任务同时下载）
- 本地收藏与观看历史（Room 持久化、自动去重）
- 账号登录与 Cookie 管理
- 作者主页浏览（作品统计、视频分区、播放列表分区）
- 播放列表查看（公开播放列表浏览、批量加入下载）
- 多语言切换（简体中文 / 繁體中文 / English / 日本語，热切换即时生效）
- 深色模式适配（动态配色、Material Design 3 主题）
- 搜索历史自动记录（最多 20 条，本地持久化）
- Hilt 依赖注入架构（全局单例统一管理、ViewModel 构造注入）

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 构建工具 | Android Gradle Plugin | 9.2.1 |
| 编程语言 | Kotlin | 2.4.10 |
| UI 框架 | Jetpack Compose BOM | 2026.06.00 |
| Material Design | Material 3 (Compose) | 随 BOM 发布 |
| 架构 | MVVM + Repository + 单向数据流 | - |
| 依赖注入 | Hilt (Dagger) | 2.60 |
| 导航 | Navigation Compose | 2.9.8 |
| Hilt 导航 | Hilt Navigation Compose | 1.2.0 |
| 生命周期 | Lifecycle + ViewModel Compose | 2.10.0 |
| 协程 | Kotlin Coroutines / Flow / StateFlow | 随 Kotlin 发布 |
| 网络请求 | OkHttp | 5.4.0 |
| HTML 解析 | Jsoup | 1.22.2 |
| 图片加载 | Coil 3 (OkHttp 网络栈) | 3.5.0 |
| 视频播放 | Media3 ExoPlayer (含 HLS 扩展) | 1.10.1 |
| 数据持久化 | Room + KSP | 2.8.4 |
| 键值存储 | SharedPreferences + DataStore Flow 封装 | - |
| 符号处理 | KSP | 2.3.10 |
| Activity | Activity Compose | 1.12.3 |
| Core KTX | AndroidX Core KTX | 1.13.1 |
| 最低 SDK | Android 11 (API 30) | - |
| 目标 SDK | Android 16 (API 37) | - |

## 项目结构

```
hanime/
|-- app/                        # 主应用模块
|   |-- HanimeApplication.kt    # @HiltAndroidApp 入口，LocaleHelper 多语言包装
|   |-- MainActivity.kt         # @AndroidEntryPoint，NavHost 导航，Edge-to-Edge 沉浸式
|   |-- service/                # DownloadService（前台下载通知）
|   |-- ui/
|   |   |-- theme/              # HanimeTheme（MD3 色板、全局禁用 ripple）
|   |   |-- components/         # BottomNav（透明触摸拦截器）
|   |   |-- screens/            # 通用 Screen（播放列表、视频列表页）
|   |   `-- viewmodel/          # 共享 ViewModel
|   `-- res/                    # 应用级资源（图标、通知渠道、FileProvider）
|
|-- core/                       # 基础能力层（纯 Android Library，无业务逻辑）
|   |-- common/                 # 通用工具：AppLogger、LocaleHelper、扩展函数
|   |-- ui/                     # 共享 UI 资源：主题色 token、4 语言 strings.xml
|   `-- network/                # 网络基础配置：HttpClient、HCookieJar 接口定义
|
|-- data/                       # 数据层（单一数据源）
|   |-- cookie/                 # HCookieJar（ConcurrentHashMap 线程安全 + 过期清理）
|   |-- di/                     # DatabaseModule（Hilt 提供 Room DB/DAO）
|   |-- download/               # DownloadManager（ReentrantLock + ConcurrentHashMap）
|   |-- local/                  # Room 数据库：AppDatabase、DAO、Entity、Migration
|   |-- parser/                 # 9 个专用 HTML 解析器（Home/Search/Watch/Download/Playlist/Author...）
|   |-- preferences/            # Preferences（SharedPreferences + StateFlow 封装）
|   |-- remote/                 # NetworkService（OkHttp + Hilt 单例 + HCookieJar）
|   `-- repository/             # HanimeRepository（协程 IO 线程调度 + 结果封装）
|
|-- domain/
|   `-- model/                  # 领域模型：纯 data class，无任何 Android 依赖
|
`-- feature/                    # 功能模块（每个模块独立 Screen + ViewModel）
    |-- home/                   # 首页（多区块 + 下拉刷新 + 错误重试）
    |-- search/                 # 搜索（关键词/分类/排序三维筛选 + 历史记录）
    |-- detail/                 # 视频详情 + ExoPlayer 播放（全屏/倍速/下载/收藏）
    |-- download/               # 下载管理（批量选择/断点续传/完成项缩略图）
    |-- profile/                # 个人中心、登录、收藏、观看历史
    `-- settings/               # 设置页（多语言/并发数/自定义 BaseURL）、关于页、作者主页
```

## 模块化与依赖方向

```
         ┌───────────────────────────────────────────────────┐
         │  feature-*  (home / search / detail / download / │
         │              profile / settings)                  │
         └────────┬─────────────────────┬────────────────────┘
                  │ depends on          │ depends on
                  ▼                     ▼
           ┌──────────┐           ┌──────────┐
           │ core:ui  │──────────▶│core:common│
           └────┬─────┘           └────┬─────┘
                │ depends on            │
                ▼                       ▼
           ┌──────────┐           ┌──────────────┐
           │   data   │──────────▶│ domain:model │
           └────┬─────┘           └──────────────┘
                │ depends on
                ▼
           ┌─────────────┐
           │ core:network│
           └─────────────┘
```

- 单向依赖：`feature → data → core → domain`，禁止反向依赖
- 每个 feature 模块包含 **单一 UI 屏幕 + 对应 ViewModel**，通过 `hiltViewModel()` 获取注入
- data 模块所有组件（Repository/Parser/DownloadManager/CookieJar/Preferences）均通过 `@Inject` 构造函数或 Hilt `@Module` 提供

## 架构设计

### MVVM + 单向数据流

```
View (Compose) --> ViewModel (StateFlow) --> Repository --> NetworkService / Database
```

- **View 层**：Jetpack Compose 声明式 UI，通过 `collectAsStateWithLifecycle` 订阅状态
- **ViewModel 层**：持有 `StateFlow`/`MutableStateFlow`，处理业务逻辑，使用 `Dispatchers.IO` 进行网络请求
- **Repository 层**：统一数据来源（网络与本地数据库）
- **Model 层**：纯数据类，定义在 `:domain:model` 模块

### 模块化

项目拆分为 12 个 Gradle 模块，依赖方向单向流动：

```
feature-* --> core:ui --> core:common
    |             |
    v             v
  data ------> domain:model
    |
    v
core:network
```

## 构建与运行

### 环境要求

- JDK 17 或更高版本
- Android Studio (支持 AGP 9.x)
- Kotlin 2.4.10

### 构建命令

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease

# 运行单元测试
./gradlew test
```

## 未来更新计划

| 优先级 | 功能 | 说明 |
| --- | --- | --- |
| 🟢 高 | 完善多语言支持 | 补齐所有硬编码字符串的资源化、拉取服务端文案、RTL 布局适配、区域化时间/数字格式 |
| 🟡 中 | 优化 UI 样式 | 统一卡片圆角 / 间距 / 字重规范、播放页控制栏交互重构、动效与过渡动画完善、响应式布局适配平板 |
| 🔴 中 | 新增视频评论功能 | 视频详情页评论区、回复/点赞、评论分页加载、评论举报与敏感词过滤 |

---

## 开源协议

本项目采用 [GNU General Public License v3.0](LICENSE) 协议开源。

您可以自由地使用、研究、修改和分发本软件，但分发本软件或其衍生作品时，必须同样以 GPLv3.0 协议开源完整源代码，并保留原始版权声明与协议文本。

## 致谢

本项目使用了以下开源项目：

- [Android Gradle Plugin](https://developer.android.com/build/releases/gradle-plugin) - Apache 2.0
- [Kotlin](https://kotlinlang.org) - Apache 2.0
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Apache 2.0
- [Media3 ExoPlayer](https://developer.android.com/media/media3) - Apache 2.0
- [OkHttp](https://square.github.io/okhttp) - Apache 2.0
- [Jsoup](https://jsoup.org) - MIT
- [Coil 3](https://coil-kt.github.io/coil) - Apache 2.0
- [Room](https://developer.android.com/jetpack/androidx/releases/room) - Apache 2.0
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) - Apache 2.0
- [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) - Apache 2.0
- [KSP](https://kotlinlang.org/docs/ksp-overview.html) - Apache 2.0

## 免责声明

本项目仅供学习和研究用途，不存储任何视频内容，所有内容均来自第三方网站。使用者需自行承担使用风险，作者不对任何因使用本软件造成的直接或间接损失负责。请遵守当地法律法规。
