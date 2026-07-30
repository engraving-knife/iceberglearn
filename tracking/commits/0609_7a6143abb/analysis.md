# 提交 0609：Build: Bump guava from 33.0.0-jre to 33.1.0-jre

## 提交信息

- **序号**：0609 / 4088
- **哈希**：7a6143abbb458ae17fea4cad12c2aa86d74814b0
- **短哈希**：7a6143abb
- **日期**：2024-03-18 14:13:57 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump guava from 33.0.0-jre to 33.1.0-jre (#9977)
- **PR/Issue**：#9977

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 构建中声明的 Guava 版本从 `33.0.0-jre`（2023 年 12 月发布，Guava 33.x 系列首个版本）升级到 `33.1.0-jre`（2024 年 3 月发布），是一次 minor 级别（semver-minor）升级。与 0606–0608 不同，**Guava 是 Iceberg 的核心生产依赖**（非测试域），通过 shaded/relocated JAR 被几乎所有 Iceberg 模块消费，影响面远大于前三个测试栈升级。

升级动机是跟随 Guava 33.x 分支的最新发布，获取 33.0.0 之后累积的 bug 修复与改进。Guava 33.0.0 → 33.1.0 期间没有公开的高危 CVE 驱动（Guava 历史上的 CVE-2023-2976 `Files.createTempDir` 信息泄露已在 32.0.0 修复，CVE-2020-8908 更早），因此本升级更多是例行维护性追平，而非紧急安全补丁。33.1.0 主要包含 `Collectors`、`Cache`、`ImmutableCollections` 等模块的稳定性修复与文档改进，API 向后兼容。

在 Iceberg 中，Guava 的消费链路特殊且关键：

1. **shaded 模块 `iceberg-bundled-guava`**（`build.gradle` 第 250–288 行）：这是 Iceberg 对 Guava 的封装层。该模块以 `compileOnly(libs.guava.guava)`（第 256 行）声明对原始 Guava 的依赖，然后通过 Gradle Shadow 插件生成 shaded JAR，将 `com.google.common` 包**重定位**为 `org.apache.iceberg.relocated.com.google.common`（第 280 行 `relocate 'com.google.common', 'org.apache.iceberg.relocated.com.google.common'`），并排除 `com.google.code.findbugs`、`com.google.errorprone`、`com.google.j2objc`、`org.slf4j:slf4j-api`、`org.checkerframework:checker-qual` 等传递依赖。最终产物是一个自包含、命名空间隔离的 Guava JAR。
2. **所有 Iceberg 模块**（api、core、common、aliyun、aws、flink、spark 等约 20 个模块）通过 `implementation project(path: ':iceberg-bundled-guava', configuration: 'shadow')` 消费该 shaded JAR，在源码中使用 `org.apache.iceberg.relocated.com.google.common.*` 的 import。全代码库有 **2372 个 Java 文件**引用了 relocated guava 包，涵盖 `Preconditions`、`Lists`、`Maps`、`ImmutableList`、`ImmutableMap`、`Joiner`、`Splitter` 等高频工具类，是 Iceberg 运行时基础设施。
3. **`iceberg-core` 测试**（第 364 行）以 `testImplementation libs.guava.testlib` 直接消费原始 `guava-testlib`（使用 `com.google.common` 原始包名），用于借助 Guava 的测试套件验证 Iceberg 自定义集合类的契约正确性。

shading/relocation 设计的关键意义在于：下游用户引入 Iceberg 时不会因 Guava 版本冲突而受损——Iceberg 捆绑的 Guava 隐藏在 `org.apache.iceberg.relocated` 命名空间下，与用户自身依赖的 Guava 互不干扰。因此本升级对 Iceberg 内部生效，但不会把 Guava 版本强加给下游。

## 如何达成设计目的

由于 Iceberg 使用 Gradle Version Catalog（`gradle/libs.versions.toml`）集中管理依赖版本，整个升级只需修改一处版本声明。Version Catalog 中 `guava = "33.0.0-jre"` 改为 `guava = "33.1.0-jre"`，所有通过 `version.ref = "guava"` 引用该版本的库声明会自动跟随，包括：

- `guava-guava = { module = "com.google.guava:guava", version.ref = "guava" }`（被 `iceberg-bundled-guava` 以 `compileOnly` 消费，进入 shaded JAR）
- `guava-testlib = { module = "com.google.guava:guava-testlib", version.ref = "guava" }`（被 `iceberg-core` 以 `testImplementation` 消费）

由于是 minor 版本升级且 Guava 在 33.x 内保持 API 兼容，shaded JAR 重打包后所有 `org.apache.iceberg.relocated.com.google.common.*` 引用无需改动，2372 个源文件的 import 保持有效。relocation 机制保证了升级的"原地替换"特性：只要 Guava 不删除被 Iceberg 使用的 API（33.1.0 未删除），shaded JAR 重新构建即可，下游零感知。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Guava 版本从 33.0.0-jre 升级到 33.1.0-jre，获取 33.x 分支最新 bug 修复与改进，保持核心依赖基线新鲜。

**工作逻辑**：在版本目录的 `[versions]` 段，`guava = "33.0.0-jre"` 改为 `guava = "33.1.0-jre"`（diff 上下文位于 `google-libraries-bom` 之后、`hadoop2` 之前）。该版本号被同文件 `[libraries]` 段两条声明通过 `version.ref = "guava"` 引用：
- `guava-guava`：`iceberg-bundled-guava` 模块（第 256 行 `compileOnly(libs.guava.guava)`）消费，经 Shadow 插件重定位为 `org.apache.iceberg.relocated.com.google.common` 后输出 shaded JAR，再被约 20 个模块以 `implementation project(path: ':iceberg-bundled-guava', configuration: 'shadow')` 消费。
- `guava-testlib`：`iceberg-core` 模块（第 364 行 `testImplementation libs.guava.testlib`）消费，用于测试套件。

修改后，重新构建 `iceberg-bundled-guava` 的 `shadowJar` 任务会把 Guava 33.1.0-jre 的 `com.google.common` 字节码重定位并打包，所有依赖该 shaded JAR 的模块自动获得 33.1.0 的实现。`guava-testlib` 同步升级到 33.1.0，与生产代码使用的 Guava 版本一致，避免测试套件版本与被测代码版本错位。

## 小结

本提交通过一行 Version Catalog 版本号修改，将 Iceberg 核心生产依赖 Guava 从 33.0.0-jre 升级到 33.1.0-jre（minor 版本，API 兼容）。由于 Iceberg 通过 `iceberg-bundled-guava` shaded 模块对 Guava 进行重定位封装（`com.google.common` → `org.apache.iceberg.relocated.com.google.common`），升级在重新构建 shaded JAR 后对内生效，2372 个源文件无需改动，且不会向下游暴露 Guava 版本冲突。回迁到 1.4.x 时需注意：1.4.x 当前 guava 版本可能仍为 32.1.1-jre（未回迁 33.0.0 升级），需先回迁 33.0.0 升级再回迁本提交，或直接跳级到 33.1.0（32.1.1 → 33.1.0 跨一个 minor+patch，需验证 shaded JAR 中 `@Beta` API 是否有移除影响 Iceberg 使用）；建议执行完整 `./gradlew build` 验证 shaded JAR 重新打包无误、`iceberg-core` 的 guava-testlib 测试套件通过。
