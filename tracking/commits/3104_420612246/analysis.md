# 提交 3104：Spark 4.1: Upgrade to Spark 4.1.1 (#14946)

## 提交信息

- **序号**：3104 / 4088
- **哈希**：4206122465e7576e978e2fa56efd739c24301701
- **短哈希**：420612246
- **日期**：2026-01-12
- **作者**：Manu Zhang
- **提交说明**：Spark 4.1: Upgrade to Spark 4.1.1 (#14946)
- **PR/Issue**：#14946

## 总体目的

该提交由 Iceberg Spark 维护者 Manu Zhang 手动提交，将 Iceberg 的 Spark 4.1 集成所依赖的 Apache Spark 版本从 4.1.0 升级到 4.1.1。Iceberg 为不同的 Spark 主版本维护独立的集成模块（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`、`spark/v4.1`），每个模块通过 `gradle/libs.versions.toml` 中的版本变量（`spark34`、`spark35`、`spark40`、`spark41`）声明其编译与测试所用的 Spark 版本。`spark41` 变量专门驱动 `spark/v4.1` 模块——该模块是 Iceberg 适配 Spark 4.1 新 API 的专属集成层（含 DataSource V2、SQL 扩展、过程调用等）。

Apache Spark 4.1.1 是 Spark 4.1 系列的 patch 维护版本。Spark 4.1.0 是 4.1 大版本的首次发布，往往伴随较多的稳定性修复需求；4.1.1 作为后续 patch 版本，通常包含 SQL/ Catalyst 优化器、DataSource V2 读取路径、ANSI 模式、运行时与shuffle 等方面的 bug 修复。Iceberg 的 Spark 集成深度依赖 DataSource V2、Catalyst 表规划与扩展点，Spark 在这些子系统上的缺陷会直接影响 Iceberg 表的读写正确性与测试稳定性。因此跟随 Spark 4.1 维护版本升级，是保障 `spark/v4.1` 模块在编译、集成测试与生产使用中与 Spark 4.1 最新修复保持一致的常规维护动作。

升级为 patch 级别（4.1.0 → 4.1.1），Spark 在 4.1.x 内保持二进制与 API 兼容，预期对 Iceberg 现有 Spark 4.1 集成代码无行为影响，主要是吸收上游稳定性修复、使 CI 中的 Spark 4.1 测试矩阵跑在最新 patch 上。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中的 `spark41` 版本变量，从 `4.1.0` 改为 `4.1.1`。`spark/v4.1` 模块的各子工程通过 `version.ref = "spark41"` 引用该变量来声明 Spark 依赖（`spark-sql_2.13`、`spark-catalyst` 等），单点修改即可让整个 Spark 4.1 集成模块在编译与测试中统一使用 4.1.1。本提交未改动任何源码或测试，说明升级在 API 层面向后兼容，无需适配性修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Spark 4.1 集成所用的 Spark 版本。

**工作逻辑**：
将 `spark41 = "4.1.0"` 修改为 `spark41 = "4.1.1"`。该变量被 `spark/v4.1` 模块下所有引用 Spark 的坐标通过 `version.ref` 使用。文件同区还声明了 `spark34 = "3.4.4"`、`spark35 = "3.5.7"`、`spark40 = "4.0.1"`，分别驱动各自集成模块，本次仅升级 `spark41` 一项，不影响其他 Spark 版本的集成。升级后 `spark/v4.1` 模块的编译产物与测试将绑定 Spark 4.1.1。

## 总结

该提交将 Iceberg Spark 4.1 集成所依赖的 Apache Spark 版本从 4.1.0 手动升级到 4.1.1（patch 级别），通过单点修改 `spark41` 版本变量驱动整个 `spark/v4.1` 模块。Spark 4.1.1 是 4.1 系列的稳定性维护版本，升级使 Iceberg 的 Spark 4.1 集成与测试对齐上游最新修复；4.1.x 内 API 兼容，无需源码适配。
