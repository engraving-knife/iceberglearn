# 提交 2079：Data, Spark 3.4, 3.5: Add flag to handle missing files for `importSparkTable`

## 提交信息

- **序号**：2079 / 4088
- **哈希**：af32a07f4609b0b8f7ae62cf05565affd5f33c29
- **短哈希**：af32a07f4
- **日期**：2025-05-05 11:22:53 -0500
- **作者**：Xi Chen
- **提交说明**：Data, Spark 3.4, 3.5: Add flag to handle missing files for `importSparkTable` (#12212)
- **PR/Issue**：#12212

## 总体目的

`SparkTableUtil.importSparkTable` 用于将一个已有的 Spark 表（通常是 Parquet/ORC 等外部表）导入为 Iceberg 表。导入过程会枚举源表的所有分区并列出每个分区下的数据文件，将其注册为 Iceberg 的 DataFile。

在实际场景中，源 Spark 表可能存在"幽灵分区"——即 Spark 元数据中记录了某个分区，但该分区对应的物理目录或文件已经不存在（被手动删除、过期清理等）。此前，当 `importSparkTable` 遇到这种缺失的分区时，底层 `TableMigrationUtil.listPartition` 会抛出 `FileNotFoundException`（包装在 `RuntimeException` 中），导致整个导入操作失败。用户无法跳过这些缺失的分区，只能手动修复源表元数据后才能完成导入。

本提交为 `importSparkTable` 和 `importSparkPartitions` 新增 `ignoreMissingFiles` 布尔参数。当设置为 `true` 时，如果某个分区的文件列举因 `FileNotFoundException` 失败，则跳过该分区（记录 WARN 日志并返回空列表），而非中断整个导入。原有的不带该参数的方法被重载保留，默认传入 `false` 以保持向后兼容。

## 如何达成设计目的

设计采用方法重载 + 异常捕获的模式，关键组件协作关系如下：

- **`SparkTableUtil.importSparkTable` / `importSparkPartitions`**：公开 API，新增带 `ignoreMissingFiles` 参数的重载版本，原方法委托给新方法并传入 `false`。
- **`SparkTableUtil.listPartition`（私有）**：在调用 `TableMigrationUtil.listPartition` 时用 try-catch 包裹，当 `ignoreMissingFiles` 为 true 且异常根因是 `FileNotFoundException` 时，记录 WARN 日志并返回空列表；否则重新抛出异常。
- **`TableMigrationUtil.listPartition`**：底层文件列举逻辑（在 `data` 模块），当分区目录不存在时抛出包含 `FileNotFoundException` 根因的 `RuntimeException`。
- **测试**：在 `TestTableMigrationUtil`（data 模块）和 `TestIcebergSourceTablesBase`（Spark 模块）中新增测试，验证缺失文件场景下默认抛出异常、启用 `ignoreMissingFiles` 后跳过并成功导入。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (修改, +66/-30 lines)

**修改目的**：为 `importSparkTable` 和 `importSparkPartitions` 添加 `ignoreMissingFiles` 参数，并在分区列举时处理缺失文件。

**工作逻辑**：
- 新增 `FileNotFoundException` import 和 SLF4J Logger。
- 重构私有 `listPartition` 方法：移除了使用 `int parallelism` 的旧版本，将 `boolean ignoreMissingFiles` 作为参数传入。方法内用 `try { return TableMigrationUtil.listPartition(...); } catch (RuntimeException e) { if (ignoreMissingFiles && e.getCause() instanceof FileNotFoundException) { LOG.warn(...); return Collections.emptyList(); } else { throw e; } }` 包裹调用。
- `importSparkTable` 新增重载：原 6 参数版本委托给新的 7 参数版本（增加 `ignoreMissingFiles`，默认 `false`）。新版本将该参数透传给 `importSparkPartitions`。
- `importSparkPartitions` 同样新增重载：原版本委托给新版本（默认 `false`），新版本将 `ignoreMissingFiles` 透传给 `listPartition` 调用。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (修改, +70/-31 lines)

**修改目的**：与 v3.4 相同的改动，为 Spark 3.5 模块添加 `ignoreMissingFiles` 支持。

**工作逻辑**：与 v3.4 完全一致的改动模式。

### `data/src/test/java/org/apache/iceberg/data/TestTableMigrationUtil.java` (新增, +110/-0 lines)

**修改目的**：为 `TableMigrationUtil.listPartition` 新增单元测试，覆盖正常列举、空分区和缺失文件三种场景。

**工作逻辑**：
- `testListPartition`：创建一个包含 Parquet 文件的分区目录，验证 `listPartition` 返回 1 个 DataFile。
- `testListEmptyPartition`：创建空的分区目录，验证返回 0 个 DataFile。
- `testListPartitionMissingFilesFailure`：指向不存在的分区路径，验证抛出 `RuntimeException`，根因为 `FileNotFoundException`，消息包含 "Unable to list files in partition:"。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (修改, +87/-0 lines)

**修改目的**：为 Spark 3.4 的 `importSparkTable` 新增集成测试，覆盖缺失文件场景。

**工作逻辑**：
- `testImportSparkTableWithMissingFilesFailure`：创建 Parquet 表并添加一个分区后删除该分区目录，调用不带 `ignoreMissingFiles` 的 `importSparkTable`，验证抛出 `SparkException`，根因为 `FileNotFoundException`。
- `testImportSparkTableWithIgnoreMissingFilesEnabled`：同样创建带缺失分区的表，但调用带 `ignoreMissingFiles=true` 的 `importSparkTable`，验证导入成功且分区表只有 2 行（跳过了缺失的 id=1234 分区）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceTablesBase.java` (修改, +86/-0 lines)

**修改目的**：与 v3.4 相同的测试，为 Spark 3.5 模块覆盖缺失文件场景。

**工作逻辑**：与 v3.4 测试逻辑一致。

## 总结

本提交为 `importSparkTable` / `importSparkPartitions` 新增 `ignoreMissingFiles` 标志，允许在导入 Spark 表到 Iceberg 时跳过物理文件缺失的"幽灵分区"。通过方法重载保持向后兼容（默认不忽略），在私有 `listPartition` 方法中捕获 `FileNotFoundException` 根因的异常并返回空列表。修改涉及 Spark 3.4 和 3.5 两个模块的 `SparkTableUtil`，新增 `data` 模块的 `TestTableMigrationUtil` 单元测试和 Spark 模块的集成测试，覆盖默认失败和启用忽略后成功两种场景。
