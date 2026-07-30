# 提交 1267：Core, Spark 3.5: Remove dangling deletes as part of RewriteDataFilesAction (#9724)

## 提交信息

- **序号**：1267 / 4088
- **哈希**：a8ec43d975d8d3bbacb6880b487208b00cb7361f
- **短哈希**：a8ec43d97
- **日期**：2024-10-22（Tue Oct 22 15:28:57 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Core, Spark 3.5: Remove dangling deletes as part of RewriteDataFilesAction (#9724)
- **PR/Issue**：#9724

## 总体目的

Iceberg 表在经历 `RewriteDataFiles`（数据文件压缩/重写）后，可能产生"dangling delete files"（悬空删除文件）——即删除文件所针对的数据文件已不再存在于当前快照中（被重写替换），这些删除文件虽然仍挂在元数据中，但其删除操作已不适用于任何现存数据文件，属于无用开销。

具体场景：`RewriteDataFiles` 把多个小数据文件合并为大文件时，旧数据文件被新文件替换。此前附着在旧数据文件上的 position delete 和 equality delete 文件，在重写后可能不再适用于任何新数据文件。但 Iceberg 的提交机制不会自动清理这些悬空删除文件（`ManifestFilterManager` 只在非分区表的全局层面做此清理，分区表不做），导致：

1. **读取开销**：后续扫描仍需加载并应用这些无用的删除文件，浪费 IO 和 CPU。
2. **元数据膨胀**：删除文件持续累积，manifest 膨胀。

本提交新增一个独立的 `RemoveDanglingDeleteFiles` action，用于识别并删除当前快照中的悬空删除文件，并在 `RewriteDataFiles` action 中新增 `remove-dangling-deletes` 选项（默认 false），让用户在数据重写后可选地触发悬空删除清理。

悬空删除的判定规则（基于序列号语义）：

- **Position delete 文件**：若其 `data sequence number` **小于**同分区中任何数据文件的最小 `data sequence number`，则该 position delete 不可能适用于任何现存数据文件（因为 position delete 只作用于序列号大于等于它的数据文件），判定为悬空。
- **Equality delete 文件**：若其 `data sequence number` **小于等于**同分区中任何数据文件的最小 `data sequence number`，则该 equality delete 不可能适用于任何现存数据文件（因为 equality delete 作用于序列号严格大于它的数据文件），判定为悬空。
- **无数据文件的分区**：若某分区中完全没有数据文件（`min_data_sequence_number` 为 null），则该分区的所有删除文件都是悬空的。

## 如何达成设计目的

分三层设计：

1. **API 层（`api` 模块）**：
   - 新增 `RemoveDanglingDeleteFiles` 接口（extends `Action`），定义 `Result.removedDeleteFiles()`。
   - 在 `ActionsProvider` 中新增 `removeDanglingDeleteFiles(Table)` 默认方法（抛 `UnsupportedOperationException`）。
   - 在 `RewriteDataFiles` 中新增 `REMOVE_DANGLING_DELETES` 属性（默认 false）和 `Result.removedDeleteFilesCount()`（默认 0）。

2. **Core 层（`core` 模块）**：
   - 新增 `BaseRemoveDanglingDeleteFiles` Immutables 风格接口，生成 `ImmutableRemoveDanglingDeleteFiles.Result`。
   - `BaseRewriteDataFiles.Result` 新增 `removedDeleteFilesCount` 默认实现。

3. **Spark 3.5 实现（`spark/v3.5` 模块）**：
   - 新增 `RemoveDanglingDeletesSparkAction`：用 Spark DataFrame 操作 Iceberg 的 `ENTRIES` 元数据表，按分区分组找最小数据序列号，left join 删除文件，按上述规则筛选悬空删除，收集到 driver 后用 `SparkDeleteFile` 包装为 `DeleteFile` 对象，通过 `RewriteFiles` 提交删除。
   - `RewriteDataFilesSparkAction`：在 `execute()` 末尾，若 `removeDanglingDeletes=true`，调用 `RemoveDanglingDeletesSparkAction` 并把删除文件数填入结果。
   - `SparkActions`：实现 `removeDanglingDeleteFiles(Table)` 返回 `RemoveDanglingDeletesSparkAction`。
   - `SparkContentFile`：修复 `specId()` 之前硬编码返回 -1 的 bug，改为从行中读取 `spec_id` 字段（`RemoveDanglingDeletesSparkAction` 的 `deleteFileWrapper` 需要正确的 specId 来选择分区类型）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RemoveDanglingDeleteFiles.java`（新增，35 行）

**修改目的**：定义移除悬空删除文件的 action 接口。

**工作逻辑**：

- `interface RemoveDanglingDeleteFiles extends Action<RemoveDanglingDeleteFiles, RemoveDanglingDeleteFiles.Result>`。
- Javadoc 说明："A delete file is dangling if its deletes no longer applies to any live data files."
- `interface Result { Iterable<DeleteFile> removedDeleteFiles(); }`。

### `api/src/main/java/org/apache/iceberg/actions/ActionsProvider.java`（修改，+6 行）

**修改目的**：在 ActionsProvider 中注册新 action 的工厂方法。

**工作逻辑**：新增 `default RemoveDanglingDeleteFiles removeDanglingDeleteFiles(Table table)`，默认抛 `UnsupportedOperationException`，由具体实现（如 `SparkActions`）覆盖。

### `api/src/main/java/org/apache/iceberg/actions/RewriteDataFiles.java`（修改，+12 行）

**修改目的**：为 `RewriteDataFiles` 新增 `remove-dangling-deletes` 选项和结果字段。

**工作逻辑**：

- 新增属性常量 `REMOVE_DANGLING_DELETES = "remove-dangling-deletes"`，默认 `false`。Javadoc 说明：在 compaction 后移除悬空删除文件，position 和 equality 悬空删除都会被移除。
- `Result` 接口新增 `default int removedDeleteFilesCount() { return 0; }`。

### `core/src/main/java/org/apache/iceberg/actions/BaseRemoveDanglingDeleteFiles.java`（新增，33 行）

**修改目的**：Immutables 风格的基类接口，生成不可变 `Result`。

**工作逻辑**：`@Value.Enclosing` + `@Value.Style` 注解配置 Immutables 代码生成，生成 `ImmutableRemoveDanglingDeleteFiles.Result`。`interface Result extends RemoveDanglingDeleteFiles.Result {}`。

### `core/src/main/java/org/apache/iceberg/actions/BaseRewriteDataFiles.java`（修改，+6 行）

**修改目的**：在 `RewriteDataFiles.Result` 的 Immutables 实现中新增 `removedDeleteFilesCount` 默认值。

**工作逻辑**：`@Value.Default default int removedDeleteFilesCount() { return RewriteDataFiles.Result.super.removedDeleteFilesCount(); }`，默认 0。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RemoveDanglingDeletesSparkAction.java`（新增，179 行）

**修改目的**：Spark 实现的悬空删除清理 action。

**工作逻辑**：

- `execute()`：
  - 若表是非分区表（单 spec 且 unpartitioned），直接返回空结果——因为 `ManifestFilterManager` 在每次提交时已自动做全表级别的悬空删除清理。
  - 否则，设置 JobGroup 后调用 `doExecute()`。
- `doExecute()`：
  - 调用 `findDanglingDeletes()` 找到悬空删除文件列表。
  - 用 `table.newRewrite()` 创建 `RewriteFiles`，对每个悬空删除文件调用 `rewriteFiles.deleteFile(deleteFile)`。
  - 若列表非空则提交。
  - 返回 `ImmutableRemoveDanglingDeleteFiles.Result`。
- `findDanglingDeletes()`（核心逻辑）：
  1. 加载 `ENTRIES` 元数据表，过滤出存活数据文件（`data_file.content == 0 AND status < 2`），按 `(partition, spec_id)` 分组，取每组最小 `sequence_number`，得到 `minSequenceNumberByPartition`。
  2. 再次加载 `ENTRIES`，过滤出存活删除文件（`data_file.content != 0 AND status < 2`）。
  3. Left join 删除文件与 `minSequenceNumberByPartition`（按 `spec_id` + `partition` 匹配）。
  4. 筛选悬空删除：
     - `min_data_sequence_number IS NULL`（分区无数据文件），或
     - `data_file.content == 1`（position delete）且 `sequence_number < min_data_sequence_number`，或
     - `data_file.content == 2`（equality delete）且 `sequence_number <= min_data_sequence_number`。
  5. 收集到 driver，用 `deleteFileWrapper` 把 Spark `Row` 包装为 `SparkDeleteFile`（`DeleteFile` 实现）。
- `deleteFileWrapper`：从 Row 中取 `spec_id`，用对应 spec 的分区类型构造 `SparkDeleteFile` 并 `wrap(row)`。注意 `SparkDeleteFile` 不可序列化，故在 driver 上做映射。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（修改，+18/-6 行）

**修改目的**：在数据重写后可选地触发悬空删除清理。

**工作逻辑**：

- `VALID_OPTIONS` 集合新增 `REMOVE_DANGLING_DELETES`。
- 新增字段 `private boolean removeDanglingDeletes`，在 `validateOptions` 中用 `PropertyUtil.propertyAsBoolean` 解析。
- `execute()` 方法重构：`doExecute` 和 `doExecuteWithPartialProgress` 的返回类型从 `Result` 改为 `Builder`（不立即 `.build()`），以便在返回前追加 `removedDeleteFilesCount`。若 `removeDanglingDeletes=true`，创建 `RemoveDanglingDeletesSparkAction`，执行并 `Iterables.size(action.execute().removedDeleteFiles())` 填入 builder。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java`（修改，+6 行）

**修改目的**：注册新 action 的工厂。

**工作逻辑**：实现 `removeDanglingDeleteFiles(Table table)` 返回 `new RemoveDanglingDeletesSparkAction(spark, table)`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`（修改，+5/-1 行）

**修改目的**：修复 `specId()` 硬编码返回 -1 的 bug。

**工作逻辑**：

- 原代码 `public int specId() { return -1; }` — 始终返回 -1，导致 `RemoveDanglingDeletesSparkAction.deleteFileWrapper` 无法获取正确的 specId 来选择分区类型。
- 新代码：新增 `fileSpecIdPosition` 字段（从 `DataFile.SPEC_ID.name()` 获取位置），`specId()` 改为从包装的 Row 中读取 `spec_id` 字段（若为 null 返回 -1）。

### 测试文件（3 个文件，+665 行）

- `TestRemoveDanglingDeleteAction.java`（新增，447 行）：全面测试 `RemoveDanglingDeletesSparkAction`，覆盖 position/equality 悬空删除、分区演进、非分区表跳过等场景。
- `TestRewriteDataFilesAction.java`（+215 行）：新增 `testRemoveDangledEqualityDeletesPartitionEvolution` 等测试，验证 `RewriteDataFiles` 带 `remove-dangling-deletes=true` 时在分区演进场景下正确清理 equality 悬空删除。
- `TestRewritePositionDeleteFilesAction.java`（+2/-1 行）：小调整。

## 小结

- **成效**：新增了 `RemoveDanglingDeleteFiles` action 及其 Spark 3.5 实现，能在 `RewriteDataFiles` 后或独立调用时识别并删除悬空删除文件。判定规则基于序列号语义：position delete 的序列号 < 分区最小数据序列号、equality delete 的序列号 <= 分区最小数据序列号即为悬空。使用 Spark DataFrame 操作 `ENTRIES` 元数据表高效识别悬空删除。同时修复了 `SparkContentFile.specId()` 硬编码 -1 的 bug。
- **影响范围**：`api`（3 个文件，新增接口）、`core`（2 个文件，Immutables 基类）、`spark/v3.5`（4 个生产文件 + 3 个测试文件）。新增功能默认关闭（`remove-dangling-deletes=false`），不影响现有行为。`SparkContentFile.specId()` 的修复是行为变化（之前总返回 -1，现在返回真实 specId），可能影响依赖该方法的代码路径。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个完整的 feature（新 action + 集成到 RewriteDataFiles），回迁价值高（清理悬空删除可提升读取性能）。但改动量大（12 个文件、974 行），需仔细测试。
  2. 需确认 1.4.x 分支上 `ActionsProvider`、`RewriteDataFiles`、`BaseRewriteDataFiles`、`SparkActions`、`RewriteDataFilesSparkAction`、`SparkContentFile` 的结构与 main 一致。Immutables 注解处理需构建配置支持。
  3. `SparkContentFile.specId()` 的修复可能影响 1.4.x 上其他依赖 `specId()` 的代码，回迁后需验证无回归。
  4. `RemoveDanglingDeletesSparkAction` 依赖 `ENTRIES` 元数据表的 `sequence_number` 列和 `data_file.content`/`status` 字段，需确认 1.4.x 的 `ENTRIES` 表结构支持。
  5. 此提交是 Spark 3.5 专用的，提交 1270 是 Spark 3.4 的对应版本，回迁时可一并考虑。
  6. 测试量很大（447+215 行），建议完整回迁测试以确保正确性。
