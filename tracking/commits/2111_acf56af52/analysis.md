# 提交分析：Parquet: Fix to ensure that last updated sequence numbers for V2 and earlier tables are null

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2111 |
| 短哈希 | `acf56af52` |
| 完整哈希 | `acf56af529743324647c5657fa53f040ed909d1e` |
| 作者 | Amogh Jahagirdar |
| 邮箱 | amoghj@apache.org |
| 日期 | 2025-05-12 13:49:44 2025 -0600 |
| 提交信息 | Parquet: Fix to ensure that last updated sequence numbers for V2 and earlier tables are null (#13001) |

## 总体目的

本提交修复了 Parquet 读取器中 `LAST_UPDATED_SEQUENCE_NUMBER` 元数据列的一个 bug。在处理 V2 及更早版本的表时，该元数据列应该返回 null（因为这些表不支持行级谱系），但由于代码中使用了错误的字段 ID 查找 base row ID，导致行为不正确。同时添加了测试用例来验证 V2 及更早版本表中行谱系列的正确行为。

## 设计目的的实现方式

1. **修复字段 ID 查找错误**：在 `ParquetValueReaders.replaceWithMetadataReader` 方法中，处理 `LAST_UPDATED_SEQUENCE_NUMBER` 分支时，原代码错误地使用 `id`（即 `LAST_UPDATED_SEQUENCE_NUMBER.fieldId()`）来查找 base row ID，修复为使用 `MetadataColumns.ROW_ID.fieldId()` 来正确查找。

2. **添加测试验证**：在 `TestSparkMetadataColumns` 中添加 `testRowLineageColumnsAreNullBeforeV3` 测试方法，验证 V2 及更早版本表中 `_row_id` 和 `_last_updated_sequence_number` 列返回 null。

## 修改详情

### 1. 修改 `ParquetValueReaders.java`

**文件**：`parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java`

**修改内容**：

在 `replaceWithMetadataReader` 方法中，`LAST_UPDATED_SEQUENCE_NUMBER` 分支的第一行：

```java
// 修复前（错误）：
Long baseRowId = (Long) idToConstant.get(id);

// 修复后（正确）：
Long baseRowId = (Long) idToConstant.get(MetadataColumns.ROW_ID.fieldId());
```

**目的和工作逻辑**：

该方法用于将 Parquet 读取器替换为元数据列读取器。当请求的列是 `LAST_UPDATED_SEQUENCE_NUMBER` 时，需要两个值：
- `baseRowId`：基础行 ID，来自 `ROW_ID` 元数据列的常量映射
- `fileSeqNumber`：文件序列号，来自 `LAST_UPDATED_SEQUENCE_NUMBER` 自身的常量映射

原代码的 bug 在于使用 `id`（`LAST_UPDATED_SEQUENCE_NUMBER.fieldId()`）来查找 `baseRowId`，这意味着它查找的是 `LAST_UPDATED_SEQUENCE_NUMBER` 对应的常量值，而非 `ROW_ID` 对应的常量值。由于 `idToConstant` 映射中 `ROW_ID` 和 `LAST_UPDATED_SEQUENCE_NUMBER` 是不同的 key，这会导致 `baseRowId` 获取到错误的值（或 null）。

修复后，正确使用 `MetadataColumns.ROW_ID.fieldId()` 来查找 `baseRowId`，确保获取到正确的行 ID 常量。对于 V2 及更早版本的表，`ROW_ID` 对应的常量映射中不存在该值（因为 V2 不支持行谱系），因此 `baseRowId` 为 null，进而使 `lastUpdated` 读取器正确返回 null。

### 2. 修改 `TestSparkMetadataColumns.java`（Spark 3.5）

**文件**：`spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改内容**：新增测试方法 `testRowLineageColumnsAreNullBeforeV3`（15 行）。

```java
@TestTemplate
public void testRowLineageColumnsAreNullBeforeV3() {
  assumeThat(formatVersion).isLessThan(3);
  // ToDo: When the other readers have row lineage plumbed through, remove these assumptions
  assumeThat(vectorized).isFalse();
  assumeThat(fileFormat).isEqualTo(FileFormat.PARQUET);

  sql("INSERT INTO TABLE %s VALUES (1L, 'a1', 'b1')", TABLE_NAME);

  assertEquals(
      "Rows must match",
      ImmutableList.of(row(1L, null, null)),
      sql("SELECT id, _row_id, _last_updated_sequence_number FROM %s", TABLE_NAME));
}
```

**目的和工作逻辑**：

该测试验证 V2 及更早版本表中行谱系元数据列的行为：
1. `assumeThat(formatVersion).isLessThan(3)`：仅在表格式版本小于 3 时执行测试
2. `assumeThat(vectorized).isFalse()`：跳过向量化读取模式（TODO 注释说明等其他读取器支持行谱系后移除此假设）
3. `assumeThat(fileFormat).isEqualTo(FileFormat.PARQUET)`：仅测试 Parquet 格式
4. 向表插入一条记录 `(1, 'a1', 'b1')`
5. 查询 `id, _row_id, _last_updated_sequence_number`，期望结果为 `(1, null, null)`——即 V2 表中行 ID 和最后更新序列号都应为 null

## 总结

本提交修复了一个 Parquet 读取器中的字段 ID 查找 bug。在 `ParquetValueReaders.replaceWithMetadataReader` 方法中，处理 `LAST_UPDATED_SEQUENCE_NUMBER` 元数据列时，原代码错误地使用 `LAST_UPDATED_SEQUENCE_NUMBER.fieldId()` 来查找 base row ID，而应该使用 `ROW_ID.fieldId()`。这导致在 V2 及更早版本表中（不支持行谱系），`lastUpdated` 读取器无法正确返回 null。

修复方案简单直接——将 `idToConstant.get(id)` 改为 `idToConstant.get(MetadataColumns.ROW_ID.fieldId())`，确保使用正确的字段 ID 查找 base row ID。同时添加了参数化测试验证 V2 及更早版本表中 `_row_id` 和 `_last_updated_sequence_number` 列返回 null 的预期行为。

共修改 2 个文件，新增 16 行，删除 1 行。
