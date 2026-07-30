# 提交 3786：Data: Remove Flag FEATURE_META_ROW_LINEAGE in BaseFormatModelTests (#16529)

## 提交信息

- **序号**：3786 / 4088
- **哈希**：f12e0cfd07c50ef42f5785c7e8f938009128c02c
- **短哈希**：f12e0cfd0
- **日期**：2026-05-25 08:37:02 +0200
- **作者**：GuoYu
- **提交说明**：Data: Remove Flag FEATURE_META_ROW_LINEAGE in BaseFormatModelTests (#16529)
- **PR/Issue**：#16529

## 总体目的

这个提交从 `BaseFormatModelTests` 中移除了 `FEATURE_META_ROW_LINEAGE` 特性标志。该标志用于标记 ORC 格式是否支持行级血缘（row lineage）元数据列的读取。现在 ORC 格式已经支持行级血缘功能（通过之前的提交如 3771 添加了 ORC 读取器支持），不再需要这个特性标志来条件性跳过测试。

移除标志后，ORC 格式的行级血缘测试（`testReadMetadataColumnRowLinage` 和 `testReadMetadataColumnRowLinageExistValue`）将不再通过 `assumeSupports` 跳过，而是直接在 ORC 格式上执行。

## 如何达成设计目的

1. 移除 `FEATURE_META_ROW_LINEAGE` 常量定义。
2. 从 ORC 格式的特性数组中移除该标志。
3. 从两个测试方法中移除 `assumeSupports(fileFormat, FEATURE_META_ROW_LINEAGE)` 调用。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+1/-7 lines)

**修改目的**：移除行级血缘特性标志，使测试在 ORC 格式上无条件执行。

**工作逻辑**：
1. 移除常量 `static final String FEATURE_META_ROW_LINEAGE = "metaRowLineage";`。
2. 从 ORC 格式的支持特性数组中移除 `FEATURE_META_ROW_LINEAGE`：
   ```java
   // 修改前
   new String[] { FEATURE_REUSE_CONTAINERS, FEATURE_COLUMN_METRICS_TRUNCATE_BINARY, FEATURE_META_ROW_LINEAGE, FEATURE_READER_DEFAULT }
   // 修改后
   new String[] { FEATURE_REUSE_CONTAINERS, FEATURE_COLUMN_METRICS_TRUNCATE_BINARY, FEATURE_READER_DEFAULT }
   ```
3. 从 `testReadMetadataColumnRowLinage` 和 `testReadMetadataColumnRowLinageExistValue` 两个测试方法中移除 `assumeSupports(fileFormat, FEATURE_META_ROW_LINEAGE);` 调用。

## 总结

这个提交清理了测试中的特性标志，反映了 ORC 格式现在已经完全支持行级血缘功能。移除 `FEATURE_META_ROW_LINEAGE` 标志后，行级血缘相关的测试会在所有支持的格式（包括 ORC）上无条件运行，确保功能覆盖的完整性。这是功能完善后的测试清理工作。
