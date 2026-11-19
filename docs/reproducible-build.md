# 构建与校验

本文件回答两件事：怎么从 tag 重建出与发布包一致的产物，以及拿到 APK 后怎么确认它确实来自本仓库。

## 环境

| 项 | 值 |
| --- | --- |
| JDK | 21 |
| Android SDK | Platform 37、Build-Tools 36.0.0 |
| Gradle | 由 `gradle/wrapper/gradle-wrapper.properties` 固定（含 `distributionSha256Sum`） |
| 版本号 | `gradle/version.properties`，构建与发布流程共用 |

```bash
git checkout v1.8.0
./gradlew :app:assembleRelease
```

未提供签名材料时产物是 unsigned 包（`app-release-unsigned.apk`），其余步骤（R8、资源裁剪、Baseline Profile）与发布包一致。

## 已固定的输入

- Gradle 发行包：URL 与 sha256 校验和都写在 wrapper 配置里，下载内容被篡改会直接失败。
- 依赖构件：`gradle/verification-metadata.xml` 记录了每个 jar/aar 的 sha256，Gradle 校验不通过即中止。
- 版本号：`gradle/version.properties` 是唯一来源，`.github/workflows/release.yml` 用它校验 tag 与构建产物是否对应。
- 发布说明：`docs/release-vX.Y.Z.md` 随 tag 入库，发布流程直接引用，不再需要站外粘贴。

## 不承诺字节级一致

APK 经过 v1/v2/v3 三段签名，其中 v1（jar 签名）会写入时间戳，因此同一份源码两次构建的字节不会完全相同。可以核对的是：

1. 发布页给出 APK 的 sha256（`Han1meAndroid-vX.Y.Z.apk.sha256`），下载后本地比对。
2. 证书指纹：README 中公开的 SHA-256 与 `apksigner verify --print-certs` 输出一致，说明包由同一把私钥签出。
3. 从 tag 重新构建并对比行为、`mapping.txt` 中的类名映射与 APK 内 `AndroidManifest.xml` 的版本号。

```bash
sha256sum -c Han1meAndroid-v1.8.0.apk.sha256
apksigner verify --print-certs Han1meAndroid-v1.8.0.apk
```

## 崩溃栈还原

release 包已混淆，堆栈里的类名是 `a.b.c`。发布页附带的 `mapping-vX.Y.Z.txt.gz` 是 R8 映射文件，配合 `retrace` 可还原：

```bash
gunzip -c mapping-v1.8.0.txt.gz > mapping.txt
$ANDROID_HOME/cmdline-tools/latest/bin/retrace mapping.txt stacktrace.txt
```
