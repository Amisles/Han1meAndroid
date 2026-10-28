<p align="center">
  <img src="docs/logo.png" width="120" height="120" alt="Han1meAndroid Logo">
</p>

<h1 align="center">Han1meAndroid</h1>

<p align="center">
  第三方 Han1me Android 客户端，基于 Jetpack Compose 构建原生界面，采用 MVVM + Repository 架构与多模块化设计。
</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/Amisles/Han1meAndroid"></a>
  <a href="../../actions/workflows/android-ci.yml"><img alt="CI" src="https://github.com/Amisles/Han1meAndroid/actions/workflows/android-ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/Amisles/Han1meAndroid"></a>
  <img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&amp;logoColor=white">
</p>

<p align="center">
  <b>简体中文</b> · <a href="README.en.md">English</a>
</p>

## 下载安装

**最新版本：v1.7.1** · 最低支持 Android 11（API 30）· 目标 Android 17（API 37）

| 项目 | 说明 |
| --- | --- |
| 下载地址 | [GitHub Releases](https://github.com/Amisles/Han1meAndroid/releases/latest) |
| 安装包 | `Han1meAndroid-v<版本>.apk`，约 7 MB |
| 分发渠道 | 仅通过 GitHub Releases 分发，未上架任何应用商店 |
| 升级方式 | 同签名覆盖安装，收藏、历史与下载记录均保留 |

该指纹在后续版本中保持不变。若不一致，说明安装包并非来自本仓库，请勿安装。

> 应用需要能够访问对应站点。若默认站点不可用，可在「设置 → 官网网址」中填写可用的镜像站。

## 界面预览

<p align="center">
  <img src="docs/screenshots/01-home.jpg" width="24%" alt="首页">
  <img src="docs/screenshots/02-detail.jpg" width="24%" alt="详情与播放">
  <img src="docs/screenshots/03-search.jpg" width="24%" alt="搜索与筛选">
  <img src="docs/screenshots/04-download.jpg" width="24%" alt="下载管理">
</p>

<p align="center">
  <img src="docs/screenshots/05-customize.jpg" width="24%" alt="界面定制">
  <img src="docs/screenshots/06-settings.jpg" width="24%" alt="设置">
  <img src="docs/screenshots/07-dark.jpg" width="24%" alt="深色模式">
</p>

## 功能

- **首页与发现**：多区块首页浏览、下拉刷新与错误重试；「继续观看」横滑分区展示未看完的条目，点击直达并续播（可关闭）
- **搜索与筛选**：多维度搜索（关键词 / 分类 / 排序，支持任意组合）+ 标签 / 日期 / 时长筛选 + 搜索历史自动记录（最多 20 条，本地持久化）
- **在线播放**：多画质 HLS 流媒体播放，支持全屏、倍速、画中画（PiP）、双击快进/快退、双指缩放
- **离线下载**：后台下载与离线缓存（前台服务通知、断点续传、多线程分块并行下载、并发控制）、批量下载管理
- **收藏与历史**：本地收藏与观看历史（Room 持久化、自动去重）；收藏支持自建分组、移动、自定义标签（最多 10 个）与按标题本地搜索
- **观看统计**：本周与累计观看时长、完成率、常看作者 Top 5、近 7 天分布，纯本地计算，可在设置中关闭
- **数据备份**：一键导出 / 导入收藏、分组、历史、下载列表、搜索历史、设置与界面定制，支持「合并」或「覆盖」策略，导入前自动留快照
- **订阅提醒**：每日检查订阅页更新，发现新片时发送本地通知（默认关闭）
- **人机校验**：站点要求 Cloudflare 校验时弹出内置浏览器，完成校验后自动保存并重试；也可在设置页手动触发
- **账号体系**：账号登录与 Cookie 管理（EncryptedSharedPreferences 加密存储）、账户资料编辑（修改昵称与邮箱）
- **社区互动**：视频评论区（评论列表、回复展开、点赞数显示）、作者主页浏览（作品统计、视频分区、播放列表分区）、一键订阅作者并接入官方订阅内容页
- **播放列表**：公开播放列表浏览、批量加入下载
- **个性化**：多语言切换（简体中文 / 繁體中文 / English / 日本語，热切换即时生效）、主题模式切换（浅色 / 深色 / 跟随系统，Material Design 3 动态配色）
- **界面定制**：全局字号、缩略图宽高比与圆角、列表缩略图宽度、栅格列数与目标列宽、图片解码质量档（省流 / 标准 / 高清）、首页每区显示条数；首页分区级布局（列表 / 网格 / 横滑）与顺序调整；四档预设方案；配置以文本导入导出
- **网络诊断**：检测当前镜像站的 DNS、连接、证书、镜像与登录状态
- **平板适配**：响应式框架适配平板导航栏 / 栅格 / 字体与触摸目标
- **「我的」抽屉**：首页顶栏入口，左侧滑出抽屉聚合账户资料、订阅、设置等入口
- **工程架构**：Hilt 依赖注入（全局单例统一管理、ViewModel 构造注入）

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 构建工具 | Android Gradle Plugin | 9.4.1 |
| 编程语言 | Kotlin | 2.4.21 |
| UI 框架 | Jetpack Compose BOM | 2026.09.00 |
| Material Design | Material 3 (Compose) | 随 BOM 发布 |
| 架构 | MVVM + Repository + 单向数据流 | - |
| 依赖注入 | Hilt (Dagger) | 2.60.1 |
| 导航 | Navigation Compose | 2.10.2 |
| Hilt 导航 | Hilt Navigation Compose | 1.4.0 |
| 生命周期 | Lifecycle + ViewModel Compose | 2.11.0 |
| 协程 | Kotlin Coroutines / Flow / StateFlow | 随 Kotlin 发布 |
| 网络请求 | OkHttp | 5.5.0 |
| HTML 解析 | Jsoup | 1.23.2 |
| 图片加载 | Coil 3 (OkHttp 网络栈) | 3.6.3 |
| 视频播放 | Media3 ExoPlayer（HLS 扩展 + Session） | 1.11.1 |
| 数据持久化 | Room + KSP | 2.8.5 |
| 后台任务 | WorkManager + Hilt Work | 2.12.0 |
| 键值存储 | EncryptedSharedPreferences + DataStore Flow 封装 | - |
| 安全存储 | AndroidX Security (EncryptedSharedPreferences) | 1.1.0 |
| 符号处理 | KSP | 2.3.12 |
| Activity | Activity Compose | 1.13.0 |
| Core KTX | AndroidX Core KTX | 1.19.1 |
| 最低 SDK | Android 11 (API 30) | - |
| 目标 SDK | Android 17 (API 37) | - |

## 项目结构

```
hanime/
|-- app/                        # 主应用模块
|   |-- HanimeApplication.kt    # @HiltAndroidApp 入口，LocaleHelper 多语言包装
|   |-- MainActivity.kt         # @AndroidEntryPoint，NavHost 导航，Edge-to-Edge 沉浸式
|   |-- service/                # DownloadService（前台下载通知）
|   |-- app/work/               # SubscriptionReminderWorker（订阅更新检查）
|   |-- ui/
|   |   |-- theme/              # HanimeTheme（MD3 色板、Typography、状态层点击反馈）
|   |   |-- components/         # BottomNav（透明触摸拦截器）
|   |   |-- screens/            # 通用 Screen（播放列表、视频列表页）
|   |   |-- viewmodel/          # 共享 ViewModel
|   |   `-- CloudflareChallengeDialog.kt  # 人机校验弹窗
|   `-- res/                    # 应用级资源（图标、通知渠道、FileProvider）
|
|-- core/                       # 基础能力层（纯 Android Library，无业务逻辑）
|   |-- common/                 # 通用工具：AppLogger、LocaleHelper、扩展函数
|   `-- ui/                     # 共享 UI 层：MD3 色板、4 语言 strings.xml、界面设计令牌
|                               #   （UiConfig / UiConfigStore / UiPreset / HomeSectionConfig）、
|                               #   响应式窗口信息、共享组件（卡片 / 缩略图 / 设置分组 …）
|
|-- data/                       # 数据层（单一数据源）
|   |-- backup/                 # BackupManager（zip 导出导入、冲突策略、自动快照）
|   |-- cookie/                 # HCookieJar（ConcurrentHashMap 线程安全 + 过期清理）
|   |-- di/                     # DatabaseModule（Hilt 提供 Room DB/DAO）
|   |-- download/               # DownloadManager（多线程分块下载 + 并发门控 + 断点续传位图 + 限速令牌桶 + 存储统计）
|   |-- local/                  # Room 数据库：AppDatabase、DAO、Entity、Migration
|   |-- parser/                 # 多个专用 HTML 解析器（首页 / 搜索 / 详情 / 下载 / 播放列表 / 作者 / 订阅 / 评论 / 账户 …）
|   |-- preferences/            # Preferences（SharedPreferences + StateFlow 封装）
|   |-- remote/                 # NetworkService（OkHttp + Hilt 单例 + HCookieJar）、CloudflareChallenge
|   |-- repository/             # HanimeRepository（协程 IO 线程调度 + 结果封装）
|   |-- stats/                  # StatsManager（观看统计计算）
|   `-- subscription/           # 订阅更新检测与 WorkManager 调度
|
|-- domain/
|   `-- model/                  # 领域模型：纯 data class，无任何 Android 依赖
|
`-- feature/                    # 功能模块（每个模块独立 Screen + ViewModel）
    |-- home/                   # 首页（多区块 + 下拉刷新 + 继续观看 + 分区级布局与顺序）
    |-- search/                 # 搜索（关键词/分类/排序 + 筛选 + 历史 + 输入建议）
    |-- detail/                 # 视频详情 + ExoPlayer 播放（全屏/倍速/下载/收藏/媒体会话）
    |-- download/               # 下载管理（批量选择/断点续传/限速/存储面板）
    |-- profile/                # 个人中心、登录、收藏分组、观看历史、订阅、观看统计
    `-- settings/               # 设置页、界面定制、网络诊断、备份恢复、关于、作者主页
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

项目拆分为 11 个 Gradle 模块，依赖方向单向流动：

```
feature-* --> core:ui --> core:common
    |             |
    v             v
  data ------> domain:model
```

## 构建与运行

### 环境要求

- JDK 21（AGP 9.x 构建要求；源码兼容级别为 Java 11）
- Android Studio (支持 AGP 9.x)
- Kotlin 2.4.21

### 构建命令

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease

# 运行单元测试
./gradlew test
```

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
- 提交 PR 修复 Bug 或实现新功能

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
