# 提交 1076：Build: Upgrade to Gradle 8.10 (#10976)

## 提交信息

- **序号**：1076 / 4088
- **哈希**：24afc1f980201219fd9c7018b48f99af64977fbf
- **短哈希**：24afc1f98
- **日期**：2024-08-20 11:54:52 -0600
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Build: Upgrade to Gradle 8.10 (#10976)
- **PR/Issue**：#10976

## 总体目的

Iceberg 使用 Gradle Wrapper（`gradlew` 脚本 + `gradle-wrapper.properties`）来锁定整个项目使用的 Gradle 版本，使任何开发者和 CI 环境都能用同一版本 Gradle 进行构建，避免"在我机器上能构建"的版本漂移问题。

Gradle 8.10 于 2024-08-09 发布，相对于此前 Iceberg 使用的 8.9 版本带来了一系列改进和 bug 修复，包括：构建性能优化、配置缓存（configuration cache）稳定性提升、Kotlin DSL 改进、对 JDK 23 的更好支持、以及若干安全补丁。Iceberg 作为一个跨多个模块（core、spark、flink、trino、aws、gcp、azure 等）的大型项目，及时跟进 Gradle 版本可以：

1. 获取最新的构建性能优化，缩短 CI 时长；
2. 跟进安全补丁，避免已知漏洞；
3. 兼容更新的 JDK 版本（为新贡献者使用最新 LTS JDK 扫清障碍）；
4. 维持与 Gradle 生态（插件、依赖）的兼容性，避免长期不升级导致后续升级跨度太大、风险太高。

本提交的目的是把 Iceberg 的 Gradle Wrapper 版本从 8.9 升级到 8.10，使整个项目构建工具链保持在最新稳定版。

## 如何达成设计目的

Gradle Wrapper 的升级通过修改两个文件完成：

1. `gradle/wrapper/gradle-wrapper.properties`：更新 `distributionUrl` 指向新版二进制 zip，并同步更新 `distributionSha256Sum` 校验值，确保下载的 Gradle 分发包完整可信。
2. `gradlew`：更新内嵌的"引导下载 `gradle-wrapper.jar`"步骤中引用的 GitHub raw URL，从 `v8.9.0` 改为 `v8.10.0`，用于在 `gradle-wrapper.jar` 缺失时从 Gradle 官方仓库下载对应版本的引导 jar。

通常这类升级会通过运行 `./gradlew wrapper --gradle-version 8.10` 来自动生成，本提交的 diff 与该命令的输出一致。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties`

**修改目的**：把 Gradle Wrapper 锁定的 Gradle 版本从 8.9 升级到 8.10，并更新分发包 SHA-256 校验和。

**工作逻辑**：

```diff
-distributionSha256Sum=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
-distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
+distributionSha256Sum=5b9c5eb3f9fc2c94abaea57d90bd78747ca117ddbbf96c859d3741181a12bf2a
+distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
```

- `distributionUrl`：Gradle Wrapper 首次运行时下载 Gradle 分发包的 URL，从 `gradle-8.9-bin.zip` 改为 `gradle-8.10-bin.zip`。
- `distributionSha256Sum`：分发包的 SHA-256 校验和，用于在下载完成后验证分发包未被篡改/损坏。两个版本的校验和不同，必须同步更新，否则首次执行 `./gradlew` 时会因校验失败而拒绝解压。

其余字段（`distributionBase`、`distributionPath`、`networkTimeout=10000`、`validateDistributionUrl=true`、`zipStoreBase` 等）保持不变。

### `gradlew`

**修改目的**：同步更新 `gradlew` 脚本中"引导下载 `gradle-wrapper.jar`"的回退 URL 到 8.10.0 对应的 Gradle 仓库 tag。

**工作逻辑**：

```diff
 if [ ! -e $APP_HOME/gradle/wrapper/gradle-wrapper.jar ]; then
-    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar
+    curl -o $APP_HOME/gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradle/wrapper/gradle-wrapper.jar
 fi
```

`gradlew` 脚本在启动时检查 `gradle/wrapper/gradle-wrapper.jar` 是否存在；如果不存在（例如某些源码分发包未包含该 jar），会通过 `curl` 从 GitHub 上对应 Gradle 版本的 tag（`v8.10.0`）下载引导 jar。把 URL 从 `v8.9.0` 改为 `v8.10.0` 保证回退下载到的 jar 与 wrapper 版本匹配，避免版本错配导致的运行时错误。该回退路径在 Iceberg 正常源码检出场景下不会被触发（仓库已包含 `gradle-wrapper.jar`），仅作为容灾兜底。

## 小结

- **成效**：把 Iceberg 项目的 Gradle 构建工具链版本从 8.9 升级到 8.10，获取新版本的性能优化、bug 修复和安全补丁，保持构建系统与上游生态同步。
- **影响范围**：仅 2 个构建基础设施文件（`gradle-wrapper.properties`、`gradlew`），共 3 行改动。不影响任何业务代码、API 或运行时行为；首次构建时所有开发者/CI 会自动下载并切换到 Gradle 8.10。
- **回迁到 1.4.x 的注意事项**：本提交是构建工具升级，**可以回迁到 1.4.x 维护分支**，但需谨慎评估。回迁前需确认：(1) 1.4.x 分支当前 Gradle 版本（应与 main 同期版本，可能是 8.9）；(2) 1.4.x 的 `build.gradle`、各模块脚本是否使用了 8.10 中变更的 API（通常 Gradle 小版本升级保持向后兼容，风险低）；(3) CI 环境是否支持 Gradle 8.10 运行（需要 JDK 8+，但 Iceberg 通常要求 JDK 11/17）。Gradle 升级属于低风险改动，但 1.4.x 作为维护分支，若构建稳定可不必强行升级，除非有特定需求（如安全补丁或新 JDK 兼容）。建议在 1.4.x 上先跑一次完整构建验证后再合并。
