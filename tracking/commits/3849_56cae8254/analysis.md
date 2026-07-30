# 提交 3849：API, Spark 4.1: Add ignore_missing_files to snapshot procedure (#16710)

## 提交信息

- **序号**：3849 / 4088
- **哈希**：56cae82545f6968361a064985d433e24530ebcf9
- **短哈希**：56cae8254
- **日期**：2026-06-09 09:29:16 -0700
- **作者**：drexler-sky
- **提交说明**：API, Spark 4.1: Add ignore_missing_files to snapshot procedure (#16710)
- **PR/Issue**：#16710

## 总体目的

本提交为 Spark 4.1 的 `snapshot` 存储过程（procedure）新增 `ignore_missing_files` 选项。`snapshot` 过程用于将现有的 Spark 表（如 Parquet 表）快照为 Iceberg 表，它会列出源表的所有数据文件并为它们创建 Iceberg 元数据。

在实际生产环境中，源表的数据文件可能因为并发清理（如分区目录被删除）而消失。在这种情况下，快照操作默认会因为 `FileNotFoundException` 而失败。本提交新增 `ignore_missing_files` 选项，允许用户在快照时跳过缺失的文件而非失败，这对于在并发环境下执行快照操作非常有用。

## 如何达成设计目的

整体设计分四层：

1. **API 层**：在 `SnapshotTable` 接口中新增 `ignoreMissingFiles()` 默认方法（抛出 `UnsupportedOperationException`），保持向后兼容。

2. **Spark Action 层**：在 `SnapshotTableSparkAction` 中实现 `ignoreMissingFiles()`，设置标志位并传递给 `SparkTableUtil.importSparkTable()`。

3. **Procedure 层**：在 `SnapshotTableProcedure` 中新增 `ignore_missing_files` 布尔参数，解析后调用 action 的 `ignoreMissingFiles()`。

4. **文档和测试**：更新 Spark procedures 文档，新增两个测试验证默认失败行为和忽略缺失文件行为。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/SnapshotTable.java` (+12/-0 lines)

**修改目的**：在 API 接口中新增 `ignoreMissingFiles()` 方法。

**工作逻辑**：
新增默认方法，默认抛出 `UnsupportedOperationException`：
```java
default SnapshotTable ignoreMissingFiles() {
  throw new UnsupportedOperationException("Ignoring missing files is not supported");
}
```
Javadoc 说明：启用后，源数据文件消失时（如分区目录被并发清理删除）会跳过而非失败。

### `docs/docs/spark-procedures.md` (+1/-0 lines)

**修改目的**：文档记录新参数。

**工作逻辑**：
在 snapshot 过程的参数表中新增一行：
```markdown
| `ignore_missing_files` |  | boolean | When true, skip source data files that cannot be found instead of failing (defaults to false) |
```

### `spark/v4.1/spark-extensions/src/test/java/.../TestSnapshotTableProcedure.java` (+41/-0 lines)

**修改目的**：测试新参数的行为。

**工作逻辑**：
新增两个测试和辅助方法：

1. `testSnapshotMissingFilesFailByDefault`：创建有缺失文件的分区源表，调用 snapshot 过程（不传 `ignore_missing_files`），验证抛出 `FileNotFoundException`。

2. `testSnapshotIgnoreMissingFiles`：同样创建有缺失文件的源表，调用时传 `ignore_missing_files => true`，验证只导入存活的分区（1 个文件），查询结果只包含存活分区的数据。

3. `createPartitionedSourceWithMissingFiles()`：创建分区 Parquet 表，插入两条数据到不同分区，然后删除一个分区的目录模拟并发删除。

### `spark/v4.1/spark/src/main/java/.../SnapshotTableSparkAction.java` (+17/-1 lines)

**修改目的**：实现 `ignoreMissingFiles()` 并传递给导入工具。

**工作逻辑**：

1. 新增 `ignoreMissingFiles` 字段（默认 `false`）和 `ignoreMissingFiles()` 方法实现。

2. 在 `execute()` 中调用 `SparkTableUtil.importSparkTable()` 时传入 `ignoreMissingFiles` 标志：
```java
SparkTableUtil.importSparkTable(
    spark(),
    v1TableIdent,
    icebergTable,
    stagingLocation,
    Collections.emptyMap(),
    false,
    ignoreMissingFiles,
    executorService);
```

### `spark/v4.1/spark/src/main/java/.../SnapshotTableProcedure.java` (+14/-1 lines)

**修改目的**：在存储过程中暴露新参数。

**工作逻辑**：

1. 新增 `IGNORE_MISSING_FILES_PARAM` 参数定义（`optionalInParameter`）。

2. 将参数加入 `PARAMETERS` 数组。

3. 在 `call()` 方法中解析参数并调用 action：
```java
boolean ignoreMissingFiles = input.asBoolean(IGNORE_MISSING_FILES_PARAM, false);
if (ignoreMissingFiles) {
  action = action.ignoreMissingFiles();
}
```

## 总结

本提交为 Spark 4.1 的 snapshot 存储过程新增了 `ignore_missing_files` 选项，允许用户在快照时跳过因并发清理而消失的源数据文件。这是一个实用的功能增强，解决了生产环境中并发删除与快照操作竞争的问题。实现遵循了 Iceberg 的分层架构（API → Action → Procedure），并提供了完整的文档和测试覆盖。目前仅应用于 Spark 4.1，后续可能向后移植到 3.5/4.0。
