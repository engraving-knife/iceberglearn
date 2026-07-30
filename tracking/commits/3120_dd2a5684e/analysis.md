# 提交 3120：Spark 3.5: Upgrade to Spark 3.5.8 (#15033)

## 提交信息

- **序号**：3120 / 4088
- **哈希**：dd2a5684e349fb2276680dc667973933b0b53e45
- **短哈希**：dd2a5684e
- **日期**：2026-01-16 16:46:03 +0100
- **作者**：Manu Zhang
- **提交说明**：Spark 3.5: Upgrade to Spark 3.5.8 (#15033)
- **PR/Issue**：#15033

## 总体目的

Iceberg 为 Spark 提供了按主版本分模块的集成（v3.4、v3.5、v4.0、v4.1），每个模块在 Gradle 版本目录 `gradle/libs.versions.toml` 中维护对应 Spark 版本号，并在构建时以此版本拉取 `spark-hive` 等依赖进行编译与测试。本提交将 Spark 3.5 模块依赖的 Spark 版本从 `3.5.7` 升级到 `3.5.8`。

`3.5.8` 是 Spark 3.5.x 系列的一个维护版本（patch release），属于语义版本中的修订号升级（PATCH）。按语义版本约定，这类升级仅包含 bug 修复与小的改进，不引入破坏性 API 变更，理论上向前兼容。Iceberg 跟进此类 patch 升级的动机是：及时获取 Spark 3.5 分支上的缺陷修复与稳定性改进，保持集成模块与上游 Spark 修复保持同步，避免在用户上报的问题中命中已修复的 Spark bug。

## 如何达成设计目的

只需在 Gradle 版本目录 `gradle/libs.versions.toml` 中将 `spark35` 版本字符串从 `"3.5.7"` 改为 `"3.5.8"` 即可。所有通过 `libs.versions.spark35.get()` 引用该版本的构建脚本会自动应用新版本，无需逐文件修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Spark 3.5 集成模块依赖的 Spark 版本升至 3.5.8。

**工作逻辑**：
该文件是 Gradle 版本目录，集中管理依赖版本。第 88 行 `spark35 = "3.5.7"` 改为 `spark35 = "3.5.8"`。在 `spark/v3.5/build.gradle` 中，`spark35` 被用于声明 `org.apache.spark:spark-hive_${scalaVersion}` 的 `compileOnly` 与 `integrationImplementation`/`testImplementation` 依赖（如 `compileOnly("org.apache.spark:spark-hive_${scalaVersion}:${libs.versions.spark35.get()}")`），即 Spark 3.5 模块编译、测试与集成测试所用的 Spark 运行时版本。升级后构建会拉取 Spark 3.5.8 的 artifact，使 Iceberg Spark 3.5 集成在 3.5.8 上构建与测试。由于是 patch 升级，预期不影响公共 API 兼容性，主要影响是纳入 Spark 3.5.8 的 bug 修复。

## 总结

本提交是一次纯粹的依赖版本跟进，将 Spark 3.5 集成模块的 Spark 版本从 3.5.7 升至 3.5.8（patch 级别）。改动仅一行版本目录声明，影响范围限于 Spark 3.5 模块的编译与测试依赖，预期带来上游修复而无破坏性影响，体现了项目对上游依赖的及时维护。
