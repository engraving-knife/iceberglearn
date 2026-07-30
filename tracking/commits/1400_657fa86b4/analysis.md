# 提交 1400：Build: Bump Apache Parquet 1.14.4 (#11502)

## 提交信息

- **序号**：1400 / 4088
- **哈希**：657fa86b4928de23bf01ceda0f6f6112bc19403c
- **短哈希**：657fa86b4
- **日期**：2024-11-20（Wed Nov 20 09:35:34 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Build: Bump Apache Parquet 1.14.4 (#11502)
- **PR/Issue**：#11502
- **关联历史**：
  - PR #11264 曾把 Parquet 从 1.13.1 升级到 1.14.3
  - PR #11462（提交 7cc16fa9）回退了 #11264
  - 本提交先回退那个回退（恢复 1.14.3 升级），再升级到 1.14.4

## 总体目的

把 Iceberg 依赖的 Apache Parquet 库从 1.13.1 升级到 1.14.4。Parquet 1.14.x 引入了若干改进与新特性，其中对 Iceberg 测试可见的一项是：列的未压缩大小（uncompressed size）被加入到列统计中，以允许在读取前更好地预分配内存。这导致 `readable_metrics` 元数据表中"列大小"字段的实际数值发生变化——例如 `binaryCol` 的列大小从原来的固定 `52L` 变成依赖 Parquet 版本的动态值。

此前 PR #11264（升级到 1.14.3）因测试用例硬编码了列大小期望值而失败被回退（#11462）。本提交重新执行升级，但把测试中硬编码的列大小改为从 `dataFile.columnSizes()` 动态查询，从而兼容 Parquet 1.14.x 的新统计行为。最终版本定为 1.14.4（而非 1.14.3），以包含 1.14.4 的额外修复。

## 如何达成设计目的

1. **版本变量更新**：`gradle/libs.versions.toml` 中 `parquet = "1.13.1"` 改为 `parquet = "1.14.4"`。

2. **测试期望动态化**：修改 `flink/v1.18`、`flink/v1.19`、`flink/v1.20` 三个 Flink 版本下的 `TestMetadataTableReadableMetrics.java`，把 `testPrimitiveColumns` 中硬编码的列大小（如 `52L`、`32L`、`85L` 等）替换为从 `dataFile.columnSizes()` 按 fieldId 查询得到的实际值。同时在 `testNestedValues` 中，由于嵌套字段不存储列大小统计，改为从查询结果中读取第一行的列大小作为期望值。

3. **辅助方法改造**：`createPrimitiveTable()` 与 `createNestedTable()` 改为返回 `Table`，以便测试方法能从 `table.currentSnapshot().addedDataFiles(table.io())` 获取 DataFile 进而查询 `columnSizes()`。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Parquet 版本到 1.14.4。

**工作逻辑**：
```toml
-parquet = "1.13.1"
+parquet = "1.14.4"
```

所有引用 `libs.parquet.avro`、`libs.parquet.column`、`libs.parquet.hadoop` 等的模块自动继承新版本。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`

**修改目的**：让测试兼容 Parquet 1.14.x 引入的列大小统计变化。

**工作逻辑**：

1. 新增 import `java.util.Map`。

2. `createNestedTable()` 改为返回 `Table`（之前返回 `void`），并在末尾 `return table;`。`createPrimitiveTable()` 同样改造为返回 `Table`（虽未在 diff 中完整显示，但从 `testPrimitiveColumns` 中 `Table table = createPrimitiveTable();` 可推断）。

3. `testPrimitiveColumns`：
   ```java
   Table table = createPrimitiveTable();
   List<Row> result = sql("SELECT readable_metrics FROM %s$files", TABLE_NAME);

   // With new releases of Parquet, new features might be added which cause the
   // size of the column to increase. For example, with Parquet 1.14.x the
   // uncompressed size has been added to allow for better allocation of memory upfront.
   // Therefore, we look the sizes up, rather than hardcoding them
   DataFile dataFile = table.currentSnapshot().addedDataFiles(table.io()).iterator().next();
   Map<Integer, Long> columnSizeStats = dataFile.columnSizes();
   ```
   然后每个 `Row.of(...)` 中第一列（列大小）从硬编码常量改为 `columnSizeStats.get(PRIMITIVE_SCHEMA.findField("binaryCol").fieldId())` 等。

4. `testNestedValues`：
   ```java
   createNestedTable();
   List<Row> result = sql("SELECT readable_metrics FROM %s$files", TABLE_NAME);

   // We have to take a slightly different approach, since we don't store
   // the column sizes for nested fields.
   long leafDoubleColSize =
       (long) ((Row) ((Row) result.get(0).getField(0)).getField(0)).getField(0);
   long leafLongColSize = (long) ((Row) ((Row) result.get(0).getField(0)).getField(1)).getField(0);

   Row leafDoubleCol = Row.of(leafDoubleColSize, 3L, 1L, 1L, 0.0D, 0.0D);
   Row leafLongCol = Row.of(leafLongColSize, 3L, 1L, null, 0L, 1L);
   ```
   嵌套字段没有 `columnSizes()` 统计，所以从查询结果本身读取列大小作为期望值（自洽验证：保证大小字段非 null 且类型正确，但不锁定具体数值）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`

**修改目的**：与 v1.20 相同的改造，95 处变更。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestMetadataTableReadableMetrics.java`

**修改目的**：与 v1.20 相同的改造，95 处变更。

三个 Flink 版本的测试文件改动几乎一致，区别仅在于包路径前缀（`flink/v1.18/`、`flink/v1.19/`、`flink/v1.20/`）。

## 小结

- **成效**：成功把 Parquet 升级到 1.14.4，绕过此前因测试硬编码列大小而导致的回退；测试改为动态查询列大小，对未来 Parquet 版本引入新的列统计变化也更健壮。
- **影响范围**：4 个文件、242 处新增、47 处删除；核心是版本号 1 行变更，其余为三个 Flink 版本的测试适配。
- **回迁到 1.4.x 的注意事项**：
  - Parquet 1.14.x 是 minor 版本升级（1.13 → 1.14），可能引入新特性或行为变化（如本提交处理的列大小统计）。回迁到 1.4.x 需谨慎评估。
  - 关键风险：1.4.x 的测试中如果也有硬编码列大小的期望值（很可能有，因为 main 上原本就是硬编码），回迁时必须同步把测试改为动态查询，否则测试会失败。本提交的测试改造（三个 Flink 版本）需要一起回迁。
  - 1.4.x 还需检查是否有其它模块（Spark、Core 等）的测试硬编码了 Parquet 列大小，若有则需同样改造。
  - Parquet 1.14.x 对 JDK 版本、依赖（如 Hadoop、Hive）的最低要求可能比 1.13.x 高，回迁前需验证 1.4.x 的构建环境兼容。
  - 如果 1.4.x 已有用户依赖 Parquet 1.13.x 的特定行为（例如列大小统计的缺失），升级到 1.14.x 可能改变 `readable_metrics` 表的输出，需在 release notes 中说明。
  - 建议：如果 1.4.x 已发布且不计划大版本依赖升级，可暂不回迁，作为下一个 minor（1.5.x）的依赖升级处理；若 1.4.x 仍在维护期且 Parquet 1.14.4 修复了影响 1.4.x 的 bug，则需回迁并完整适配测试。
