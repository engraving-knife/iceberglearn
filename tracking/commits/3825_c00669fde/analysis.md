# 提交 3825：API, Spark 4.1: Add ignore_missing_files to migrate procedure (#16643)

## 提交信息

- **序号**：3825 / 4088
- **哈希**：c00669fde813cac7e9d474c1a2c38fa8e4f75a95
- **短哈希**：c00669fde
- **日期**：2026-06-04 19:52:22 -0700
- **作者**：drexler-sky <evan123wu@gmail.com>
- **提交说明**：API, Spark 4.1: Add ignore_missing_files to migrate procedure (#16643)
- **PR/Issue**：#16643

## 总体目的

本提交为 Iceberg 的 `migrate` 表迁移过程（将非 Iceberg 表迁移为 Iceberg 表）新增 `ignore_missing_files` 选项，使迁移过程中遇到源数据文件丢失时可以选择跳过而非失败。在将 Spark Parquet/ORC 等非 Iceberg 表迁移为 Iceberg 表时，迁移动作需要列出并读取源表的所有数据文件来生成 Iceberg 元数据。如果在此期间有并发清理（如分区目录被外部进程删除、`DELETE` 操作遗留、生命周期策略清理等）导致某些源数据文件消失，迁移会因为 `FileNotFoundException` 而失败。

在生产环境中，源表数据文件被并发清理是常见的运维场景。默认失败行为对用户不友好——用户必须先排查并修复缺失文件才能重新迁移。本提交提供 `ignore_missing_files` 选项，启用后迁移会跳过缺失的源数据文件（仅记录警告），只迁移仍然存在的文件，使迁移能在"部分文件丢失"的情况下继续完成。这是一个实用的容错能力，与 Iceberg 其它动作（如 `rewrite_data_files`、`expire_snapshots`）中类似的 `ignore_missing_files` 选项保持一致。

本提交先在 Spark 4.1 模块实现，API 接口层提供默认方法。后续提交会同步到 Spark 3.5 与 4.0。

## 如何达成设计目的

设计上分三层：
- API 层在 `MigrateTable` 接口新增 `ignoreMissingFiles()` 默认方法（默认抛 `UnsupportedOperationException`），由具体实现覆盖。
- Spark 4.1 实现层 `MigrateTableSparkAction` 增加 `ignoreMissingFiles` 布尔字段并实现该方法，在调用 `SparkTableUtil.importSparkTable` 时传入该标志。
- Spark 过程层 `MigrateTableProcedure` 新增 `ignore_missing_files` 可选布尔参数，从过程入参读取后调用 action 的 `ignoreMissingFiles()`。
底层 `SparkTableUtil.importSparkTable` 已有支持 `ignoreMissingFiles` 的重载，本提交在调用时传入即可。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/MigrateTable.java` (+12/-0 lines)

**修改目的**：在公共 API 接口声明 `ignoreMissingFiles` 能力。

**工作逻辑**：
新增默认方法，默认抛 `UnsupportedOperationException`，Javadoc 说明启用后遇到源数据文件 `FileNotFoundException` 时跳过并警告而非失败，默认失败：
```java
default MigrateTable ignoreMissingFiles() {
  throw new UnsupportedOperationException("Ignoring missing files is not supported");
}
```

### `docs/docs/spark-procedures.md` (+1/-0 lines)

**修改目的**：在 `migrate` 过程的参数表中记录新参数。

**工作逻辑**：
在参数表格中新增一行：
```
| `ignore_missing_files` |  | boolean | When true, skip source data files that cannot be found instead of failing (defaults to false) |
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/MigrateTableSparkAction.java` (+14/-1 lines)

**修改目的**：在 Spark 4.1 实现中支持 `ignoreMissingFiles`。

**工作逻辑**：
- 新增字段 `private boolean ignoreMissingFiles = false;`。
- 实现 `ignoreMissingFiles()`：
```java
@Override
public MigrateTableSparkAction ignoreMissingFiles() {
  this.ignoreMissingFiles = true;
  return this;
}
```
- 在 `execute()` 调用 `SparkTableUtil.importSparkTable` 时，改为传入 `ignoreMissingFiles` 标志的重载：
```java
SparkTableUtil.importSparkTable(
    spark(), v1BackupIdent, icebergTable, stagingLocation,
    Collections.emptyMap(), false, ignoreMissingFiles, executorService);
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/procedures/MigrateTableProcedure.java` (+12/-1 lines)

**修改目的**：在 `migrate` 过程中暴露 `ignore_missing_files` 参数。

**工作逻辑**：
- 新增过程参数 `IGNORE_MISSING_FILES_PARAM = optionalInParameter("ignore_missing_files", DataTypes.BooleanType)`，并加入 `PARAMETERS` 数组。
- 在调用 action 前读取参数并设置：
```java
boolean ignoreMissingFiles = input.asBoolean(IGNORE_MISSING_FILES_PARAM, false);
if (ignoreMissingFiles) {
  migrateTableSparkAction = migrateTableSparkAction.ignoreMissingFiles();
}
```

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMigrateTableProcedure.java` (+39/-0 lines)

**修改目的**：覆盖默认失败行为与 `ignore_missing_files=true` 的跳过行为。

**工作逻辑**：
- `createPartitionedTableWithMissingFiles` 辅助方法：创建分区 Parquet 表，插入两行（id=1, id=2），然后删除 `id=1` 分区目录模拟并发删除。
- `testMigrateMissingFilesFailByDefault`：默认 `migrate` 应以 `FileNotFoundException` 为根因失败。
- `testMigrateIgnoreMissingFiles`：调用 `migrate(table => ..., ignore_missing_files => true)`，应成功迁移存活分区（id=2），返回迁移文件数 1，且查询结果只有 `("b", 2)` 一行。

## 总结

本提交为 `migrate` 表迁移过程新增 `ignore_missing_files` 容错选项，使迁移能在源数据文件被并发清理的场景下继续完成，提升运维友好性。改动覆盖 API 接口、Spark 4.1 实现、过程参数、文档与测试，与 Iceberg 其它动作的同类选项保持一致。先落地 Spark 4.1，后续会同步到其它 Spark 版本。这是迁移工具链实用性的重要增强。
