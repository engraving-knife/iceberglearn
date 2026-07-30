# 提交 1902：Spark 3.4: Rewrite V2 deletes to V3 DVs

## 提交信息

- **序号**：1902 / 4088
- **哈希**：263c62ee6659daa9c96837bf81770fdff9254da3
- **短哈希**：263c62ee6
- **日期**：2025-03-22 10:53:33 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 3.4: Rewrite V2 deletes to V3 DVs

  this backports #12250 to Spark 3.4
- **PR/Issue**：backport #12250

## 总体目的

这个提交将"V2 position deletes 重写为 V3 DV（Deletion Vector）"功能向后移植到 Spark 3.4。该功能允许将 v2 格式的 position delete 文件（以行格式存储删除位置）转换为 v3 格式的 DV 文件（以 Puffin 格式存储紧凑的删除向量），从而获得更高效的存储和查询性能。

在 Iceberg v3 中，DV（Deletion Vector）取代了传统的 position delete 文件格式。DV 使用位图（bitmap）存储被删除的行位置，比传统的逐行 position delete 文件更紧凑，且查询时应用删除的效率更高。对于从 v2 升级到 v3 的表，已有的 position delete 文件需要被重写为 DV 格式。

`RewritePositionDeleteFilesSparkAction` 是用于重写 position delete 文件的 Spark action。本提交使其在 v3 表上能将 v2 格式的 position delete 文件重写为 DV 格式，而非直接报错拒绝。同时新增了 `DVWriter` 来实际写入 DV 文件。

## 如何达成设计目的

整体设计思路：

1. **移除 v3 限制**：将 `RewritePositionDeleteFilesSparkAction` 中 "Cannot rewrite position deletes for V3 table" 的检查移除，改为在 v3 表上执行 V2→DV 重写逻辑。

2. **检测是否需要重写**：新增 `requiresRewriteToDVs()` 方法，扫描 position_deletes 元数据表，检查是否存在非 Puffin 格式的删除文件（即 v2 格式的 position delete）。如果所有删除文件已经是 DV 格式（Puffin），则跳过重写。

3. **DV Writer**：在 `SparkPositionDeletesRewrite` 中新增 `DVWriter`，使用 `PartitioningDVWriter` 将删除位置写入 Puffin 格式的 DV 文件。

4. **format-version 感知**：在 `createWriter` 方法中根据表的 format-version 选择创建 `DVWriter`（v3）或传统的 `DeleteWriter`（v2）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java` (修改, +35/-4 lines)

**修改目的**：支持在 v3 表上将 V2 position deletes 重写为 DV。

**工作逻辑**：

1. 在 `execute` 方法开头添加检查：如果表是 v3 且 `requiresRewriteToDVs()` 返回 false（所有删除文件已是 DV 格式），则直接返回空结果。

2. 新增 `requiresRewriteToDVs()` 方法：扫描 position_deletes 元数据表，过滤掉 Puffin 格式的文件，如果存在非 Puffin 格式的删除文件则返回 true（需要重写）。

3. 移除 `validateAndInitOptions` 中的 `checkArgument(formatVersion <= 2)` 检查，允许在 v3 表上执行重写。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java` (修改, +148/-100 lines)

**修改目的**：新增 DVWriter，根据 format-version 选择写入器。

**工作逻辑**：

1. `createWriter` 方法中根据 `TableUtil.formatVersion(table)` 分支：
   - v3：创建 `DVWriter`，使用 Puffin 文件格式和 `PartitioningDVWriter`
   - v2：创建原有的 `DeleteWriter`

2. 新增 `DVWriter` 内部类，使用 `PartitioningDVWriter` 将删除位置写入 Puffin 格式的 DV 文件。

3. `commit` 方法中使用 `DeleteFileSet.of()` 替代 `ImmutableSet.copyOf()` 来封装删除文件。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/PositionDeletesRowReader.java` (修改, +1 line)

**修改目的**：少量适配。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (修改, +4/-1 lines)

**修改目的**：适配。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesTable.java` (修改, +599/-249 lines)

**修改目的**：大量新增测试用例验证 V2→DV 重写。

### 其他测试文件

`TestRewritePositionDeleteFilesAction.java` 和 `TestHelpers.java` 的适配性修改。

## 总结

本提交将 V2 position deletes 到 V3 DV 的重写功能 backport 到 Spark 3.4。通过移除 v3 限制、新增 `requiresRewriteToDVs()` 检测方法和 `DVWriter` 写入器，使 `RewritePositionDeleteFilesSparkAction` 能在 v3 表上将旧的 position delete 文件转换为更紧凑的 DV 格式。这是 Iceberg v3 DV 功能落地的关键部分。
