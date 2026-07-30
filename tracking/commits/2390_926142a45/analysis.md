# 提交 2390：Spark 3.5: Backport #13555 for preserving row lineage on compaction (#13637)

## 提交信息

- **序号**：2390 / 4088
- **哈希**：926142a45224d2955ed95b493a2c6dcc3baaaa72
- **短哈希**：926142a45
- **日期**：2025-07-23 09:16:00 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.5: Backport #13555 for preserving row lineage on compaction (#13637)
- **PR/Issue**：#13637（backport #13555）

## 总体目的

此提交将 PR #13555 的功能 backport 到 Spark 3.5 分支，核心目标是**在数据文件压缩（compaction）操作中保留行级血统（row lineage）信息**。

Iceberg V3 表引入了行级血统（row lineage）特性，通过 `_row_id` 和 `_last_updated_sequence_number` 两个元数据列来追踪行的身份和变更历史。在正常的数据写入和 overwrite 操作中，这些列会被正确写入。然而，当执行 compaction（将多个小文件合并为大文件）时，原先的实现没有保留行级血统列，导致压缩后的数据文件丢失了行标识信息，破坏了 V3 表的行级血统保证。

本提交通过识别 compaction 操作场景（通过 `rewrite` 标记和 `rewrittenFileSetId`），在 compaction 写入时自动将行级血统列添加到写入 schema 中，确保压缩后的文件保留了完整的行级血统信息。

## 如何达成设计目的

关键设计点如下：

1. **引入 "rewrite" 选择器**：在 `SparkCatalog`、`SparkCachedTableCatalog` 和 `IcebergSource` 中新增 "rewrite" 标识，用于区分表加载是为普通查询还是为 compaction 操作。
2. **SparkTable 新增 isTableRewrite 标志**：当表以 rewrite 模式加载时，`SparkTable` 会设置 `isTableRewrite=true`，并在 schema 解析时通过 `addLineageIfRequired` 方法在表 schema 中附加行级血统元数据列。
3. **SparkWriteBuilder 增强**：在构建写入时，如果表支持行级血统且当前是 compaction 操作（`rewrittenFileSetId != null`）或 overwrite 操作，则将 `_row_id` 和 `_last_updated_sequence_number` 列添加到 Spark 写入 schema 中（如果尚不存在）。
4. **IcebergSource 自动检测**：通过 `SCAN_TASK_SET_ID` 或 `REWRITTEN_FILE_SCAN_TASK_SET_ID` 选项自动识别 rewrite 场景。
5. **SparkCachedTableCatalog 重构**：引入 `TableLoadOptions` 内部类封装表加载选项，使代码更清晰且支持 rewrite 选项。

## 修改详情

### `spark/v3.5/build.gradle` (+1/-1 lines)

**修改目的**：更新 build 配置以支持 backport。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCachedTableCatalog.java` (+95/-47 lines)

**修改目的**：重构表加载逻辑并支持 rewrite 选择器。

**工作逻辑**：原先 `load` 方法返回 `Pair<Table, Long>`，重构后直接返回 `SparkTable`。新增 `TableLoadOptions` 内部类来封装所有加载选项（snapshotId、asOfTimestamp、branch、tag、isTableRewrite）。`parseLoadOptions` 方法从标识符元数据中解析选项，包括新增的 "rewrite" 标记。当检测到 rewrite 选项时，创建带有 `isTableRewrite=true` 的 SparkTable。各 `loadTable` 重载方法也相应调整，使用 `copyWithSnapshotId` 替代重新构造 SparkTable。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+14/-0 lines)

**修改目的**：在 SparkCatalog 中支持 rewrite 选择器。

**工作逻辑**：新增 `REWRITE` 常量。在 `loadTable` 的异常处理路径中，如果标识符名称为 "rewrite"，则返回带有 `isTableRewrite=true` 的 SparkTable。在 `load` 方法中解析 metadata 时识别 "rewrite" 标记并设置 `isRewrite` 标志，最终在条件分支中创建带 rewrite 标志的 SparkTable。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+10/-0 lines)

**修改目的**：自动检测 compaction 场景并设置 rewrite 选择器。

**工作逻辑**：从加载选项中获取 `SCAN_TASK_SET_ID` 或 `REWRITTEN_FILE_SCAN_TASK_SET_ID`，如果存在则将选择器设为 "rewrite"，使后续表加载流程能识别这是 compaction 操作。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+20/-2 lines)

**修改目的**：支持 rewrite 模式下的行级血统 schema。

**工作逻辑**：新增 `isTableRewrite` 字段和对应的构造函数重载。新增 `addLineageIfRequired` 方法：当表支持行级血络且处于 rewrite 模式时，通过 `MetadataColumns.schemaWithRowLineage` 将行级血统列添加到 schema 中。`schema()` 方法在返回非元数据表的 schema 时调用此方法。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+20/-2 lines)

**修改目的**：在 compaction 写入时自动添加行级血统列。

**工作逻辑**：将 `writeIncludesRowLineage` 条件从仅 `overwriteFiles` 扩展为 `overwriteFiles || writeConf.rewrittenFileSetId() != null`。新增 `writeAlreadyIncludesLineage` 检查以避免重复添加。如果写入需要行级血统但 Spark 数据 schema 中尚不存在，则手动添加 `_row_id`（LongType）和 `_last_updated_sequence_number`（LongType）列到 Spark 写入 schema 中，再进行 schema 验证和合并。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+200/-33 lines)

**修改目的**：更新和新增 compaction 测试以覆盖行级血统场景。

**工作逻辑**：将原 `testBinPackWithDeletes` 拆分为 V2 专用的 `testBinPackWithV2PositionDeletes`（限定 formatVersion=2）和新增的 V3 DV 测试。调整了 V3 表的 target file size 以补偿行级血统字段带来的额外数据量。新增了验证 compaction 后行级血统列保留的测试逻辑。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+73/-1 lines)

**修改目的**：扩展存储过程测试以覆盖 V3 表 compaction 行级血统保留。

## 总结

此提交是行级血统在 compaction 场景下保留功能的重要 backport，确保 Spark 3.5 在对 V3 表执行数据文件压缩时不会丢失行标识信息。实现方式是通过 "rewrite" 选择器标识 compaction 场景，在表加载和写入构建阶段自动注入行级血统元数据列。测试覆盖了 V2 和 V3 两种场景下的 compaction 行为。
