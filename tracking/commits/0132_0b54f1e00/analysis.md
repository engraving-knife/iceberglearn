# 提交 0132：Build: Bump arrow from 13.0.0 to 14.0.0 (#8984)

## 提交信息

- **序号**：0132 / 4088
- **哈希**：0b54f1e0071184b375ce8180a1c370290f946099
- **短哈希**：0b54f1e00
- **日期**：2023-11-06 12:28:57 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump arrow from 13.0.0 to 14.0.0 (#8984)
- **PR/Issue**：#8984

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Apache Arrow 从 13.0.0 升级到 14.0.0（一个 major 版本）。Arrow 在 Iceberg 中通过 `arrow-memory-netty` 和 `arrow-vector` 两个制品被直接用于生产环境，主要服务于 Iceberg 的列式内存数据读写（如 ORC/Parquet 之外的向量交互、Spark 与 Iceberg 之间的箭头格式桥接等场景）。

由于这是一个 semver-major 升级（13 → 14），Arrow 14 相对 13 可能包含破坏性 API 变更、已废弃 API 的移除以及新的二进制/向量格式特性。Arrow 14 的主要变化包括：对向量子集视图（`ValueVector` 的某些 API）、`VectorLoader`/`VectorSchemaRoot` 行为的调整，以及部分 `Field` 类型处理和 `ArrowBuf` 相关 API 的收紧。这类升级需要 Iceberg 在编译期和测试期同步验证所有依赖箭头向量的代码路径仍然兼容。

Dependabot 在提交说明中标注 `update-type: version-update:semver-major`，并附带两个依赖项：`org.apache.arrow:arrow-memory-netty` 与 `org.apache.arrow:arrow-vector`（均为 `direct:production`）。本次升级是 Iceberg 保持与上游 Arrow 生态同步、获取 bug 修复与性能改进的常规维护工作的一部分。

## 如何达成设计目的

设计目的很简单：在 Iceberg 的 Gradle 版本目录（version catalog）中把 `arrow` 的版本号统一从 `13.0.0` 改为 `14.0.0`。由于 Iceberg 已采用 `gradle/libs.versions.toml` 集中管理依赖版本，`arrow-memory-netty` 与 `arrow-vector` 均引用同一个 `arrow` 版本变量，因此只需改一行即可让两个制品同步升级，避免版本漂移。

## 修改详情

### [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)

**修改目的**：将 Arrow 版本变量从 13.0.0 提升到 14.0.0。

**工作逻辑**：在 `libs.versions.toml` 第 26 行附近，把 `arrow = "13.0.0"` 改为 `arrow = "14.0.0"`。该变量被同目录下 `arrow-memory-netty = { module = "org.apache.arrow:arrow-memory-netty", version.ref = "arrow" }` 和 `arrow-vector = { module = "org.apache.arrow:arrow-vector", version.ref = "arrow" }` 引用（以及其他可能通过 `version.ref` 引用的位置），所以这一处改动会同时拉高两个制品的版本。改动量极小（1 行 / 1 增 1 删），但属于跨大版本升级，理论上需要完整构建与测试矩阵验证兼容性。

## 小结

由 Dependabot 自动完成的 Arrow 13→14 major 升级，通过 version catalog 单行修改同步刷新 `arrow-memory-netty` 与 `arrow-vector`，是 Iceberg 依赖生态常规维护的一环。
