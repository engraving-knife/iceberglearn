# 提交 3019：Flink, Core: RewriteDataFiles add max file group count (#14837)

## 提交信息

- **序号**：3019 / 4088
- **哈希**：f2449550c72e8d744b43438ef332716121af2c6b
- **短哈希**：f2449550c
- **日期**：2025-12-16
- **作者**：GuoYu
- **提交说明**：Flink, Core: RewriteDataFiles add max file group count (#14837)
- **PR/Issue**：#14837

## 总体目的

本提交为 Iceberg 的数据文件重写（RewriteDataFiles）功能新增"每个文件组最大输入文件数"限制，解决超大分区因资源约束无法完成重写的问题。在 Iceberg 的 `SizeBasedFileRewritePlanner` 中，重写计划器会使用 BinPacking 算法将待重写的数据文件按大小打包成多个"文件组"（file group），每个组的总大小不超过 `max-file-group-size-bytes`（默认 100GB）。每个组随后作为一个独立单元被重写。

然而，仅有总大小限制存在一个实际痛点：当一个分区包含大量小文件时，即使总大小未超限，单个文件组中可能包含数万个文件。重写一个文件组时，引擎需要同时打开和读取这些文件，在 Flink/Spark 等分布式引擎中会导致内存溢出（OOM）、句柄耗尽或任务超时。提交说明明确指出："This option controls the largest count of data that should be rewritten in a single file group. It helps with breaking down the rewriting of very large partitions which may not be rewritable otherwise due to the resource constraints of the cluster."（此选项控制单个文件组中可重写的最大数据量计数。它有助于拆分超大分区的重写，否则这些分区可能因集群资源约束而无法重写）。

通过新增 `max-file-group-input-files` 配置项，用户可以限制每个文件组中的文件数量上限。当文件数超过该限制时，BinPacking 算法会提前开启新组，即使当前组的总大小尚未达到 `targetWeight`。这使得超大分区能被拆分为更多更小的文件组，每组可在集群资源约束内完成重写。本提交同时修改了 Core 模块的 BinPacking 算法、SizeBasedFileRewritePlanner 计划器，以及 Flink v2.1 维护 API 的 Builder 和文档。

## 如何达成设计目的

设计思路是在 BinPacking 的"装箱"逻辑中增加一个与 `targetWeight` 并列的维度约束——`maxItemsPerBin`（每个箱中最多允许的物品数）。原先 `Bin.canAdd()` 仅判断 `binWeight + weight <= targetWeight`，现在增加 `&& binSize < maxSize` 条件。当任一约束达到上限时，就开启新的箱（文件组）。为保证向后兼容，新增了带 `maxItemsPerBin` 参数的构造方法，旧构造方法委托到新方法并传入 `Long.MAX_VALUE`（即不限制）。在 `SizeBasedFileRewritePlanner` 中新增配置项读取逻辑和校验，在 Flink 的 `RewriteDataFiles.Builder` 中暴露 `maxFileGroupInputFiles` API 供用户设置。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewritePlanner.java` (+22/-1 lines)

**修改目的**：新增 `max-file-group-input-files` 配置项及其在重写计划器中的使用。

**工作逻辑**：
新增常量 `MAX_FILE_GROUP_INPUT_FILES = "max-file-group-input-files"`，默认值 `MAX_FILE_GROUP_INPUT_FILES_DEFAULT = Long.MAX_VALUE`（即默认不限制，保持向后兼容）。在 `options(Map)` 初始化方法中新增 `this.maxGroupCount = maxGroupCount(options)` 读取配置。

`maxGroupCount` 方法通过 `PropertyUtil.propertyAsLong` 读取配置值，并使用 `Preconditions.checkArgument(value > 0, ...)` 校验必须为正数。

关键变更在 `planFileGroups` 方法：原先创建 `new BinPacking.ListPacker<>(maxGroupSize, 1, false)`（三个参数），现在改为 `new BinPacking.ListPacker<>(maxGroupSize, 1, false, maxGroupCount)`（四个参数），将最大文件数约束传入 packer。

### `core/src/main/java/org/apache/iceberg/util/BinPacking.java` (+42/-4 lines)

**修改目的**：在 BinPacking 装箱算法中增加"每个箱最大物品数"约束。

**工作逻辑**：
这是本提交的核心算法改动，涉及多个内部类：

1. **`ListPacker<T>`**：新增 `maxItemsPerBin` 字段。新增四参数构造方法 `ListPacker(long targetWeight, int lookback, boolean largestBinFirst, long maxItemsPerBin)`，旧三参数构造方法委托为新构造方法并传入 `Long.MAX_VALUE`。`pack` 和 `packEnd` 方法创建 `PackingIterable` 时透传 `maxItemsPerBin`。

2. **`PackingIterable<T>`**：新增 `maxSize` 字段及对应构造方法，同样保持向后兼容的旧构造方法委托模式。`iterator()` 方法创建 `PackingIterator` 时传入 `maxSize`。

3. **`PackingIterator<T>`**：新增 `maxSize` 字段并透传到 `newBin()` 创建的 `Bin` 对象。

4. **`Bin<T>`**：新增 `maxSize` 字段和 `binSize` 计数器。构造方法从 `Bin(long targetWeight)` 改为 `Bin(long targetWeight, long maxSize)`。核心变更在 `canAdd(long weight)` 方法：原先仅检查 `binWeight + weight <= targetWeight`，现在增加 `&& binSize < maxSize` 条件。`add` 方法中新增 `this.binSize++` 递增计数。

这样，当箱中物品数达到 `maxSize` 时，即使总重量未达 `targetWeight`，也会触发新箱创建。两个约束是 AND 关系，任一满足即停止添加。

### `core/src/test/java/org/apache/iceberg/util/TestBinPacking.java` (+50/-1 lines)

**修改目的**：为新增的最大物品数约束添加单元测试。

**工作逻辑**：
新增 `testBasicBinPackingTargetSize` 测试方法，使用 `list(1, 2, 3, 4, 5)` 作为输入，在不同 `targetWeight` 和 `targetSize` 组合下验证装箱结果。例如 `pack(list(1, 2, 3, 4, 5), 3, Integer.MAX_VALUE, 2)` 期望结果为 `list(list(1, 2), list(3), list(4), list(5))`——targetSize=2 限制每组最多 2 个物品，即使重量未达 targetWeight=3 也强制分组。测试覆盖了多个边界组合，验证两个约束的协同工作。

辅助方法 `pack` 也进行了重构，新增带 `targetSize` 参数的重载，并将旧方法委托为新方法传入 `Long.MAX_VALUE`，保持已有测试不受影响。

### `docs/docs/flink-maintenance.md` (+1/-0 lines)

**修改目的**：在 Flink 维护文档中记录新增的 `maxFileGroupInputFiles` 配置项。

**工作逻辑**：
在 RewriteDataFiles 配置表中新增一行：`maxFileGroupInputFiles(long) | Maximum allowed number of input files within a file group | Long.MAX_VALUE | long`，说明默认值为 `Long.MAX_VALUE`（不限制），用户可按需调小。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+13/-0 lines)

**修改目的**：在 Flink v2.1 的 RewriteDataFiles Builder 中暴露新配置项。

**工作逻辑**：
新增 `maxFileGroupInputFiles(long maxFileGroupInputFiles)` 方法，将值转为字符串后通过 `rewriteOptions.put(SizeBasedFileRewritePlanner.MAX_FILE_GROUP_INPUT_FILES, ...)` 写入重写选项 map，供后续 `SizeBasedFileRewritePlanner` 读取。Javadoc 引用 `SizeBasedFileRewritePlanner.MAX_FILE_GROUP_INPUT_FILES` 说明详细语义。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/RewriteUtil.java` (+7/-1 lines)

**修改目的**：重构测试工具方法以支持自定义重写选项。

**工作逻辑**：
将原 `planDataFileRewrite(TableLoader)` 方法改为委托到新方法 `planDataFileRewrite(TableLoader, Map<String, String> rewriterOptions)`，原方法使用 `ImmutableMap.of(MIN_INPUT_FILES, "2")` 作为默认选项。这样测试可以传入自定义选项（如 `MAX_FILE_GROUP_INPUT_FILES`），而不影响已有测试。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestDataFileRewritePlanner.java` (+20/-0 lines)

**修改目的**：为 Flink 集成场景下的 max file group count 功能添加端到端测试。

**工作逻辑**：
新增 `testMaxFileGroupCount` 测试。创建分区表并插入数据到 p1（2 个文件）和 p2（4 个文件）两个分区。不设限制时，`planDataFileRewrite(tableLoader())` 产生 2 个文件组（每个分区一组）。设置 `MAX_FILE_GROUP_INPUT_FILES=2` 时，`planDataFileRewrite(tableLoader(), ImmutableMap.of(MIN_INPUT_FILES, "2", MAX_FILE_GROUP_INPUT_FILES, "2"))` 产生 3 个文件组——p2 的 4 个文件被限制每组最多 2 个，拆成 2 组，加上 p1 的 1 组，共 3 组。这验证了新功能在 Flink 计划器中的正确工作。

## 总结

本提交为 Iceberg 数据文件重写功能增加了一个实用的资源控制维度——文件组内最大文件数限制，有效解决了超大分区因文件数过多导致重写任务资源溢出的问题。改动从底层 BinPacking 算法到上层 Flink API 和文档形成完整链路，保持了向后兼容性（默认不限制），并通过 Core 和 Flink 双层测试验证了正确性。这一功能对于拥有大量小文件的大规模 Iceberg 表的运维具有实际价值。
