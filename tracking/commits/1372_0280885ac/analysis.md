# 提交 1372：Pig: Remove iceberg-pig (#11380)

## 提交信息

- **序号**：1372 / 4088
- **哈希**：0280885ac95bdf763556a84bb9d7c6fd9c8c5e2a
- **短哈希**：0280885ac
- **日期**：2024-11-13（Wed Nov 13 16:00:08 2024 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Pig: Remove iceberg-pig (#11380)
- **PR/Issue**：#11380

## 总体目的

Apache Pig 是一个较早期的数据流脚本引擎，Iceberg 仓库长期维护了一个 `iceberg-pig` 模块用于将 Iceberg 表接入 Pig 的 `LoadFunc` API。然而该模块长期处于"半成品"状态：`IcebergInputFormat` 中针对 `PIG` 数据模型的分支多数都是 `throw new UnsupportedOperationException("... not yet supported for Pig")`，实际上从未真正落地对 Parquet/ORC 读取和 residual 表达式求值的支持；同时 Pig 社区本身活跃度也已大幅下降。维护一个无人使用、无完整功能、却仍要参与 CI 触发路径和依赖管理的模块，对项目是负担。

本提交彻底从仓库中移除 `iceberg-pig` 模块及其在构建系统、CI 工作流、自动 labeler、README 与各文档中的引用，并清理 `mr`（MapReduce）模块中只为 Pig 而存在的代码路径（`InMemoryDataModel.PIG` 枚举值、`usePigTuples()` 配置方法以及 `IcebergInputFormat` 内多处 `case PIG` 分支）。这是一次"减负式"清理，目的是让仓库更聚焦于仍在活跃使用与维护的引擎集成（Spark、Flink、Hive/MR、Trino 等）。

## 如何达成设计目的

清理工作分四个层面进行：

1. **构建系统层面**：从 `settings.gradle` 移除 `include 'pig'` 与项目重命名条目，使 Gradle 不再把 `pig/` 当作子项目；从 `build.gradle` 删除整段 `project(':iceberg-pig') { ... }` 的依赖与测试配置；从 `gradle/libs.versions.toml` 删除 `pig = "0.17.0"` 版本声明和 `pig = { module = "org.apache.pig:pig", ... }` 库别名，使依赖目录不再包含 Pig。
2. **源码层面**：删除 `pig/` 目录下全部 5 个源文件与 1 个测试文件（`IcebergPigInputFormat`、`IcebergStorage`、`PigParquetReader`、`SchemaUtil`、`SchemaUtilTest`）。同时清理 `mr` 模块中遗留的 Pig 痕迹：从 `InputFormatConfig.InMemoryDataModel` 枚举移除 `PIG`；删除 `ConfigBuilder.usePigTuples()` 方法；从 `IcebergInputFormat` 的 `checkResiduals`、`open`、`openTask`（Parquet/ORC 分支）等若干 `switch` 语句中删除 `case PIG` 分支；同步更新 `TestIcebergInputFormats` 测试用例。
3. **CI/基础设施层面**：从 `.github/labeler.yml` 删除 `PIG` 标签规则；从 5 个 CI workflow（`delta-conversion-ci`、`flink-ci`、`hive-ci`、`kafka-connect-ci`、`spark-ci`）的触发路径列表中移除 `pig/**`，避免 PR 改动 pig 目录时再触发不相关的 CI。
4. **文档层面**：从 `README.md`、`docs/docs/api.md`、`site/docs/contribute.md` 三个对外说明文档中删除关于 `iceberg-pig` 模块的描述条目。

## 修改详情

### `settings.gradle`

**修改目的**：从 Gradle 多模块构建中剔除 `pig` 子项目。

**工作逻辑**：删除两行——`include 'pig'` 与 `project(':pig').name = 'iceberg-pig'`。从此 Gradle 不再识别 `pig/` 目录为子项目，构建时不再编译该模块。

### `build.gradle`

**修改目的**：删除 `iceberg-pig` 子项目的构建块。

**工作逻辑**：整体删除 `project(':iceberg-pig') { ... }` 这段约 33 行的配置，其中原本声明了对 `iceberg-bundled-guava`、`iceberg-api`、`iceberg-common`、`iceberg-core`、`iceberg-parquet` 的依赖，以及 `parquet-avro`（排除 avro/jackson/it.unimi.dsi）、`pig`（compileOnly，排除 junit）、`hadoop2` 相关 compileOnly 依赖、`hadoop2.minicluster`（testImplementation）。删除后构建脚本不再包含任何 Pig 相关依赖声明。

### `gradle/libs.versions.toml`

**修改目的**：从版本目录中清除 Pig 依赖。

**工作逻辑**：删除 `[versions]` 段中的 `pig = "0.17.0"` 与 `[libraries]` 段中的 `pig = { module = "org.apache.pig:pig", version.ref = "pig" }` 两行。

### `mr/src/main/java/org/apache/iceberg/mr/InputFormatConfig.java`

**修改目的**：移除 MapReduce InputFormat 中为 Pig 准备的数据模型枚举与配置入口。

**工作逻辑**：
- 在 `InMemoryDataModel` 枚举中删除 `PIG`（保留 `HIVE` 与 `GENERIC`）。
- 删除 `ConfigBuilder.usePigTuples()` 方法，该方法原本会向配置写入 `IN_MEMORY_DATA_MODEL = "PIG"`，是 Pig 集成进入 MR 读取路径的入口。

### `mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java`

**修改目的**：删除输入格式实现中针对 PIG 数据模型的 switch 分支。

**工作逻辑**：在 4 处位置删除 `case PIG:` 分支：
1. `checkResiduals` 触发条件：原 `model == HIVE || model == PIG` 简化为 `model == HIVE`（residual 求值对 HIVE 仍生效，对 GENERIC 不强制检查）。
2. `open()` 方法的 `switch (inMemoryDataModel)`：删除抛 `UnsupportedOperationException("Pig and Hive object models are not supported.")` 的 `case PIG` 分支。
3. `openTask()` 内对 value reader 的 `switch`：删除 `case PIG:`（fall-through 到 `case HIVE:` 抛"待实现"异常），保留 HIVE 分支。
4. Parquet 读取的 `switch`：删除 `case PIG: throw new UnsupportedOperationException("Parquet support not yet supported for Pig")`。
5. ORC 读取的 `switch`：删除 `case PIG: throw new UnsupportedOperationException("ORC support not yet supported for Pig")`。

这些分支此前都只会抛异常，删除后不影响任何真实可用功能。

### `mr/src/test/java/org/apache/iceberg/mr/TestIcebergInputFormats.java`

**修改目的**：同步清理测试中对 `usePigTuples()` 的调用。

**工作逻辑**：删除测试中一行 `builder.usePigTuples();`，该行原本用于验证 residual filter 不被支持时抛异常的场景，移除后该断言路径不再依赖 PIG 模型（断言本身保留，验证仍由其它路径覆盖）。

### `pig/` 目录全部文件（删除）

**修改目的**：移除整个 Pig 集成模块源码。

**工作逻辑**：删除以下 5 个源文件与 1 个测试文件：
- `pig/src/main/java/org/apache/iceberg/pig/IcebergPigInputFormat.java`（308 行）：Pig `LoadFunc` 实现。
- `pig/src/main/java/org/apache/iceberg/pig/IcebergStorage.java`（348 行）：Pig `StoreFunc` 实现。
- `pig/src/main/java/org/apache/iceberg/pig/PigParquetReader.java`（462 行）：Pig 元组读取 Parquet 文件的辅助类。
- `pig/src/main/java/org/apache/iceberg/pig/SchemaUtil.java`（171 行）：Iceberg Schema 与 Pig Schema 互转工具。
- `pig/src/test/java/org/apache/iceberg/pig/SchemaUtilTest.java`（287 行）：上述工具的单元测试。

合计约 1576 行代码被删除。

### `.github/labeler.yml`

**修改目的**：取消 PIG 标签自动打标。

**工作逻辑**：删除 `PIG:` 区块及其 `changed-files` 规则（匹配 `pig/**/*`），此后 PR 改动 pig 目录不会再被打上 PIG 标签（因目录已不存在，规则也失去意义）。

### 5 个 CI workflow 文件

**修改目的**：避免 Pig 目录改动触发不相关 CI。

**涉及文件**：`.github/workflows/delta-conversion-ci.yml`、`flink-ci.yml`、`hive-ci.yml`、`kafka-connect-ci.yml`、`spark-ci.yml`。

**工作逻辑**：在每个 workflow 的 `on.push.paths` 与 `on.pull_request.paths` 触发路径列表中删除 `'pig/**'` 一行。这样后续 PR 即使（在历史版本分支上）改动 pig 目录，也不会再触发这些引擎的 CI 构建。

### `README.md`、`docs/docs/api.md`、`site/docs/contribute.md`

**修改目的**：从对外文档中移除 Pig 模块介绍。

**工作逻辑**：分别删除三处 `* iceberg-pig is an implementation of Pig's LoadFunc API for Iceberg` 列表项，使文档不再向用户提及该模块。

## 小结

- **成效**：仓库彻底移除了长期未真正可用的 `iceberg-pig` 模块及其在构建、CI、labeler、文档、`mr` 模块代码中的全部痕迹，减少约 1646 行代码/配置；同时清理了 `IcebergInputFormat` 中若干只会抛 `UnsupportedOperationException` 的死分支，使 MR 读取路径更简洁。
- **影响范围**：纯删除型变更，无新增功能。对仍使用 Spark/Flink/Hive/MR 的用户无任何行为变化；唯一受影响的是理论上依赖 `iceberg-pig` 的用户——但鉴于 Pig 分支从未真正实现读取，此类用户不存在或无法正常工作。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。理由：(1) 这是模块删除而非 bug 修复，1.4.x 作为已发布版本分支应保持 API/模块集合稳定，删除模块属于破坏性变更；(2) Pig 模块在 1.4.x 中若仍存在，保留它对运行时无任何副作用（只是不被使用）；(3) 即便想清理 1.4.x 的构建负担，也应等下一个 minor 版本统一处理。若 1.4.x 分支构建因依赖问题需要移除 pig，可单独评估，但一般无需为此回迁。
