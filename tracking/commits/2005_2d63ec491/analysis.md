# 提交 2005：Core: Fix deprecated FileSystem.isDirectory warning and remove redundant test code

## 提交信息

- **序号**：2005 / 4088
- **哈希**：2d63ec4917c97687061bd202b58eb8e9ab7c5144
- **短哈希**：2d63ec491
- **日期**：2025-04-16 12:02:15 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Fix deprecated FileSystem.isDirectory warning and remove redundant test code (#12805)
- **PR/Issue**：#12805

## 总体目的

本提交完成两项改进：
1. 修复 Hadoop `FileSystem.isDirectory()` 方法已弃用（deprecated）的编译警告，替换为推荐的 API 调用方式。
2. 清理测试代码中大量未使用的局部变量、未使用的方法、未使用的常量和冗余赋值。

`FileSystem.isDirectory()` 在较新版本的 Hadoop 中被标记为已弃用，推荐使用 `getFileStatus().isDirectory()` 来检查路径是否为目录，或使用 `exists()` 来检查路径是否存在。本提交将 `TestHadoopCatalog` 和 `TestJdbcCatalog` 中的相关调用替换为新 API。

同时，通过静态分析工具发现的多个测试文件中的未使用变量和冗余代码也被一并清理，包括从未被引用的局部变量（如 `snapshotAId`、`snapshotCId`、`base`、`delete` 等）、从未被调用的私有方法（如 `delete()`、`upgrade()`）以及从未被使用的常量（如 `V2_FORMAT_VERSION`、`FILE_D`）。

## 如何达成设计目的

1. **修复弃用警告**：将 `fs.isDirectory(path)` 替换为 `fs.getFileStatus(path).isDirectory()`（检查是否为目录）或 `fs.exists(path)`（检查路径是否存在）。

2. **清理冗余代码**：移除编译器警告的未使用变量、方法和常量，以及不必要的赋值表达式。

## 修改详情

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCatalog.java` (修改, +11/-11 lines)

**修改目的**：修复 `FileSystem.isDirectory()` 弃用警告。

**工作逻辑**：
- 检查目录是否存在且为目录时：`fs.isDirectory(path)` → `fs.getFileStatus(path).isDirectory()`
- 检查路径是否不存在时：`fs.isDirectory(path)` (期望 false) → `fs.exists(path)` (期望 false)
- 涉及 6 处 `isDirectory` 检查的替换

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java` (修改, +2/-2 lines)

**修改目的**：修复 `FileSystem.isDirectory()` 弃用警告。

**工作逻辑**：将 2 处 `fs.isDirectory(path)` 替换为 `fs.getFileStatus(path).isDirectory()`。

### `core/src/test/java/org/apache/iceberg/TestBaseIncrementalAppendScan.java` (修改, -8 lines)

**修改目的**：移除未使用的局部变量。

**工作逻辑**：移除了多处从未被引用的 `snapshotAId`、`snapshotBId`、`snapshotCId` 局部变量声明。

### `core/src/test/java/org/apache/iceberg/TestMergeAppend.java` (修改, -7 lines)

**修改目的**：移除未使用的局部变量。

**工作逻辑**：移除了多处从未被引用的 `TableMetadata base`、`TableMetadata delete`、`TableMetadata metadata` 局部变量声明。

### `core/src/test/java/org/apache/iceberg/TestMicroBatchBuilder.java` (修改, -7 lines)

**修改目的**：移除未使用的私有方法。

**工作逻辑**：移除了从未被调用的 `delete(DeleteFiles, List<DataFile>)` 私有方法。

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecBuilderCaseSensitivity.java` (修改, -1 line)

**修改目的**：移除未使用的常量。

**工作逻辑**：移除了从未被引用的 `V2_FORMAT_VERSION` 常量。

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecInfo.java` (修改, +1/-3 lines)

**修改目的**：移除未使用的局部变量和简化赋值。

**工作逻辑**：移除未使用的 `PartitionSpec spec` 变量和 `initialColSize` 变量。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (修改, -1 line)

**修改目的**：移除未使用的局部变量。

**工作逻辑**：移除未引用的 `deltaSnapshotId` 变量。

### `core/src/test/java/org/apache/iceberg/TestSnapshot.java` (修改, -2 lines)

**修改目的**：移除未使用的局部变量。

**工作逻辑**：移除未引用的 `specId` 变量。

### `core/src/test/java/org/apache/iceberg/TestSnapshotLoading.java` (修改, +1/-2 lines)

**修改目的**：移除未使用的变量赋值。

**工作逻辑**：将 `TableMetadata newTableMetadata = TableMetadata.buildFrom(...).build()` 简化为不赋值的形式，因为返回值未被使用。

### `core/src/test/java/org/apache/iceberg/TestTables.java` (修改, -8 lines)

**修改目的**：移除未使用的私有方法。

**工作逻辑**：移除从未被调用的 `upgrade(File, String, int)` 私有方法。

### `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java` (修改, -1 line)

**修改目的**：移除未使用的局部变量。

**工作逻辑**：移除未引用的 `lastColumnId` 变量。

### `core/src/test/java/org/apache/iceberg/hadoop/HadoopFileIOTest.java` (修改, +1/-3 lines)

**修改目的**：移除未使用的变量赋值。

**工作逻辑**：将 `String inputFilePath = Files.createTempDirectory(...)...` 简化为仅创建临时目录，因为路径字符串未被使用。

### `core/src/test/java/org/apache/iceberg/hadoop/HadoopTableTestBase.java` (修改, -7 lines)

**修改目的**：移除未使用的常量。

**工作逻辑**：移除从未被引用的 `FILE_D` 静态常量定义。

### `core/src/test/java/org/apache/iceberg/util/TestSnapshotUtil.java` (修改, +1/-2 lines)

**修改目的**：移除未使用的字段。

**工作逻辑**：将 `this.metadataDir = new File(tableDir, "metadata")` 简化为不赋值的形式，因为字段 `metadataDir` 从未被读取。

## 总结

本提交完成了两项改进：修复了 Hadoop `FileSystem.isDirectory()` 的弃用警告（替换为 `getFileStatus().isDirectory()` 或 `exists()`），并清理了 15 个测试文件中大量未使用的变量、方法和常量，减少了约 48 行冗余代码，提升了测试代码的整洁度。
