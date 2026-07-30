# 提交 1046：Build: Bump Spark 3.5 to 3.5.2 (#10918)

## 提交信息

- **序号**：1046 / 4088
- **哈希**：ae08334cad1f1a9eebb9cdcf48ce5084da9bc44d
- **短哈希**：ae08334ca
- **日期**：2024-08-12（Mon Aug 12 14:12:20 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Build: Bump Spark 3.5 to 3.5.2 (#10918)
- **PR/Issue**：#10918

## 总体目的

Apache Spark 3.5 系列发布了补丁版本 3.5.2，包含若干 bug 修复与改进。Iceberg 的 Spark 集成模块（`spark/v3.5`）需要将构建依赖的 Spark 3.5 版本从 3.5.1 升级到 3.5.2，以跟进上游补丁、获取修复，并确保 Iceberg 在 Spark 3.5.2 上构建与测试通过。这是常规的依赖版本跟进，保持 Iceberg Spark 运行时与最新 Spark 3.5 补丁版本对齐。

## 如何达成设计目的

通过修改 Gradle 版本目录（version catalog）中 Spark 3.5 对应的版本变量，将 `spark-hive35` 从 `3.5.1` 提升到 `3.5.2`。所有引用该变量的模块（spark/v3.5 等）会自动采用新版本，无需逐模块修改构建脚本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Spark 3.5 依赖版本从 3.5.1 升级到 3.5.2。

**工作逻辑**：在版本目录中将 `spark-hive35 = "3.5.1"` 改为 `spark-hive35 = "3.5.2"`。该变量是 Spark 3.5（hive 3.5 兼容）系列依赖的统一版本号，被 spark/v3.5 模块的 Spark、Spark Hive 相关依赖引用。升级后构建与测试均基于 Spark 3.5.2 进行。

## 小结

- **成效**：将 Iceberg 的 Spark 3.5 集成依赖跟进到 3.5.2，获取上游补丁修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行版本号改动，影响 spark/v3.5 模块的构建依赖。
- **回迁到 1.4.x 的注意事项**：需视 1.4.x 分支是否支持 Spark 3.5 而定。1.4.x 主要面向 Spark 3.3/3.4/3.5，若 1.4.x 已包含 spark/v3.5 模块且使用相同版本变量，则可回迁以获取 3.5.2 补丁；但应确认 1.4.x 的 spark/v3.5 模块结构与 API 兼容性，以及 3.5.2 不引入破坏 Iceberg 集成的变更。属于低风险的依赖版本提升，通常可直接 cherry-pick 该单行改动。
