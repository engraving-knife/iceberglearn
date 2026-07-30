# 提交 1640：Build: Upgrade to Gradle 8.12.1 (#12093)

## 提交信息

- **序号**：1640 / 4088
- **哈希**：8ce1b32ecc2d9ed3e5000ae6a5eb663037eabfe8
- **短哈希**：8ce1b32ec
- **日期**：2025-01-25（Sat Jan 25 07:21:41 2025 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build: Upgrade to Gradle 8.12.1
- **PR/Issue**：#12093

## 总体目的

Iceberg 项目使用 Gradle Wrapper 锁定全项目使用的 Gradle 版本，确保所有开发者与 CI 跑的是同一个版本。本次提交把 Gradle Wrapper 从 8.12 升级到 8.12.1。

Gradle 8.12.1 是 8.12 的一个补丁版本，主要修复 8.12 中发现的问题（典型为构建稳定性、增量构建/配置缓存相关回归、Kotlin DSL 与某些插件兼容性等）。8.12 引入了一批新特性（如改进的配置缓存、Isolated Projects 等），社区在落地过程中报告了一些 bug，8.12.1 修复后更稳妥。把 wrapper 升到 8.12.1 让 Iceberg 的构建跟上 Gradle 最新补丁，避免踩到 8.12 的已知问题。

## 如何达成设计目的

通过更新 Gradle Wrapper 配置实现：

1. `gradle/wrapper/gradle-wrapper.properties` 中：
   - `distributionSha256Sum` 更新为 8.12.1 发行包的 SHA256（`8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94`）；
   - `distributionUrl` 指向 `gradle-8.12.1-bin.zip`。
2. `gradlew` 启动脚本中：当本地缺失 `gradle-wrapper.jar` 时回退下载的 URL 从 `v8.12.0` 改为 `v8.12.1`（保持与 wrapper.properties 一致）。

无需修改任何业务代码或 build.gradle。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`（修改，+2/-2）

**修改目的**：把 Gradle Wrapper 锁定的发行版从 8.12 切到 8.12.1。

**工作逻辑**：

```
-distributionSha256Sum=7a00d51fb93147819aab76024feece20b6b84e420694101f276be952e08bef03
-distributionUrl=https\://services.gradle.org/distributions/gradle-8.12-bin.zip
+distributionSha256Sum=8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94
+distributionUrl=https\://services.gradle.org/distributions/gradle-8.12.1-bin.zip
```

- `distributionSha256Sum` 用于校验下载的 Gradle 发行包完整性，避免被篡改或下载不完整；
- `distributionUrl` 是 Gradle Wrapper 实际下载的版本；
- 升级后，开发者首次运行 `./gradlew` 会自动下载 8.12.1 并校验 SHA256。

### `gradlew`（修改，+1/-1）

**修改目的**：保持 `gradlew` 的回退下载 URL 与 wrapper.properties 一致。

**工作逻辑**：

```
-    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.12.0/gradle/wrapper/gradle-wrapper.jar
+    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.12.1/gradle/wrapper/gradle-wrapper.jar
```

`gradlew` 在发现本地缺失 `gradle-wrapper.jar` 时（典型场景：从源码包而非 git clone 获取代码，或 wrapper.jar 被 .gitignore 忽略），会从 GitHub 上对应版本的 gradle 仓库下载 wrapper.jar。这里把版本号同步到 8.12.1，避免下载到旧版本 wrapper.jar 与新 properties 不匹配。

## 小结

- **成效**：把项目 Gradle 版本升级到 8.12.1（8.12 的补丁版），获得 8.12 系列的最新稳定性修复；不影响业务代码，只动构建工具版本。
- **影响范围**：仅 `gradle/wrapper/gradle-wrapper.properties` 与 `gradlew` 两个构建脚手架文件。开发者首次构建会重新下载 8.12.1 发行包（约 100+MB）。CI 环境若已缓存 8.12 需重新下载 8.12.1。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支若仍在 8.12（或更早），回迁本提交前需确认 1.4.x 上的 build.gradle 与插件兼容 8.12.1（一般补丁版兼容，但若 1.4.x 用了某些第三方插件对新 Gradle 敏感需测试）；
  - SHA256 校验值必须与官方 8.12.1 发行包匹配，否则构建会报校验失败；
  - 回迁安全，纯构建工具版本升级；建议在 1.4.x CI 上跑一次完整构建验证。
