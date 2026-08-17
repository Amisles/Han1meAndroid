<p align="center">
  <img src="docs/logo.png" width="120" height="120" alt="HanimeAndroid Logo">
</p>

<h1 align="center">HanimeAndroid</h1>

<p align="center">
  开源的第三方 Hanime Android 客户端，基于 Jetpack Compose 构建原生界面，采用 MVVM + Repository 架构与多模块化设计。
</p>

<p align="center">
  <b>当前版本：v1.2.0</b>（versionCode 8）· 最低支持 Android 11（API 30）· 目标 Android 16（API 36）
</p>

## 功能

- **首页与发现**：多区块首页浏览、下拉刷新与错误重试
- **搜索与筛选**：多维度搜索（关键词 / 分类 / 排序，支持任意组合）+ 搜索历史自动记录（最多 20 条，本地持久化）
- **在线播放**：多画质 HLS 流媒体播放，支持全屏、倍速、画中画（PiP）、后台播放、双击快进/快退、双指缩放
- **离线下载**：后台下载与离线缓存（前台服务通知、断点续传、多线程分块并行下载、并发控制）、批量下载管理（一键批量选择、多任务同时下载）
- **收藏与历史**：本地收藏与观看历史（Room 持久化、自动去重）
- **账号体系**：账号登录与 Cookie 管理（EncryptedSharedPreferences 加密存储）、账户资料编辑（修改昵称与邮箱）
- **社区互动**：视频评论区（评论列表、回复展开、点赞数显示）、作者主页浏览（作品统计、视频分区、播放列表分区）、一键订阅作者并接入官方订阅内容页
- **播放列表**：公开播放列表浏览、批量加入下载
- **个性化**：多语言切换（简体中文 / 繁體中文 / English / 日本語，热切换即时生效）、主题模式切换（浅色 / 深色 / 跟随系统，Material Design 3 动态配色）
- **平板适配**：响应式框架适配平板导航栏 / 栅格 / 字体与触摸目标
- **「我的」抽屉**：首页顶栏入口，左侧滑出抽屉聚合账户资料、订阅、设置等入口
- **工程架构**：Hilt 依赖注入（全局单例统一管理、ViewModel 构造注入）

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
| 键值存储 | EncryptedSharedPreferences + DataStore Flow 封装 | - |
| 安全存储 | AndroidX Security (EncryptedSharedPreferences) | 1.1.0-alpha06 |
| 符号处理 | KSP | 2.3.10 |
| Activity | Activity Compose | 1.12.3 |
| Core KTX | AndroidX Core KTX | 1.13.1 |
| 最低 SDK | Android 11 (API 30) | - |
| 目标 SDK | Android 16 (API 36) | - |

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
|   |-- download/               # DownloadManager（多线程分块下载 + Semaphore 并发控制 + CAS 软槽位 + ConcurrentHashMap + ReentrantLock）
|   |-- local/                  # Room 数据库：AppDatabase、DAO、Entity、Migration
|   |-- parser/                 # 多个专用 HTML 解析器（首页 / 搜索 / 详情 / 下载 / 播放列表 / 作者 / 订阅 / 评论 / 账户 …）
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
    `-- settings/               # 设置页（多语言/主题模式/并发数/自定义 BaseURL）、关于页、作者主页
```
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

- JDK 21（AGP 9.x 构建要求；源码兼容级别为 Java 11）
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
| 🟡 中 | 优化 UI 样式与交互 | 统一卡片圆角 / 间距 / 字重规范、播放页控制栏交互重构、动效与过渡动画完善 |

> ✅ 近期已落地：平板响应式适配（v1.1.0）、单文件分片（字节范围）多线程下载（v1.2.0）。

---

## 贡献与支持

欢迎参与本项目！无论是反馈 Bug、建议功能，还是提交代码，都能让 HanimeAndroid 变得更好。

### 反馈问题（Issue）

- 提交前请先检索 [已有 Issue](../../issues)，避免重复
- 新建 Issue 时请选择对应模板，并填写：
  - 复现步骤与预期 / 实际表现
  - App 版本、Android 版本、设备型号
  - 相关日志（位于 App `filesDir/hanime_app.log`）或截图
- 非公开或安全相关问题，请通过 [Security Advisory](../../security/advisories/new) 提交

### 提交代码（Pull Request）

1. Fork 本仓库并新建分支：`git checkout -b feat/your-feature` 或 `fix/your-bugfix`
2. 遵循现有代码风格与 MVVM + 多模块架构约定
3. 如改动涉及网络或解析，请补充 / 更新对应单元测试
4. PR 标题建议使用约定式提交（`feat:` / `fix:` / `docs:` / `refactor:` …）
5. 提交 PR 时请关联相关 Issue，并简要说明改动点与测试方式
6. 等待 Review，根据反馈在同一分支上追加提交即可

### 支持项目

如果这个项目对你有帮助，欢迎在仓库右上角点个 ⭐ Star —— 你的支持是持续维护的动力！

也可以通过以下方式帮助项目：

- 在 Issue 中提出改进建议
- 翻译 / 校对多语言资源（简中 / 繁中 / English / 日本語）
- 提交 PR 修复 Bug 或实现「未来更新计划」中的功能

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
