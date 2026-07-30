# 提交 2064：Spark: Update RewriteDataFilesSparkAction and RewritePositionDeleteFilesSparkAction to use the new APIs (#12692)

## 提交信息

- **序号**：2064 / 4088
- **哈希**：bcda1a339eecac1e02758638583771fe24998f54
- **短哈希**：bcda1a339
- **日期**：2025-04-30 16:36:43 +0200
- **作者**：pvary
- **提交说明**：Spark: Update RewriteDataFilesSparkAction and RewritePositionDeleteFilesSparkAction to use the new APIs (#12692)
- **PR/Issue**：#12692

## 总体目的

Iceberg actions 模块正在引入新的"规划器/执行器分离"API：把"文件重写"拆成 `FileRewritePlanner`（规划文件组）与 `FileRewriteRunner`（执行重写）两个独立接口，替代原来 `FileRewriter` 把规划与执行耦合在一起的旧 API。本提交把 Spark 3.5 模块中的两个核心 action——`RewriteDataFilesSparkAction` 与 `RewritePositionDeleteFilesSparkAction`——从旧 `FileRewriter` API 迁移到新 `BinPackRewriteFilePlanner` + `FileRewriteRunner` API，并把原有的各种 Spark 文件重写器（BinPack/Sort/ZOrder/Shuffling 以及 PositionDeletes）重构为新 API 下的 Runner 子类，从而与新 API 对齐、消除重复逻辑、便于后续扩展（如自定义 planner/runner 注入）。

## 如何达成设计目的

整体重构策略：
1. **引入抽象基类 `SparkRewriteRunner`**：实现 `FileRewriteRunner` 接口，封装 `SparkSession`/`Table`、`validOptions`/`init` 默认实现、`spec(int)` 等公共方法。泛型参数 `<I, T, F, G>` 分别为 plan info、scan task、content file、rewrite group 类型，使数据文件重写与位置删除重写能共用基类。
2. **Runner 子类化**：把原有 7 个 `*Rewriter` 类重命名/重构为 `*RewriteRunner`/`*FileRewriteRunner`，并改为继承 `SparkRewriteRunner`（或其中间基类 `SparkDataFileRewriteRunner`）：
   - `SparkBinPackDataRewriter` → `SparkBinPackFileRewriteRunner`
   - `SparkSizeBasedDataRewriter` → `SparkDataFileRewriteRunner`（数据文件 runner 的公共基类）
   - `SparkBinPackPositionDeletesRewriter` → `SparkRewritePositionDeleteRunner`
   - `SparkShufflingDataRewriter` → `SparkShufflingFileRewriteRunner`
   - `SparkSortDataRewriter` → `SparkSortFileRewriteRunner`
   - `SparkZOrderDataRewriter` → `SparkZOrderFileRewriteRunner`
3. **新增 `SparkShufflingDataRewritePlanner`**：继承 `BinPackRewriteFilePlanner`，为 sort/zOrder/shuffle 重写提供规划逻辑，并引入 `compression-factor` 选项，在 `expectedOutputFiles` 中按压缩因子调整预期输出文件数（解决 shuffle 后压缩比变化导致文件数估算偏差）。
4. **Action 改造**：`RewriteDataFilesSparkAction` 把字段从 `FileRewriter<FileScanTask, DataFile> rewriter` 改为 `BinPackRewriteFilePlanner planner` + `FileRewriteRunner<...> runner`；`binPack()`/`sort()`/`zOrder()` 设置 runner，并新增 `ensureRunnerNotSet()` 守卫；`execute()` 改为 `planner.plan()` + `runner.rewrite()` 的新流程，移除原先 `planFileGroups`/`validateAndInitOptions` 等内联逻辑。`RewritePositionDeleteFilesSparkAction` 同样迁移到新 planner/runner。
5. **测试迁移**：删除旧 `TestSparkFileRewriter`，新增 `TestSparkFileRewriteRunners` 与 `TestSparkShufflingDataRewritePlanner` 测试新 API；更新 `TestRewriteDataFilesAction`/`TestRewritePositionDeleteFilesAction`/benchmark 等以适配新 API。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkRewriteRunner.java` (新增, +82 lines)

**修改目的**：Spark 文件重写 runner 的抽象基类。

**工作逻辑**：
泛型抽象类 `SparkRewriteRunner<I, T extends ContentScanTask<F>, F extends ContentFile<F>, G extends RewriteGroupBase<I,T,F>>` 实现 `FileRewriteRunner<I,T,F,G>`。持有 `SparkSession spark` 与 `Table table`，提供 `spark()`/`table()` 访问器；`validOptions()` 返回空集，`init(Map)` 空实现；`spec(int specId)` 从 `table().specs()` 取分区 spec。子类负责实现 `rewrite` 与具体 `doRewrite` 逻辑。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingDataRewritePlanner.java` (新增, +82 lines)

**修改目的**：为 shuffle/sort/zOrder 重写提供规划器，引入压缩因子。

**工作逻辑**：
继承 `BinPackRewriteFilePlanner`。新增 `COMPRESSION_FACTOR` 选项（默认 1.0），`init` 中读取该值，重写 `expectedOutputFiles(inputSize)` 为 `super.expectedOutputFiles((long)(inputSize * compressionFactor))`，使压缩比较高时按更小的有效输入大小估算输出文件数，避免输出文件过多。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (修改, 大量删减)

**修改目的**：迁移到 planner + runner 新 API。

**工作逻辑**：
- 字段：`rewriter` → `planner` + `runner`。
- `binPack()`/`sort()`/`sort(sortOrder)`/`zOrder(...)` 改为创建对应 `Spark*FileRewriteRunner`，并经 `ensureRunnerNotSet()` 守卫。
- `execute()` 中原先的 `validateAndInitOptions`/`planFileGroups`/`RewriteExecutionContext` 等内联规划逻辑被替换为 `init(startingSnapshotId)` + `planner.plan()`，得到 `FileRewritePlan`，再由 `runner.rewrite(...)` 执行；后续 commit/部分进度等流程保留但适配新数据结构。
- 移除大量已不再需要的 import（`CloseableIterable`、`StructLikeMap`、`GenericRecord`、`RewriteJobOrder` 等）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (修改, 大量删减)

**修改目的**：迁移到新 API。

**工作逻辑**：
类似 `RewriteDataFilesSparkAction`，把内联规划与执行逻辑替换为 `BinPackRewriteFilePlanner` + `SparkRewritePositionDeleteRunner`。

### Runner 重命名/重构文件（R 重命名）

- `SparkBinPackDataRewriter.java` → `SparkBinPackFileRewriteRunner.java`：继承 `SparkDataFileRewriteRunner`，`description()` 返回 "BIN-PACK"，`doRewrite` 用 `SCAN_TASK_SET_ID`/`SPLIT_SIZE` 读、`REWRITTEN_FILE_SCAN_TASK_SET_ID`/`TARGET_FILE_SIZE_BYTES`/`DISTRIBUTION_MODE` 写，并在 spec 不一致时触发 repartition。
- `SparkSizeBasedDataRewriter.java` → `SparkDataFileRewriteRunner.java`：作为数据文件 runner 公共基类，封装 size-based 的读/写公共逻辑。
- `SparkBinPackPositionDeletesRewriter.java` → `SparkRewritePositionDeleteRunner.java`：继承 `SparkRewriteRunner<RewritePositionDeleteFiles.FileGroupInfo, PositionDeletesScanTask, DeleteFile, RewritePositionDeletesGroup>`，禁用 AQE，处理 position deletes 元数据表的重写。
- `SparkShufflingDataRewriter.java` → `SparkShufflingFileRewriteRunner.java`：继承 `SparkDataFileRewriteRunner`，负责 shuffle/sort/zOrder 的执行（带 shuffle）。
- `SparkSortDataRewriter.java` → `SparkSortFileRewriteRunner.java`：sort runner，配置 sort order。
- `SparkZOrderDataRewriter.java` → `SparkZOrderFileRewriteRunner.java`：zOrder runner，配置 zOrder 列。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriteRunners.java` (新增, +139 lines)

**修改目的**：测试新 runner API。

**工作逻辑**：
针对新的 `SparkRewriteRunner` 子类体系进行单元测试，验证 runner 的描述、选项、init 等行为。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkFileRewriter.java` (删除, -462 lines)

**修改目的**：移除旧 `FileRewriter` API 的测试。

**工作逻辑**：
该测试针对旧的 `Spark*DataRewriter` 类，已被 `TestSparkFileRewriteRunners` 取代。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSparkShufflingDataRewritePlanner.java` (新增, +92 lines)

**修改目的**：测试 `SparkShufflingDataRewritePlanner` 的 `compression-factor` 选项。

**工作逻辑**：
验证 `expectedOutputFiles` 在不同 `compression-factor` 下的计算结果，以及 `validOptions`/`init` 行为。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (修改, -大量行)

**修改目的**：适配新 API。

**工作逻辑**：
移除针对旧 rewriter 私有方法的测试，改为通过公共 action API 测试。部分原先直接构造 rewriter 的用例改用 `binPack()`/`sort()`/`zOrder()` 入口。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java` (修改)

**修改目的**：适配新 API。

**工作逻辑**：
同上，改为通过 action 公共 API 测试。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (修改, +1/-1)

**修改目的**：小调整以适配新行为。

**工作逻辑**：
测试期望的小幅调整。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFiles.java` (修改)

**修改目的**：适配新行为。

**工作逻辑**：
小幅调整。

### `spark/v3.5/spark/src/jmh/java/org/apache/iceberg/spark/action/IcebergSortCompactionBenchmark.java` (修改)

**修改目的**：benchmark 适配新 API。

**工作逻辑**：
把 benchmark 中对旧 rewriter 的直接使用改为新 runner API。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestCompressionSettings.java` (修改)

**修改目的**：适配新 API。

**工作逻辑**：
小幅调整以匹配新 runner 的压缩设置入口。

## 总结

本提交把 Spark 3.5 的 `RewriteDataFilesSparkAction` 与 `RewritePositionDeleteFilesSparkAction` 从旧 `FileRewriter` API 迁移到新的 `BinPackRewriteFilePlanner` + `FileRewriteRunner` 分离 API。引入 `SparkRewriteRunner` 抽象基类与 `SparkShufflingDataRewritePlanner`（带 compression-factor），把 6 个旧 rewriter 重构为 runner 子类，并更新/新增对应测试。整体代码量减少约 450 行（680 增 / 1129 删），架构更清晰、可扩展性更好。
