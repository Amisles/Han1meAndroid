# HanimeAndroid

开源的第三方 Hanime Android 客户端，基于 Jetpack Compose 构建原生界面，采用 MVVM + Repository 架构与多模块化设计。

## 功能

- 多区块首页浏览
- 多维度搜索与筛选
- 多画质在线播放（HLS 流媒体）
- 后台下载与离线缓存
- 本地收藏与观看历史
- 账号登录与 Cookie 管理
- 作者主页浏览
- 播放列表查看

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 构建工具 | Android Gradle Plugin | 9.2.1 |
| 编程语言 | Kotlin | 2.4.10 |
| UI 框架 | Jetpack Compose BOM | 2026.06.00 |
| 架构 | MVVM + Repository | - |
| 导航 | Navigation Compose | 2.9.8 |
| 生命周期 | Lifecycle | 2.10.0 |
| 网络请求 | OkHttp | 5.4.0 |
| HTML 解析 | Jsoup | 1.22.2 |
| 图片加载 | Coil 3 | 3.5.0 |
| 视频播放 | Media3 ExoPlayer | 1.10.1 |
| 数据持久化 | Room | 2.8.4 |
| 符号处理 | KSP | 2.3.10 |
| 最低 SDK | Android 11 (API 30) | - |
| 目标 SDK | Android 16 (API 37) | - |

## 项目结构

```
hanime/
|-- app/                        # 主模块，包含 MainActivity 与导航
|-- core/
|   |-- common/                 # 通用工具与日志
|   |-- ui/                     # 共享 UI 组件与主题
|   `-- network/                # 网络配置
|-- data/                       # 数据层
|   |-- cookie/                 # Cookie 管理与序列化
|   |-- download/               # 下载管理器
|   |-- local/                  # 本地数据库 (Room DAO)
|   |-- parser/                 # HTML 解析器
|   |-- preferences/            # SharedPreferences 封装
|   |-- remote/                 # 网络请求服务
|   `-- repository/             # 数据仓库
|-- domain/
|   `-- model/                  # 领域模型（纯数据类）
`-- feature/
    |-- home/                   # 首页
    |-- search/                 # 搜索
    |-- detail/                 # 视频详情与播放
    |-- download/               # 下载管理
    |-- profile/                # 个人中心、登录、收藏、历史
    `-- settings/               # 设置、关于、作者主页
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
