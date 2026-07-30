# 提交 2392：Spark 3.4: Backport #13555 for preserving row lineage on compaction (#13641)

## 提交信息

- **序号**：2392 / 4088
- **哈希**：8e2dc047c25da64aeed0fb7f2bb9faefe5cde480
- **短哈希**：8e2dc047c
- **日期**：2025-07-23 10:29:11 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.4: Backport #13555 for preserving row lineage on compaction (#13641)
- **PR/Issue**：#13641（backport #13555）

## 总体目的

此提交将 PR #13555 的功能 backport 到 Spark 3.4 分支，核心目标与提交 2390（Spark 3.5 backport）完全一致：**在数据文件压缩（compaction）操作中保留行级血统（row lineage）信息**。

Iceberg V3 表通过 `_row_id` 和 `_last_updated_sequence_number` 元数据列实现行级血统。原先的 compaction 实现没有保留这些列，导致压缩后的文件丢失行标识信息。本提交通过识别 compaction 场景并在写入时自动注入行级血统列来修复此问题。

此提交与 2390 的代码变更完全相同，仅目标分支不同（spark/v3.4 vs spark/v3.5）。

## 如何达成设计目的

设计与 2390 完全一致，关键设计点包括：

1. **引入 "rewrite" 选择器**：在 `SparkCatalog`、`SparkCachedTableCatalog` 和 `IcebergSource` 中新增 "rewrite" 标识。
2. **SparkTable 新增 isTableRewrite 标志**：rewrite 模式下通过 `addLineageIfRequired` 附加行级血统列。
3. **SparkWriteBuilder 增强**：compaction 或 overwrite 时自动添加行级血统列到写入 schema。
4. **IcebergSource 自动检测**：通过 `SCAN_TASK_SET_ID` 或 `REWRITTEN_FILE_SCAN_TASK_SET_ID` 识别 rewrite 场景。
5. **SparkCachedTableCatalog 重构**：引入 `TableLoadOptions` 内部类封装加载选项。

## 修改详情

### `spark/v3.4/build.gradle` (+1/-1 lines)

**修改目的**：更新 build 配置以支持 backport。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCachedTableCatalog.java` (+95/-47 lines)

**修改目的**：重构表加载逻辑并支持 rewrite 选择器。

**工作逻辑**：`load` 方法返回类型从 `Pair<Table, Long>` 改为 `SparkTable`。新增 `TableLoadOptions` 内部类封装所有加载选项。`parseLoadOptions` 方法解析 "rewrite" 标记。检测到 rewrite 选项时创建带 `isTableRewrite=true` 的 SparkTable。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+14/-0 lines)

**修改目的**：在 SparkCatalog 中支持 rewrite 选择器。

**工作逻辑**：新增 `REWRITE` 常量，在 `loadTable` 异常处理路径和 `load` 方法中识别 "rewrite" 标记，创建带 rewrite 标志的 SparkTable。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+10/-0 lines)

**修改目的**：自动检测 compaction 场景。

**工作逻辑**：从加载选项获取 `SCAN_TASK_SET_ID` 或 `REWRITTEN_FILE_SCAN_TASK_SET_ID`，存在时设置 rewrite 选择器。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+20/-2 lines)

**修改目的**：支持 rewrite 模式下的行级血统 schema。

**工作逻辑**：新增 `isTableRewrite` 字段和构造函数重载。`addLineageIfRequired` 方法在 rewrite 模式下通过 `MetadataColumns.schemaWithRowLineage` 添加行级血统列。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+20/-2 lines)

**修改目的**：compaction 写入时自动添加行级血统列。

**工作逻辑**：`writeRequiresRowLineage` 条件扩展为 `overwriteFiles || writeConf.rewrittenFileSetId() != null`。如写入 schema 中不存在行级血统列则手动添加 `_row_id` 和 `_last_updated_sequence_number`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+200/-33 lines)

**修改目的**：更新和新增 compaction 测试覆盖行级血统场景。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+73/-1 lines)

**修改目的**：扩展存储过程测试覆盖 V3 表 compaction 行级血统保留。

## 总结

此提交是 2390 在 Spark 3.4 分支上的对应 backport，代码变更完全一致。确保 Spark 3.4 在对 V3 表执行 compaction 时保留行级血统信息，维护了跨 Spark 版本的功能一致性。
