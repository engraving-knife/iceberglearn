# 提交 0377：Build: Bump minor version for Spark-3.3 (#9492)

## 提交信息

- **序号**：0377
- **哈希**：99958d96ea97b05e8605db205f94bd777e88f195
- **短哈希**：99958d96e
- **日期**：2024-01-17 17:46:34 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Build: Bump minor version for Spark-3.3 (#9492)
- **PR/Issue**：#9492

## 总体目的

这个提交将 Iceberg 针对 Spark 3.3 分支依赖的 Spark 版本从 `3.3.3` 升级到 `3.3.4`，属于常规的依赖维护（patch 级版本提升）。

Iceberg 为不同的 Spark 大版本维护独立的构建变体（`spark-hive33`、`spark-hive34`、`spark-hive35`），分别在 `gradle/libs.versions.toml` 这个版本目录（version catalog）中声明所依赖的 Spark 版本。`3.3.4` 是 Spark 3.3 系列的第四个维护版本，通常包含 bug 修复、安全补丁和小幅改进，不引入破坏性 API 变更。将依赖锁定到最新的 patch 版本可以让 Iceberg 的 Spark 3.3 集成模块在构建和测试时基于 Spark 社区最新的稳定修复，及时获得上游修复的收益，并降低未来某天用户拿着 3.3.4 来报问题时因 Iceberg CI 仍跑在 3.3.3 而无法复现的风险。

值得注意的是，这次升级只动 `spark-hive33` 一项，并未同步改动 `spark-hive34`（仍为 `3.4.2`）和 `spark-hive35`（仍为 `3.5.0`），说明这是一次针对 Spark 3.3 单一系列的定点升级，而非全量依赖巡检。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `spark-hive33` 别名对应的版本字符串，从 `3.3.3` 改为 `3.3.4`。由于 Iceberg 使用版本目录统一管理依赖坐标，所有引用 `spark-hive33` 的模块（如 `spark/v3.3/`）会自动继承新版本，无需在多处分散修改，保证了一致性。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Spark 3.3 系列依赖版本从 `3.3.3` 提升到 `3.3.4`，纳入上游最新维护版本。

**工作逻辑**：

在版本目录的 `[versions]` 区块中，将：

```toml
spark-hive33 = "3.3.3"
```

改为：

```toml
spark-hive33 = "3.3.4"
```

紧随其后的 `spark-hive34 = "3.4.2"` 与 `spark-hive35 = "3.5.0"` 保持不变。`libs.versions.toml` 是 Gradle 7 引入的版本目录机制，Iceberg 用它集中声明所有第三方依赖版本，再在各子模块的 `build.gradle` 中以 `libs.spark.hive33` 这类引用来消费。因此这一处字符串改动会传播到所有依赖 `spark-hive33` 的 Spark 3.3 子模块的构建与测试 classpath，实现"一处修改，处处生效"。

## 小结

这是一个典型的小型依赖维护提交：在版本目录中把 Spark 3.3 的 patch 版本抬高一档，使 Iceberg 的 Spark 3.3 集成模块跟上 Spark 社区的最新稳定修复。改动面极小、风险极低，但体现了项目对依赖版本卫生的持续关注。这类"bump"提交在大型多模块项目里是日常 CI 健康的基础保障。
