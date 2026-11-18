# 贡献指南

感谢你愿意花时间改进 Han1meAndroid。这个仓库由个人维护，合并速度主要取决于改动是否容易验证：范围小、可复现、带测试的 PR 通常很快就能过。

## 在开始之前

- **缺陷**：用 [Bug 报告模板](.github/ISSUE_TEMPLATE/bug_report.yml) 提 Issue，附版本号、设备、复现步骤；崩溃请附 `filesDir/hanime_app.log` 中相关片段（贴之前删掉账号与 Cookie）。
- **新功能**：先开 Issue 说明「要解决什么问题」，避免大改动物色方向之后才发现不接受。
- **安全漏洞**：不要开公开 Issue，见 [SECURITY.md](SECURITY.md) 的私密报告渠道。
- **内容与版权**：不讨论站点内容、账号代充、绕过付费等请求，这类 Issue 会被直接关闭。

## 开发环境

| 项 | 要求 |
| --- | --- |
| JDK | 21（AGP 9.x 构建要求；源码兼容级别为 Java 11） |
| Android SDK | Platform 37、Build-Tools 36.0.0 |
| Kotlin | 版本见 `gradle/libs.versions.toml` |

```bash
./gradlew assembleDebug                                   # 编译
./gradlew testDebugUnitTest                               # 全量单元测试
./gradlew :data:testDebugUnitTest --tests "*Parser*"      # 只跑解析层
```

## 模块边界

仓库分为 13 个 Gradle 模块，必须满足下列依赖方向（由重到轻，反向依赖一律不合并）：

| 模块 | 允许依赖 |
| --- | --- |
| `app` | 全部 `feature-*`、`core:*`、`data`、`domain:model` |
| `feature-*` | `core:ui`、`core:common`、`data`、`domain:model` |
| `data` | `core:common`、`domain:model` |
| `core:ui` | `core:common`、`domain:model` |
| `core:common` | `domain:model` |
| `domain:model` | 无（纯 Kotlin 模型与契约） |

补充约定：

- `feature-*` 之间互不依赖；需要复用就下沉到 `core:ui` 或 `core:common`。
- 业务契约（`*Repository` 接口）放在 `domain:model` 的 `app.amisles.hanime.domain.repository`，实现放在 `data/.../repository`，Hilt 绑定集中在 `data/di/RepositoryModule.kt`。
- 视图层不直接调用 `NetworkService`；网络与解析只经过 Repository。

## 代码约定

- Kotlin 官方代码风格，4 空格缩进，一次只做一件事。
- 注释只写「为什么」和约束，不复述代码；能用命名与类型表达的地方不写注释。
- 新增用户可见文案必须同时更新 `core/ui/src/main/res/` 下的 `values`、`values-en`、`values-ja`、`values-zh-rTW` 四份 `strings.xml`，键名与条数保持一致。
- 引入新依赖前先在 Issue 里说明理由；能复用现有库就不要新增。
- 不提交 `keystore.properties`、`*.jks`、`*.log`、抓包文件或站点页面快照（测试夹具除外，见下）。

## 测试要求

- 改动 `data/parser` 必须补或更新单元测试，并在 PR 中说明站点改了什么结构。
- 真实页面回归测试的夹具放在 `data/src/test/resources/fixtures/`，由 `Fixtures.html("文件名")` 从 classpath 读取，与运行目录无关。
- 夹具是线上页面的原样快照：**不要为了测试变绿去改夹具**。站点改版时重新抓取页面覆盖同名文件，让断言自己暴露差异；确认解析无需修改后再更新断言。
- 夹具命名：`comic-*.html` 漫画站页面、`video-detail-*.html` 视频详情页、`playlist-*.html` 播放清单页。
- 提交前至少本地跑过一次 `./gradlew testDebugUnitTest`。

## 提交与 Pull Request

- 分支从 `main` 切出，命名为 `feat/xxx`、`fix/xxx`、`refactor/xxx`。
- 提交信息使用约定式提交前缀（`feat:` `fix:` `refactor:` `perf:` `test:` `docs:` `chore:` `ci:`），描述用中文，一次提交只做一件事。
- PR 请填写 [模板](.github/PULL_REQUEST_TEMPLATE.md)：改动内容、关联 Issue、验证方式。
- CI（`.github/workflows/android-ci.yml`）必须全绿；改动构建脚本、ProGuard 规则或资源时，请自行确认 `assembleRelease` 也能通过。
- 不接受：与改动无关的格式化重排、顺手重命名、把死代码清理混进功能提交。

## 发布流程（维护者）

1. 更新 `app/build.gradle.kts` 的 `versionCode` / `versionName`。
2. 打 tag 并推送：`git tag vX.Y.Z && git push origin vX.Y.Z`，tag 必须等于 `v` + `versionName`。
3. `.github/workflows/release.yml` 会校验版本一致、验证签名、产出 APK 与 sha256，并创建 GitHub Release。
4. 发布说明可放在 `docs/release-vX.Y.Z.md`，工作流优先使用它，缺失时回退到自动生成的 notes。

## 行为准则

参与本项目即表示同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。
