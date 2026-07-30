# 提交 1936：Core: FileRewritePlanner implementation (#12493)

## 提交信息

- **序号**：1936 / 4088
- **哈希**：d5971429ea903be873b5884c64a3dd41076179ea
- **短哈希**：d5971429e
- **日期**：2025-03-29 09:15:14 +0100
- **作者**：pvary
- **提交说明**：Core: FileRewritePlanner implementation (#12493)
- **PR/Issue**：#12493

## 总体目的

此提交是 Iceberg 文件重写（rewrite）架构重构的核心实现。原有的 `FileRewriter` 接口将"规划（哪些文件需要重写、如何分组）"与"执行（实际重写文件）"两个职责耦合在同一个类中，且与具体引擎（Spark/Flink 等）的实现强绑定，难以独立测试和复用，也限制了未来不同引擎对规划逻辑的共享。

本提交引入新的"规划器（Planner）+ 运行器（Runner）"分离架构：新增 `FileRewritePlanner` 接口体系（此前接口 `FileRewritePlanner`、`FileRewritePlan`、`FileRewriteRunner` 已存在，本提交补齐其具体实现），将"扫描表、选择待重写文件、按分区与大小装箱分组、估算输出文件数与 split 大小"等纯规划逻辑抽取到独立的 planner 类中，产出不可变的 `FileRewritePlan`。引擎侧只需实现 `FileRewriteRunner` 消费 plan 即可。

具体新增三个核心类：
1. `SizeBasedFileRewritePlanner`（抽象基类）：实现通用的基于文件大小的规划算法（bin-packing 分组、文件大小阈值过滤、split size 估算、输出文件数估算等），与具体文件类型无关。
2. `BinPackRewriteFilePlanner`：针对数据文件（DataFile）的具体 planner，继承 `SizeBasedFileRewritePlanner`，额外处理 delete file 数量阈值、delete ratio 阈值、job order 等 RewriteDataFiles 特有逻辑。
3. `BinPackRewritePositionDeletePlanner`：针对 position delete 文件（DeleteFile）的具体 planner，处理 RewritePositionDeleteFiles 的 job order 等逻辑。

同时将旧的 `FileRewriter`、`SizeBasedFileRewriter`、`SizeBasedDataRewriter`、`SizeBasedPositionDeletesRewriter` 标记为 `@Deprecated(since 1.9.0, will be removed in 1.10.0)`，引导用户迁移到新的 planner + runner 架构。`RewriteFileGroup`/`RewritePositionDeletesGroup` 中构造参数 `splitSize` 重命名为 `inputSplitSize` 以更清晰表达语义，并补充弃用 Javadoc。

## 如何达成设计目的

整体设计采用模板方法模式（Template Method）+ 策略分离：

- **`FileRewritePlanner<I, T, F, G>` 接口**（已存在）：定义 `init(Map)` 配置、`validOptions()` 声明支持的配置项、`plan()` 产出 `FileRewritePlan<I,T,F,G>` 的契约。泛型参数：`I` 为 plan info 类型、`T` 为输入 scan task 类型、`F` 为内容文件类型、`G` 为重写分组类型。

- **`SizeBasedFileRewritePlanner<I,T,F,G>` 抽象基类**：实现通用的基于大小的规划算法。定义配置常量（`TARGET_FILE_SIZE_BYTES`、`MIN_FILE_SIZE_BYTES`、`MAX_FILE_SIZE_BYTES`、`MIN_INPUT_FILES`、`REWRITE_ALL`、`MAX_FILE_GROUP_SIZE_BYTES`），在 `init` 中解析并校验配置。提供模板方法 `filterFiles`、`filterFileGroups`、`defaultTargetFileSize` 供子类扩展。核心算法方法：
  - `planFileGroups(tasks)`：用 `BinPacking.ListPacker` 按 `maxGroupSize` 将任务装箱成分组。
  - `inputSplitSize(inputSize)`：根据输入大小与期望输出文件数估算 split 大小，约束在 `[targetFileSize, writeMaxFileSize]` 区间。
  - `expectedOutputFiles(inputSize)`：决定输出文件数，考虑余数文件是否值得单独存在（基于平均文件大小不超过 target 的 110% 的启发式）。
  - `writeMaxFileSize()`：target + (max - target) * 0.5，作为实际写入时的最大文件大小，避免产生小余数文件。
  - `RewriteExecutionContext` 内部类：为每个分组生成全局索引与分区索引。

- **`BinPackRewriteFilePlanner`**：继承抽象基类，针对数据文件。增加 `DELETE_FILE_THRESHOLD`、`DELETE_RATIO_THRESHOLD`、`REWRITE_JOB_ORDER` 配置。`filterFiles` 选择"大小越界 / 删除文件过多 / 删除比例过高"的文件；`filterFileGroups` 保留"输入文件足够 / 内容足够 / 内容过多 / 含删除过多的文件"的分组。`plan()` 扫描表、按分区分组、调用 `planFileGroups`、为每组构造 `RewriteFileGroup`（带 inputSplitSize、expectedOutputFiles、writeMaxFileSize），并按 `rewriteJobOrder` 排序。

- **`BinPackRewritePositionDeletePlanner`**：针对 position delete 文件。结构类似但扫描 `PositionDeletesTable`，使用 `PartitionUtil` 解析分区，构造 `RewritePositionDeletesGroup`，仅处理 job order 配置（无 delete threshold/ratio 概念）。

- **旧类弃用**：`FileRewriter`、`SizeBasedFileRewriter`、`SizeBasedDataRewriter`、`SizeBasedPositionDeletesRewriter` 加 `@Deprecated` 注解与 Javadoc，指向新 planner/runner。`RewriteFileGroup`/`RewritePositionDeletesGroup` 构造参数 `splitSize` 改名 `inputSplitSize`，旧 API 保留弃用标记。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewritePlanner.java` (新增, +361 lines)

**修改目的**：提供基于文件大小的通用重写规划抽象基类。

**工作逻辑**：
- 泛型 `<I, T extends ContentScanTask<F>, F extends ContentFile<F>, G extends RewriteGroupBase<I,T,F>>`，实现 `FileRewritePlanner<I,T,F,G>`。
- 定义配置常量与默认值：`TARGET_FILE_SIZE_BYTES`、`MIN_FILE_SIZE_BYTES`（默认 target * 0.75）、`MAX_FILE_SIZE_BYTES`（默认 target * 1.80）、`MIN_INPUT_FILES`（默认 5）、`REWRITE_ALL`（默认 false）、`MAX_FILE_GROUP_SIZE_BYTES`（默认 100GB）。
- `init(options)`：调用 `sizeThresholds` 解析并校验 target/min/max（target>min、target<max），读取 minInputFiles、rewriteAll、maxGroupSize、outputSpecId。
- `planFileGroups(tasks)`：`BinPacking.ListPacker` 按 maxGroupSize 装箱，若 rewriteAll 则跳过过滤。
- `expectedOutputFiles(inputSize)`：基于 targetFileSize 用 `LongMath.divide` 向上/向下取整，结合"余数文件是否大于 minFileSize"与"无余数时平均文件大小是否超过 target 的 110% 或 writeMaxFileSize"的启发式决定保留或合并余数。
- `inputSplitSize(inputSize)`：inputSize/expectedOutputFiles + SPLIT_OVERHEAD，约束在 `[targetFileSize, writeMaxFileSize]`。
- `writeMaxFileSize()`：`target + (max - target) * 0.5`。
- `RewriteExecutionContext`：并发安全地为每组生成 globalIndex 与 partitionIndex。
- 抽象方法 `defaultTargetFileSize`、`filterFiles`、`filterFileGroups` 供子类实现。

### `core/src/main/java/org/apache/iceberg/actions/BinPackRewriteFilePlanner.java` (新增, +312 lines)

**修改目的**：数据文件的具体 bin-pack 重写规划器。

**工作逻辑**：
- 继承 `SizeBasedFileRewritePlanner<FileGroupInfo, FileScanTask, DataFile, RewriteFileGroup>`。
- 构造器接收 `table`、`filter`、`snapshotId`、`caseSensitive`。
- 新增配置 `DELETE_FILE_THRESHOLD`（默认 Integer.MAX_VALUE，即默认不启用）、`DELETE_RATIO_THRESHOLD`（默认 0.3）、`REWRITE_JOB_ORDER`。
- `filterFiles`：保留 `outsideDesiredFileSizeRange || tooManyDeletes || tooHighDeleteRatio` 的文件。
- `filterFileGroups`：保留 `enoughInputFiles || enoughContent || tooMuchContent || 组内任一文件 tooManyDeletes/tooHighDeleteRatio` 的分组。
- `plan()`：`planFileGroups()` 扫描表（`TableScan` + filter + snapshotId），`groupByPartition` 按当前 spec 分区分组（specId 不匹配的归为未分区），对每个分区调用 `planFileGroups(tasks)` 装箱，为每组构造 `RewriteFileGroup`（info 含 globalIndex/partitionIndex/partition），按 `rewriteJobOrder` 排序，返回 `FileRewritePlan`（含 groups iterable、totalGroupCount、groupsInPartition）。
- `tooHighDeleteRatio`：基于 file-scoped 删除文件的 recordCount 估算删除比例。

### `core/src/main/java/org/apache/iceberg/actions/BinPackRewritePositionDeletePlanner.java` (新增, +222 lines)

**修改目的**：position delete 文件的具体 bin-pack 重写规划器。

**工作逻辑**：
- 继承 `SizeBasedFileRewritePlanner<FileGroupInfo, PositionDeletesScanTask, DeleteFile, RewritePositionDeletesGroup>`。
- 构造器接收 `table`、`filter`、`caseSensitive`。
- 新增 `REWRITE_JOB_ORDER` 配置（来自 `RewritePositionDeleteFiles`）。
- `filterFiles`：保留 `outsideDesiredFileSizeRange` 的文件（delete 文件无 delete 概念）。
- `filterFileGroups`：保留 `enoughInputFiles || enoughContent || tooMuchContent` 的分组。
- `plan()`：扫描 `PositionDeletesTable`，用 `PartitionUtil` 解析分区，按分区分组装箱，构造 `RewritePositionDeletesGroup`，按 job order 排序。

### `core/src/main/java/org/apache/iceberg/actions/FileRewriter.java` (修改, +3 lines)

**修改目的**：弃用旧接口。

**工作逻辑**：加 `@Deprecated` 注解与 Javadoc `@deprecated since 1.9.0, will be removed in 1.10.0; use FileRewritePlanner and FileRewriteRunner`。

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java` (修改, +4 lines)

**修改目的**：弃用旧抽象类。

**工作逻辑**：加 `@Deprecated` 注解，指向 `SizeBasedFileRewritePlanner` 和 `FileRewriteRunner`。

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedDataRewriter.java` (修改, +23 lines)

**修改目的**：弃用旧数据重写类，并改进 Javadoc 措辞。

**工作逻辑**：加 `@Deprecated` 注解指向 `BinPackRewriteFilePlanner` 和 `FileRewriteRunner`；`DELETE_RATIO_THRESHOLD` 的 Javadoc 从"minimum deletion ratio"改写为"ratio of the deleted rows"，使表述更准确。

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedPositionDeletesRewriter.java` (修改, +5 lines)

**修改目的**：弃用旧 position delete 重写类。

**工作逻辑**：加 `@Deprecated` 注解指向 `BinPackRewritePositionDeletePlanner` 和 `FileRewriteRunner`。

### `core/src/main/java/org/apache/iceberg/actions/RewriteFileGroup.java` (修改, +13/-5 lines)

**修改目的**：重命名构造参数 `splitSize` 为 `inputSplitSize`，补充弃用 Javadoc。

**工作逻辑**：主构造器的 `splitSize` 参数改名为 `inputSplitSize`（传递给父类同步改名）；`fileScans()`、`sizeInBytes()`、`numFiles()` 等旧方法的 `@deprecated` Javadoc 统一补充 `since 1.9.0, will be removed in 1.10.0`。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesGroup.java` (修改, +13/-5 lines)

**修改目的**：同上，针对 position delete 分组。

**工作逻辑**：构造参数 `splitSize` 改名 `inputSplitSize`；`tasks()`、`rewrittenBytes()`、`numRewrittenDeleteFiles()` 弃用 Javadoc 补充版本信息。

### 测试文件 (新增/修改)

- `TestBinPackRewriteFilePlanner.java`（+478 lines）：针对数据文件 planner 的单元测试，覆盖文件大小阈值过滤、分组、delete threshold/ratio、job order、expectedOutputFiles 估算等场景。
- `TestBinPackRewritePositionDeletePlanner.java`（+294 lines）：针对 position delete planner 的测试。
- `TestSizeBasedFileRewritePlanner.java`（+185 lines）：针对抽象基类通用逻辑（如 expectedOutputFiles、inputSplitSize、writeMaxFileSize 估算）的测试。
- `TestSizeBasedRewriter.java`（+4 lines）：适配 `splitSize` → `inputSplitSize` 改名。

## 总结

本提交是 Iceberg 文件重写架构重构的核心，引入"规划器（Planner）+ 运行器（Runner）"分离架构。新增 `SizeBasedFileRewritePlanner` 抽象基类实现通用的基于大小的 bin-packing 规划算法（文件选择、分组、split size 估算、输出文件数估算），并由 `BinPackRewriteFilePlanner`（数据文件）与 `BinPackRewritePositionDeletePlanner`（position delete 文件）两个具体类继承扩展。旧的 `FileRewriter`/`SizeBasedFileRewriter` 系列标记为弃用（1.9.0 弃用，1.10.0 移除）。这使规划逻辑可独立于引擎测试与复用，为后续多引擎共享规划逻辑奠定基础。配套提供了 957 行测试覆盖三个 planner 类。
