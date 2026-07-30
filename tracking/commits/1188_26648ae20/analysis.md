# 提交 1188：Build: Upgrade to Gradle 8.10.2 (#11212)

## 提交信息

- **序号**：1188 / 4088
- **哈希**：26648ae20fac92adbc5341dea03c5b80fe72efe1
- **短哈希**：26648ae20
- **日期**：2024-09-26（Thu Sep 26 09:31:36 2024 +0200）
- **作者**：JB Onofré <jb.onofre@dremio.com>
- **提交说明**：Build: Upgrade to Gradle 8.10.2 (#11212)
- **PR/Issue**：#11212

## 总体目的

Iceberg 仓库使用 Gradle 作为构建工具，并通过 Gradle Wrapper（`gradlew` 脚本 + `gradle-wrapper.properties` 配置）固定构建时使用的 Gradle 版本，保证所有开发者与 CI 环境使用同一版本。此前仓库使用 Gradle 8.10.1，本提交将其升级到 8.10.2（一个 patch 版本升级）。

Gradle 8.10.2 是 8.10 系列的维护版本，主要包含 bug 修复与稳定性改进（例如对配置缓存、Kotlin DSL、增量编译等方面的修复）。升级目的是跟进 Gradle 上游修复，保持构建工具链的稳定性与最新性，避免长期停留在带已知 bug 的版本上。

## 如何达成设计目的

通过更新两处与 Gradle Wrapper 相关的文件：

1. `gradle/wrapper/gradle-wrapper.properties`：把 `distributionUrl` 从 `gradle-8.10.1-bin.zip` 改为 `gradle-8.10.2-bin.zip`，并同步更新 `distributionSha256Sum` 为 8.10.2 发行包的校验和（`31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26`）。`distributionSha256Sum` 用于在下载 Gradle 发行包后校验完整性，防止下载被篡改或损坏。
2. `gradlew`：脚本中有一段 fallback 逻辑——若 `gradle/wrapper/gradle-wrapper.jar` 不存在（例如从源码 tar 包检出、jar 被排除），则通过 `curl` 从 GitHub 上对应版本的仓库下载该 jar。把 URL 中的版本路径从 `v8.10.1` 改为 `v8.10.2`，保持版本一致。

执行 `./gradlew` 时，Wrapper 会读取 `gradle-wrapper.properties`，按 `distributionUrl` 下载并缓存对应版本的 Gradle，然后用它执行后续构建任务。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：声明新的 Gradle 版本与校验和。

**工作逻辑**：将以下两行：
```
distributionSha256Sum=1541fa36599e12857140465f3c91a97409b4512501c26f9631fb113e392c5bd1
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.1-bin.zip
```
改为：
```
distributionSha256Sum=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
```
其余属性（`distributionBase`、`distributionPath`、`networkTimeout`、`validateDistributionUrl`、`zipStoreBase`、`zipStorePath`）保持不变。`validateDistributionUrl=true` 仍会校验下载 URL 是否来自官方域名。

### `gradlew`

**修改目的**：同步 fallback 下载路径中的版本。

**工作逻辑**：将 fallback 逻辑中的 URL：
```
curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.10.1/gradle/wrapper/gradle-wrapper.jar
```
改为：
```
curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
```
这段逻辑仅在 `gradle-wrapper.jar` 缺失时触发，正常情况下不会执行，但保持版本一致可避免在 fallback 场景下下载到与 `gradle-wrapper.properties` 不匹配的 jar。

## 小结

- **成效**：构建工具链升级到 Gradle 8.10.2，获得上游维护版本的 bug 修复与稳定性改进。Wrapper 配置与 fallback 下载路径保持版本一致。
- **影响范围**：仅 2 个构建配置文件，共 3 行变更（2 行改 + 1 行改）。无 Java 代码、无构建脚本（build.gradle）逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是构建工具链升级，**回迁价值中等**。1.4.x 作为维护分支，若仍需发布（例如打 patch release），保持构建工具链最新有助于复现 CI 环境与获得 bug 修复，可以回迁。回迁时需注意：(1) 1.4.x 分支的 `gradle-wrapper.properties` 当前版本是否与 8.10.1 一致——若 1.4.x 已停留在更老版本（如 8.10.0 或更早），可考虑直接升级到 8.10.2 而非分两步；(2) 升级后建议在 1.4.x 的 CI 上跑一次完整构建验证，确认无兼容性问题（8.10.x 系列内部兼容性良好，风险低）；(3) `gradlew` 脚本的 fallback 路径需同步更新，避免 jar 缺失场景下版本不一致；(4) 此变更与发布物（jar 包）内容无关，仅影响构建过程，对 1.4.x 发布的二进制产物无功能影响。
