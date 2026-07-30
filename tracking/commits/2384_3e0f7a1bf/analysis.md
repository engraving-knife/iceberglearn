# 提交 2384：Spark 4.0: Preserve row lineage information when compaction is run (#13555)

## 提交信息

- **序号**：2384 / 4088
- **哈希**：3e0f7a1bfb167c4d18ebdc9d0f69afe0f4754512
- **短哈希**：3e0f7a1bf
- **日期**：2025-07-22 15:26:25 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 4.0: Preserve row lineage information when compaction is run (#13555)
- **PR/Issue**：#13555

## 总体目的

本提交解决了 Spark 4.0 中数据压缩（compaction）操作丢失行血统（row lineage）信息的问题。行血统是 Iceberg 的一项功能，通过为每行分配唯一的 `_row_id` 和 `_last_updated_sequence_number` 来跟踪行的历史变更。在执行覆盖写入（overwrite）操作时，Iceberg 会保留行血统信息，但在执行数据压缩（compaction，即 RewriteDataFiles 操作）时，行血统信息会被丢失。

这是因为压缩操作本质上是对现有数据文件的重写，源数据中已经包含行血统列（`_row_id` 等），但 SparkWriteBuilder 在判断是否需要在写入 schema 中包含行血统列时，仅检查了 `overwriteFiles` 条件，没有考虑压缩场景。本提交修改了这一逻辑，使压缩操作也能正确保留行血统信息。

## 如何达成设计目的

设计思路是通过引入 "rewrite" 标识来区分压缩操作，并在表加载和写入构建时根据该标识正确处理行血统列。关键设计点如下：

1. **识别压缩操作**：通过 `rewrittenFileSetId`（重写文件集 ID）来识别当前是压缩操作，而不是普通的覆盖写入。
2. **SparkTable 增加 isTableRewrite 标识**：新增构造函数参数标记表是否用于压缩操作，用于在读取 schema 时决定是否添加行血统列。
3. **SparkCatalog 和 IcebergSource 支持 rewrite 选择器**：当检测到压缩操作时，加载 SparkTable 时设置 `isTableRewrite=true`。
4. **SparkWriteBuilder 修改行血统判断逻辑**：将行血统写入条件从仅 `overwriteFiles` 扩展为 `overwriteFiles || rewrittenFileSetId != null`，同时增加检查避免重复添加已有的行血统列。
5. **SparkCachedTableCatalog 重构**：将 `load` 方法返回类型从 `Pair<Table, Long>` 改为 `SparkTable`，引入 `TableLoadOptions` 和 `copyWithSnapshotId` 方法支持更灵活的表加载。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+8/-4 lines)

**修改目的**：修改行血统写入判断逻辑，支持压缩场景。

**工作逻辑**：将 `writeIncludesRowLineage` 的条件从 `supportsRowLineage(table) && overwriteFiles` 改为 `writeRequiresRowLineage = supportsRowLineage(table) && (overwriteFiles || writeConf.rewrittenFileSetId() != null)`。同时新增 `writeAlreadyIncludesLineage` 检查，当数据源 schema 中已包含 ROW_ID 字段时（压缩场景），不再重复添加行血统列。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+13/-4 lines)

**修改目的**：增加 isTableRewrite 标识和行血统 schema 处理。

**工作逻辑**：新增 `isTableRewrite` 字段和带该参数的构造函数。新增 `addLineageIfRequired` 方法，当表支持行血统且 `isTableRewrite` 为 true 时，调用 `MetadataColumns.schemaWithRowLineage` 在 schema 中添加行血统列。修改 `schema()` 方法在返回前调用 `addLineageIfRequired`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+14/-0 lines)

**修改目的**：支持通过 "rewrite" 标识加载压缩表。

**工作逻辑**：新增 `REWRITE` 常量。在表标识符解析中增加对 "rewrite" 元数据的处理，设置 `isRewrite=true`。在 `loadTable` 方法中，当 `isRewrite` 为 true 时，创建 `SparkTable` 时传入 `isTableRewrite=true`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+10/-0 lines)

**修改目的**：在 IcebergSource 中支持压缩操作的 rewrite 选择器。

**工作逻辑**：新增 `REWRITE_SELECTOR` 常量。当检测到 `SCAN_TASK_SET_ID` 或 `REWRITTEN_FILE_SCAN_TASK_SET_ID` 选项时，将 selector 设为 "rewrite"，使后续加载逻辑能正确识别压缩操作。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCachedTableCatalog.java` (+96/-46 lines)

**修改目的**：重构 SparkCachedTableCatalog 支持 rewrite 表加载。

**工作逻辑**：将 `load` 方法的返回类型从 `Pair<Table, Long>` 改为 `SparkTable`，引入 `TableLoadOptions` 类解析表加载选项（包括 isTableRewrite）。新增 `copyWithSnapshotId` 方法支持基于已有 SparkTable 创建不同快照的实例。当 `isTableRewrite` 为 true 时，返回带 `isTableRewrite=true` 的 SparkTable。

### `spark/v4.0/build.gradle` (+1/-1 lines)

**修改目的**：增加测试堆内存。

**工作逻辑**：将 `maxHeapSize` 从 '2560m' 增加到 '3160m'，以适应新增测试用例的内存需求。

### 测试文件 (+253/-62 lines)

**修改目的**：添加压缩操作保留行血统的测试用例。

**工作逻辑**：在 `TestRewriteDataFilesProcedure.java` 和 `TestRewriteDataFilesAction.java` 中新增测试用例，验证压缩操作后行血统信息（`_row_id` 和 `_last_updated_sequence_number`）被正确保留，数据行可以通过 `_row_id` 正确关联。

## 总结

本提交是一个重要的功能修复，确保 Spark 4.0 中数据压缩操作不会丢失行血统信息。通过引入 "rewrite" 标识机制，修改了表加载和写入构建逻辑，使压缩操作能正确识别并保留行血统列。修改涉及 8 个文件，388 行新增和 117 行删除，包括核心逻辑修改和全面的测试覆盖。该修复对于依赖行血统进行数据追踪和增量处理的场景至关重要。
