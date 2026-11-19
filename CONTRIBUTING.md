# 贡献指南

感谢你愿意花时间改进 Han1meAndroid。这个仓库由个人维护，合并速度主要取决于改动是否容易验证：范围小、可复现、带测试的 PR 通常很快就能过。

## 在开始之前

- **缺陷**：用 [Bug 报告模板](.github/ISSUE_TEMPLATE/bug_report.yml) 提 Issue，附版本号、设备、复现步骤；崩溃请附 `filesDir/hanime_app.log` 中相关片段（贴之前删掉账号与 Cookie）。
- **新功能**：先开 Issue 说明「要解决什么问题」，避免大改动物色方向之后才发现不接受。
- **安全漏洞**：不要开公开 Issue，见 [SECURITY.md](SECURITY.md) 的私密报告渠道。
- **内容与版权**：该项目属于第三方客户端，仅用于数据展示并提供观看的平台，不生产任何内容，一切数据资源皆属于Han1me官方，所以最好不讨论站点内容，这类 Issue 会被直接关闭。

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

## 质量门禁

CI（`.github/workflows/android-ci.yml`）在每次 PR 上跑三件事，任何一项失败都不能合并：

| 步骤 | 命令 | 失败含义 |
| --- | --- | --- |
| 单元测试 | `./gradlew testDebugUnitTest :domain:model:test :konsist-test:test` | 逻辑或解析回归、架构约束被破坏 |
| Android Lint | `./gradlew lintDebug` | 引入了基线外的新问题 |
| Debug 构建 | `./gradlew assembleDebug` | 资源或编译错误 |

改到 ProGuard 规则、依赖版本或构建脚本时，需要额外验证 release 构建：在 Actions 里手动运行 `Android PR CI`（Run workflow），勾选 `release_only` 时只跑 `./gradlew :app:assembleRelease` 与 `mapping.txt` 检查（约 7 分钟），不勾选则连同单测、lint 与 debug 构建一起跑。发 tag 时 `release.yml` 也会完整构建一次，所以漏跑不会让坏配置直接发出去，只是发现得更晚。

架构约束由 `:konsist-test` 里的四条规则守住：`feature-*` 之间不得互相依赖、`feature-*` 只能依赖 `domain` 与 `core`（不得 import `:data` 或 `:app`）、`domain:model` 不得出现 `android.*`。新增代码违反时本地跑 `./gradlew :konsist-test:test` 即可看到具体文件与 import。

各模块根目录的 `lint-baseline.xml` 冻结的是既有告警，不是免检名单：修好一条就从基线里消掉一条（`./gradlew updateLintBaseline` 重新生成），不要在基线里新增条目。

## 依赖与供应链

- `gradle/wrapper/gradle-wrapper.properties` 固定了发行包 URL 与 `distributionSha256Sum`，升级 Gradle 必须同步更新校验和。
- 版本号只在 `gradle/version.properties` 一处，构建与发布流程都读它。
- Release 附带 R8 映射（`mapping-vX.Y.Z.txt.gz`）与依赖清单（`dependencies-vX.Y.Z.txt`），后者可用于核对实际打进包的构件。
- 不启用依赖校验清单（`gradle/verification-metadata.xml`）：Dependabot 每次升级都要重新生成整份清单，维护成本高于收益。

## 行为准则

参与本项目即表示同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。
