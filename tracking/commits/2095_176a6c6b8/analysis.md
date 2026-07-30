# 提交 2095：Spark 3.4: Update RewriteDataFilesSparkAction and RewritePositionDeleteFilesSparkAction to use the new APIs (#12980)

## 提交信息

- **序号**：2095 / 4088
- **哈希**：176a6c6b824065771c5ac5f8e797d0bd6ed3508c
- **短哈希**：176a6c6b
- **日期**：2025-05-07 16:37:36 +0200
- **作者**：gaborkaszab <gaborkaszab@cloudera.com>
- **提交说明**：Spark 3.4: Update RewriteDataFilesSparkAction and RewritePositionDeleteFilesSparkAction to use the new APIs (#12980)
- **PR/Issue**：#12980

## 总体目的

Iceberg 核心近期引入了一套新的 actions API，把"文件重写"拆分为"计划（Planner）"与"执行（Runner）"两个独立接口：`BinPackRewriteFilePlanner` / `BinPackRewritePositionDeletePlanner` 负责 scan 与分组，`FileRewriteRunner` 负责对单个 group 执行重写。新 API 还引入了 `FileRewritePlan`、`RewriteFileGroup`、`RewritePositionDeletesGroup`、`RewriteGroupBase` 等抽象，把原本散落在各 Spark action 里的"按分区收集 scan task → 调用 rewriter.planFileGroups → 构造 RewriteExecutionContext → 转成 group stream"的样板逻辑下沉到核心。

本次提交把 Spark 3.4 模块中的 `RewriteDataFilesSparkAction` 与 `RewritePositionDeleteFilesSparkAction` 迁移到这套新 API，把原来的 `SparkBinPackDataRewriter` / `SparkSortDataRewriter` / `SparkZOrderDataRewriter` / `SparkBinPackPositionDeletesRewriter` 这一类"既做计划又做执行"的 `FileRewriter` 实现，重构成纯执行端的 `Spark*FileRewriteRunner` / `SparkRewritePositionDeleteRunner`，并新增 `SparkShufflingDataRewritePlanner` 承担 shuffle 类重写的计划逻辑。重构后 action 类大幅瘦身，去除自定义的 `RewriteExecutionContext`、`planFileGroups`、`groupByPartition`、`toGroupStream` 等私有方法，统一交给核心的 Planner 产出 `FileRewritePlan`。

## 如何达成设计目的

1. **引入 `SparkRewriteRunner` 抽象基类**：实现新的 `FileRewriteRunner<I,T,F,G>` 接口，封装 `SparkSession`、`Table`、`spec(int)` 等公共属性，提供 `validOptions()` / `init(Map)` 默认实现，子类只需实现 `rewrite(G)` 与 `description()`。
2. **`SparkDataFileRewriteRunner`（数据文件 runner 基类）**：封装 `SparkTableCache` / `ScanTaskSetManager` / `FileRewriteCoordinator` 三件套的生命周期管理——`rewrite(group)` 中生成 UUID 作为 groupId，注册表与 task set，调用子类 `doRewrite`，最后从 coordinator 拉取新生成的文件并清理。
3. **具体 runner**：`SparkBinPackFileRewriteRunner`（bin-pack）、`SparkShufflingFileRewriteRunner`（shuffle/sort/zorder 公共基类，处理分布与排序、`SHUFFLE_PARTITIONS_PER_FILE`）、`SparkSortFileRewriteRunner`、`SparkZOrderFileRewriteRunner`、`SparkRewritePositionDeleteRunner`（position delete，独立实现 `rewrite`，使用 `PositionDeletesRewriteCoordinator`）。
4. **`SparkShufflingDataRewritePlanner`**：继承核心 `BinPackRewriteFilePlanner`，新增 `COMPRESSION_FACTOR` 选项，重写 `expectedOutputFiles` 以按压缩因子调整预期输出文件数，用于 shuffle 类重写。
5. **Action 改造**：
   - `RewriteDataFilesSparkAction`：用 `runner`（`FileRewriteRunner`）+ `planner`（`BinPackRewriteFilePlanner` 或 `SparkShufflingDataRewritePlanner`）替代原 `rewriter`（`FileRewriter`）；`binPack/sort/zOrder` 方法改为实例化对应 runner；`execute` 中 `planner.plan()` 直接得到 `FileRewritePlan`，删除 `planFileGroups`/`groupByPartition`/`toGroupStream`/`RewriteExecutionContext` 等私有逻辑；`doExecute` / `doExecuteWithPartialProgress` 改为接收 `FileRewritePlan`，遍历 `plan.groups()`；`validateAndInitOptions` 同时校验并初始化 planner 与 runner。
   - `RewritePositionDeleteFilesSparkAction`：用 `BinPackRewritePositionDeletePlanner` + `SparkRewritePositionDeleteRunner` 替代 `SparkBinPackPositionDeletesRewriter`，同样删除自定义分组逻辑。
6. **测试迁移**：删除老的 `TestSparkFileRewriter`（462 行），新增 `TestSparkFileRewriteRunners`（runner 构造参数校验）与 `TestSparkShufflingDataRewritePlanner`（planner 的 `COMPRESSION_FACTOR` 与 `expectedOutputFiles`）；既有 `TestRewriteDataFilesAction` / `TestRewritePositionDeleteFilesAction` 适配新 API（例如改用 `inputFileNum()` 替代 `numFiles()`，移除对 `RewriteExecutionContext` 的引用）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (修改, +82/-193 lines)

**修改目的**：迁移到 Planner/Runner 新 API，去除自定义计划逻辑。

**工作逻辑**：
- 字段 `FileRewriter<FileScanTask, DataFile> rewriter` 替换为 `BinPackRewriteFilePlanner planner` + `FileRewriteRunner<...> runner`；删除 `RewriteJobOrder rewriteJobOrder`。
- `binPack()`/`sort()`/`sort(SortOrder)`/`zOrder(...)` 改为实例化 `SparkBinPackFileRewriteRunner`/`SparkSortFileRewriteRunner`/`SparkZOrderFileRewriteRunner`，并通过新增 `ensureRunnerNotSet()` 保证只设置一次。
- `execute()`：`init(startingSnapshotId)` 中根据 runner 是否为 `SparkShufflingFileRewriteRunner` 选择 `SparkShufflingDataRewritePlanner` 或 `BinPackRewriteFilePlanner`；若 runner 为 null 则默认 bin-pack；调用 `planner.plan()` 得到 `FileRewritePlan`，`plan.totalGroupCount()==0` 直接返回空结果，否则按 partialProgress 选择 `doExecute`/`doExecuteWithPartialProgress`。
- `rewriteFiles(plan, fileGroup)`：调用 `runner.rewrite(fileGroup)`（而非旧的 `rewriter.rewrite(fileGroup.fileScans())`），其余 commit 流程不变。
- `doExecute`/`doExecuteWithPartialProgress`：参数从 `RewriteExecutionContext + Stream<RewriteFileGroup>` 改为 `FileRewritePlan`，遍历 `plan.groups()`；`groupsPerCommit` 用 `plan.totalGroupCount()` 计算；失败结果中 `dataFilesCount` 改用 `fileGroup.inputFileNum()`。
- `validateAndInitOptions`：合法选项集 = runner.validOptions + VALID_OPTIONS + planner.validOptions；分别 `planner.init(options())` 与 `runner.init(options())`；移除 `REWRITE_JOB_ORDER` 解析。
- `jobDesc` 用 `plan.totalGroupCount()` / `plan.groupsInPartition(partition)` 替代 `RewriteExecutionContext`。
- 删除内部类 `RewriteExecutionContext` 及 `planFileGroups`/`groupByPartition`/`fileGroupsByPartition`/`toGroupStream`/`newRewriteGroup` 等私有方法。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (修改, +33/-175 lines)

**修改目的**：同样迁移到 Planner/Runner 新 API。

**工作逻辑**：
- 字段 `SparkBinPackPositionDeletesRewriter rewriter` 替换为 `BinPackRewritePositionDeletePlanner planner` + `SparkRewritePositionDeleteRunner runner`（runner 在构造函数中创建）。
- `execute()`：创建 `BinPackRewritePositionDeletePlanner(table, filter, caseSensitive)`，`planner.plan()` 得到 `FileRewritePlan<...>`，0 组返回空结果，否则按 partialProgress 分派。
- `rewriteDeleteFiles(plan, fileGroup)`：调用 `runner.rewrite(fileGroup)`。
- `doExecute`/`doExecuteWithPartialProgress` 与数据文件版对称，参数改为 `FileRewritePlan`。
- `validateAndInitOptions`：合法选项集 = runner.validOptions + VALID_OPTIONS + planner.validOptions（注意原代码有重复 add planner 的小瑕疵）。
- 删除 `planFileGroups`/`planFiles`/`groupByPartition`/`fileGroupsByPartition`/`toGroupStream`/`newRewriteGroup` 及内部类 `RewriteExecutionContext`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkRewriteRunner.java` (新增, +82/-0 lines)

**修改目的**：Spark 文件重写 runner 的抽象基类。

**工作逻辑**：泛型参数 `<I, T extends ContentScanTask<F>, F extends ContentFile<F>, G extends RewriteGroupBase<I,T,F>>`，实现 `FileRewriteRunner<I,T,F,G>`；持有 `SparkSession` 与 `Table`，提供 `spark()`/`table()`/`spec(int)` 访问器；`validOptions()` 返回空集，`init(Map)` 空实现，作为默认。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkDataFileRewriteRunner.java` (新增, +61/-0 lines)

**修改目的**：数据文件重写 runner 的公共基类，管理 Spark 重写三件套生命周期。

**工作逻辑**：继承 `SparkRewriteRunner<FileGroupInfo, FileScanTask, DataFile, RewriteFileGroup>`，持有 `SparkTableCache`/`ScanTaskSetManager`/`FileRewriteCoordinator`。`rewrite(RewriteFileGroup group)`：生成 UUID groupId → `tableCache.add` + `taskSetManager.stageTasks` → 调用子类 `doRewrite(groupId, group)` → `coordinator.fetchNewFiles` 取回新文件 → finally 清理三件套。子类只需实现 `doRewrite`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkBinPackFileRewriteRunner.java` (新增, +71/-0 lines)

**修改目的**：bin-pack 数据文件重写 runner（原 `SparkBinPackDataRewriter` 的执行部分）。

**工作逻辑**：`doRewrite` 用 `SparkReadOptions.SCAN_TASK_SET_ID` + `SPLIT_SIZE` + `FILE_OPEN_COST=0` 读取，再用 `SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID` + `TARGET_FILE_SIZE_BYTES` + `DISTRIBUTION_MODE` + `OUTPUT_SPEC_ID` append 写回；`distributionMode` 在输出 spec 与原 spec 不一致时触发 RANGE 重分区，否则 NONE。`description()` 返回 `"BIN-PACK"`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingFileRewriteRunner.java` (新增, +215/-0 lines)

**修改目的**：shuffle/sort/zorder 重写的公共基类。

**工作逻辑**：继承 `SparkDataFileRewriteRunner`，引入 `SHUFFLE_PARTITIONS_PER_FILE` 选项（默认 1）；定义抽象 `sortOrder()`、`sortedDF(df, sortFunc)`、`schema()` 等；`doRewrite` 中根据 `inputSize` 与 `targetFileSize` 计算期望输出文件数，应用 `Distributions.ordered` / `RequiresDistributionAndOrdering` 注入分布与排序，必要时用 `OrderAwareCoalesce` 把多个 shuffle 分区合并回单文件；处理 `numShufflePartitionsPerFile > 1` 的内存友好场景。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkSortFileRewriteRunner.java` (新增, +64/-0 lines)

**修改目的**：sort 数据文件重写 runner（原 `SparkSortDataRewriter`）。

**工作逻辑**：继承 `SparkShufflingFileRewriteRunner`，构造时校验表或参数 `SortOrder` 已排序；`sortOrder()` 返回构造时确定的排序顺序；`sortedDF` 直接应用传入的 `sortFunc`；`description()` 返回 `"SORT"`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkZOrderFileRewriteRunner.java` (新增, +204/-0 lines)

**修改目的**：zorder 数据文件重写 runner（原 `SparkZOrderDataRewriter`）。

**工作逻辑**：继承 `SparkShufflingFileRewriteRunner`，按列名列表构造 zorder 排序；`description()` 返回 `"Z-ORDER"`；`sortedDF` 用 zorder UDF 生成排序列后再排序。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingDataRewritePlanner.java` (新增, +82/-0 lines)

**修改目的**：shuffle 类重写的计划器，继承核心 `BinPackRewriteFilePlanner`。

**工作逻辑**：新增 `COMPRESSION_FACTOR` 选项（默认 1.0），`init` 时解析；重写 `expectedOutputFiles(inputSize)` 为 `super.expectedOutputFiles((long)(inputSize * compressionFactor))`，因为 shuffle/sort 后压缩率可能与原文件不同，通过该因子调整预期输出文件数，从而影响 shuffle 分区数。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkRewritePositionDeleteRunner.java` (新增, +150/-0 lines)

**修改目的**：position delete 重写 runner（原 `SparkBinPackPositionDeletesRewriter`）。

**工作逻辑**：继承 `SparkRewriteRunner<...>`（注意不经过 `SparkDataFileRewriteRunner`，因为 delete 文件用 `PositionDeletesRewriteCoordinator`）；构造时关闭 AQE；`rewrite(RewritePositionDeletesGroup group)` 用 metadata table POSITION_DELETES 注册到 `SparkTableCache`/`ScanTaskSetManager`，`doRewrite` 读取 position deletes，与 `DataFilesTable` 做 leftsemi join 过滤掉指向已不存在数据文件的无效删除，按 `file_path, pos` 排序后写回；`dataFiles` 方法按分区过滤构建数据文件元数据表。

### 测试文件

- `TestSparkFileRewriteRunners.java`（新增, +139）：验证 `SparkSortFileRewriteRunner` 构造时对无序表/null/`SortOrder.unsorted()` 的拒绝。
- `TestSparkShufflingDataRewritePlanner.java`（新增, +92）：验证 `COMPRESSION_FACTOR` 选项解析、默认值、非法值拒绝，以及对 `expectedOutputFiles` 的影响。
- `TestSparkFileRewriter.java`（删除, -462）：旧 `FileRewriter` 的测试，已被 runner 测试取代。
- `TestRewriteDataFilesAction.java`（修改, +63/-179）：移除对 `RewriteExecutionContext`、`planFileGroups`、`rewriteJobOrder` 等的依赖，改用新 API 的可观测点。
- `TestRewritePositionDeleteFilesAction.java`（修改, +21/-41）：同上。
- `TestRewriteDataFilesProcedure.java`（修改, +1/-1）、`TestRewritePositionDeleteFiles.java`（修改, +3/-3）、`TestCompressionSettings.java`（修改, +2/-2）：小适配。
- `IcebergSortCompactionBenchmark.java`（修改, +14/-16）：改用新 runner 类名。

## 总结

本次提交是 Spark 3.4 actions 模块对核心新 Planner/Runner API 的一次大规模对齐重构：把"计划"职责交给核心 `BinPackRewriteFilePlanner`/`BinPackRewritePositionDeletePlanner`（shuffle 类自定义 `SparkShufflingDataRewritePlanner` 扩展压缩因子），把"执行"职责交给新的 `SparkRewriteRunner` 体系（`SparkBinPackFileRewriteRunner`/`SparkShufflingFileRewriteRunner`/`SparkSortFileRewriteRunner`/`SparkZOrderFileRewriteRunner`/`SparkRewritePositionDeleteRunner`）。两个 action 类因此删除了大量自定义分组与上下文管理代码（净减约 450 行），逻辑更清晰、可测试性更好，并与核心 API 演进保持一致。
